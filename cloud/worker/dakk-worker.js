/**
 * Dakk sunucusu.
 *
 *   GET  /                 Durum, anlık sinyal kanalı ve tasarruf seviyesi.
 *   POST /notify           Uygulama kapalıyken mesaj bildirimi.
 *   POST /register         Bildirim jetonunu kaydeder (2.4.0+; Supabase tablosunda tutulur).
 *   POST /unregister       Bildirim jetonunu siler.
 *   POST /media/upload     Fotoğraf için tek kullanımlık yükleme adresi (iki depodan biri, yedekli).
 *   POST /media/download   Fotoğraf için kısa ömürlü indirme adresi.
 *   POST /media/delete     Fotoğrafı sunucudan siler (alıcı indirince).
 *   POST /assistant        Kurucu panelinde "Özel API" seçildiyse OpenAI uyumlu uç noktaya vekillik.
 *   POST /admin/broadcast  Herkese bildirim, ör. "yeni sürüm çıktı".
 *   POST /admin/rules      Firestore kurallarını derleyip yayınlar.
 *   POST /admin/config     Uygulama ayarlarını okur / izinli alanları değiştirir.
 *   POST /admin/usage      Günlük ve aylık kullanım özeti.
 *   POST /admin/cleanup    7 günden eski fotoğrafları siler, kotaları denetler (pg_cron her gün çağırır).
 *   POST /admin/alerts     Kota %70'i geçtiyse kuruculara bildirim (ayda bir kez).
 *
 * Güvenlik:
 *   - Her istek Firebase kimlik jetonuyla doğrulanır.
 *   - /notify yalnızca isteği yapan kişinin gerçekten gönderdiği bir mesaj için, sohbetin diğer
 *     üyelerine bildirim gönderir. İçerik şifreli iletilir; sunucu okuyamaz.
 *   - Fotoğraflar telefonda şifrelenir; sunucu yalnızca şifreli baytları geçici olarak tutar ve
 *     adresleri yalnızca sohbetin üyelerine verir.
 *   - Özel API anahtarı ve Supabase gizli anahtarları uygulamalara gitmez.
 *   - /admin/* uçları yalnızca sunucu gizlisinde (DAKK_ADMIN_KEY) tutulan anahtarla çalışır.
 */

const DEFAULT_DATABASE_ID = "ai-studio-android-e2d067e8-8aba-4fce-a486-b836129f76c1";
const JWKS_URL = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
const SCOPES =
  "https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/firebase.messaging https://www.googleapis.com/auth/firebase";
const MAX_FCM_CONTENT = 3000;
const MAX_MESSAGE_AGE_MS = 15 * 60 * 1000;
const MAX_GROUP_MEMBERS = 50;
const MAX_MEDIA_BYTES = 2 * 1024 * 1024;
const MEDIA_TTL_MS = 7 * 24 * 60 * 60 * 1000;
// Her Supabase projesinin aylık ücretsiz veri çıkışı 5 GB. Fotoğraflara depo başına 3,5 GB ayrılır;
// kalan pay ana projede "yazıyor…" sinyali ve diğer istekler içindir.
const FREE_EGRESS_BYTES = 5 * 1000 * 1000 * 1000;
const MEDIA_STORE_BUDGET = 3.5 * 1000 * 1000 * 1000;
const FREE_EDGE_CALLS = 500_000;
const FREE_DB_BYTES = 500 * 1000 * 1000;
const QUOTA_ALERT_LEVEL = 0.7;
const CONFIG_FIELDS = {
  min_supported_version: "string",
  force_update_message: "string",
  maintenance_enabled: "boolean",
  maintenance_message: "string",
  images_enabled: "boolean",
};

// İzolat (Worker örneği) yaşadığı sürece tutulan önbellekler.
let jwksCache = { keys: null, expires: 0 };
let accessTokenCache = { token: null, expires: 0 };
let configCache = { pub: null, priv: null, expires: 0 };
let usageCache = { value: null, expires: 0 };
let lastAlertCheck = 0;
const chatCache = new Map();
const notifiedMessages = new Map();

const worker = {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, "");
    if (request.method === "GET") {
      const info = { ok: true, service: "dakk", configured: isConfigured(env), time: new Date().toISOString() };
      // "Yazıyor…" sinyali için anlık yayın kanalı. Yalnızca herkese açık (anon) anahtar paylaşılır.
      if (env.REALTIME_URL && env.REALTIME_PUBLIC_KEY) {
        info.realtime = { url: env.REALTIME_URL, key: env.REALTIME_PUBLIC_KEY };
      }
      info.features = { register: hasSupabase(env), media: hasMedia(env) };
      if (hasSupabase(env)) {
        addUsage(env, "calls", 1);
        info.saver = (await usageState(env)).saver;
      }
      if (info.configured && url.searchParams.get("check") === "deep") info.checks = await deepCheck(env);
      return json(info);
    }
    if (request.method !== "POST") return json({ error: "Yöntem desteklenmiyor" }, 405);
    addUsage(env, "calls", 1);

    try {
      if (path.includes("/admin/")) return await handleAdmin(request, env, path);
      const sa = serviceAccount(env);
      const uid = await verifyIdToken(request, sa.project_id);
      const ctx = { env, sa, uid, db: env.FIRESTORE_DATABASE_ID || DEFAULT_DATABASE_ID };
      if (path.endsWith("/notify")) return await handleNotify(request, ctx);
      if (path.endsWith("/register")) return await handleRegister(request, ctx);
      if (path.endsWith("/unregister")) return await handleUnregister(ctx);
      if (path.endsWith("/media/upload")) return await handleMediaUpload(request, ctx);
      if (path.endsWith("/media/download")) return await handleMediaDownload(request, ctx);
      if (path.endsWith("/media/delete")) return await handleMediaDelete(request, ctx);
      if (path.endsWith("/assistant")) return await handleAssistant(request, ctx);
      return json({ error: "Bulunamadı" }, 404);
    } catch (e) {
      const status = e.status || 500;
      if (status >= 500) console.error(e && e.stack ? e.stack : e);
      return json({ error: e.publicMessage || (status >= 500 ? "Sunucu hatası" : e.message) }, status);
    }
  },
};

export default worker;

/** Sunucunun Google'a, Firestore'a ve FCM'e erişebildiğini doğrular. Gizli bilgi döndürmez. */
async function deepCheck(env) {
  const out = { google: false, firestore: false, fcm: "unknown" };
  try {
    const sa = serviceAccount(env);
    const ctx = { env, sa, uid: null, db: env.FIRESTORE_DATABASE_ID || DEFAULT_DATABASE_ID };
    await getAccessToken(sa);
    out.google = true;
    await getDoc(ctx, "app_config/public");
    out.firestore = true;
    const res = await googleFetch(ctx, `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
      method: "POST",
      body: JSON.stringify({ validate_only: true, message: { token: "dakk-health-check", data: { type: "ping" } } }),
    });
    // 400 = API açık ve yetki var (sahte jeton reddedildi); 403/401 = API kapalı veya yetki yok.
    out.fcm = res.status === 400 || res.ok ? "ok" : `http_${res.status}`;
  } catch (e) {
    out.error = String((e && e.message) || e).slice(0, 160);
  }
  return out;
}

function isConfigured(env) {
  try {
    const sa = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT || "{}");
    return Boolean(sa.client_email && sa.private_key && sa.project_id);
  } catch (_) {
    return false;
  }
}

// ------------------------------------------------------------------------------------------------
// /notify
// ------------------------------------------------------------------------------------------------

async function handleNotify(request, ctx) {
  const body = await readJson(request);
  const chatId = String(body.chatId || "");
  const messageId = String(body.messageId || "");
  if (!/^[A-Za-z0-9_-]{1,200}$/.test(chatId) || !/^[A-Za-z0-9]{1,100}$/.test(messageId)) {
    throw httpError(400, "Geçersiz istek");
  }

  const chat = await getDoc(ctx, `chats/${chatId}`);
  if (!chat) throw httpError(404, "Sohbet bulunamadı");
  const members = Array.isArray(chat.memberUids) ? chat.memberUids : [];
  if (!members.includes(ctx.uid)) throw httpError(403, "Bu sohbetin üyesi değilsiniz");

  const msg = await getDoc(ctx, `chats/${chatId}/messages/${messageId}`);
  if (!msg) throw httpError(404, "Mesaj bulunamadı");
  if (msg.senderId !== ctx.uid) throw httpError(403, "Bu mesaj size ait değil");

  const dedupeKey = `${chatId}/${messageId}`;
  if (notifiedMessages.has(dedupeKey)) return json({ ok: true, skipped: "duplicate" });

  const createdAt = msg.createdAt ? Date.parse(msg.createdAt) : 0;
  if (createdAt && Date.now() - createdAt > MAX_MESSAGE_AGE_MS) return json({ ok: true, skipped: "old" });

  const messageType = String(msg.messageType || "TEXT");
  if (messageType === "SYSTEM" || messageType === "SCREENSHOT") return json({ ok: true, skipped: "silent" });

  const isGroup = chat.isGroup === true || chatId.startsWith("g_");
  const recipients = members.filter((m) => m !== ctx.uid).slice(0, MAX_GROUP_MEMBERS);
  if (recipients.length === 0) return json({ ok: true, skipped: "no_recipient" });

  const data = {
    type: "msg",
    chatId,
    messageId,
    senderId: ctx.uid,
    senderName: String(msg.senderName || "Dakk").slice(0, 60),
    messageType,
    senderKey: String(msg.senderKey || ""),
    recipientKey: String(msg.recipientKey || ""),
    assistant: msg.isAssistant === true ? "1" : "0",
  };
  const content = String(msg.content || "");
  const keys = msg.keys && typeof msg.keys === "object" ? msg.keys : {};
  if (isGroup) {
    data.group = "1";
    data.groupName = String(chat.name || "Grup").slice(0, 60);
  } else if (content.length + data.senderKey.length + data.recipientKey.length <= MAX_FCM_CONTENT) {
    // FCM veri sınırı 4 KB; sığmayan içeriği telefon Firestore'dan kendisi okur.
    data.content = content;
  }

  // 2.4.0+ telefonlar jetonlarını Supabase'e kaydeder: engelleme kontrolünü telefon kendisi yapar ve
  // grup içeriği de bildirimin içinde gelir. Eski sürümler için Firestore'daki jetona düşülür.
  const fresh = await supabaseTokens(ctx.env, recipients);
  const results = await Promise.all(
    recipients.map((r) => {
      const token = fresh.get(r);
      if (!token) return pushLegacy(ctx, r, data);
      const payload = { ...data };
      if (isGroup && typeof keys[r] === "string") {
        const size = content.length + data.senderKey.length + keys[r].length;
        if (size <= MAX_FCM_CONTENT) {
          payload.content = content;
          payload.key = keys[r];
        }
      }
      return sendFcm(ctx, token, payload, r, true);
    })
  );
  const sent = results.filter((r) => r === "sent").length;
  if (sent === 0 && results.includes("error")) throw httpError(502, "Bildirim gönderilemedi");
  addUsage(ctx.env, "notify", 1);

  notifiedMessages.set(dedupeKey, Date.now());
  if (notifiedMessages.size > 2000) {
    const cutoff = Date.now() - MAX_MESSAGE_AGE_MS;
    for (const [k, t] of notifiedMessages) if (t < cutoff) notifiedMessages.delete(k);
  }
  return json({ ok: true, sent });
}

/** Eski sürüm alıcı: jeton Firestore'da. Alıcı göndereni engellediyse gönderilmez. */
async function pushLegacy(ctx, recipient, data) {
  try {
    const contact = await getDoc(ctx, `users/${recipient}/contacts/${ctx.uid}`);
    if (contact && contact.isBlocked === true) return "blocked";
    const tokenDoc = await getDoc(ctx, `push_tokens/${recipient}`);
    const token = tokenDoc && typeof tokenDoc.token === "string" ? tokenDoc.token : "";
    if (!token) return "no_token";
    return await sendFcm(ctx, token, data, recipient, false);
  } catch (e) {
    console.error("Bildirim hatası", e && e.stack ? e.stack : e);
    return "error";
  }
}

async function sendFcm(ctx, token, data, recipient, fromSupabase) {
  try {
    const res = await googleFetch(ctx, `https://fcm.googleapis.com/v1/projects/${ctx.sa.project_id}/messages:send`, {
      method: "POST",
      body: JSON.stringify({ message: { token, data, android: { priority: "HIGH", ttl: "86400s" } } }),
    });
    if (res.ok) return "sent";
    const text = await res.text();
    if (res.status === 404 || text.includes("UNREGISTERED")) {
      if (fromSupabase) await supabaseFetch(ctx.env, `push_tokens?uid=eq.${encodeURIComponent(recipient)}`, { method: "DELETE" }).catch(() => {});
      else await deleteDoc(ctx, `push_tokens/${recipient}`).catch(() => {});
      return "unregistered";
    }
    console.error("FCM hatası", res.status, text.slice(0, 300));
    return "error";
  } catch (e) {
    console.error("Bildirim hatası", e && e.stack ? e.stack : e);
    return "error";
  }
}

// ------------------------------------------------------------------------------------------------
// /register, /unregister — bildirim jetonları (Supabase: public.push_tokens)
// ------------------------------------------------------------------------------------------------

async function handleRegister(request, ctx) {
  if (!hasSupabase(ctx.env)) throw httpError(503, "Sunucu yapılandırılmamış");
  const body = await readJson(request);
  const token = String(body.token || "");
  const appVersion = Math.max(0, Math.min(1_000_000, Number(body.appVersion) || 0));
  if (!token || token.length > 4096) throw httpError(400, "Geçersiz jeton");
  const res = await supabaseFetch(ctx.env, "push_tokens?on_conflict=uid", {
    method: "POST",
    headers: { Prefer: "resolution=merge-duplicates,return=minimal" },
    body: JSON.stringify({ uid: ctx.uid, token, app_version: appVersion, updated_at: new Date().toISOString() }),
  });
  if (!res.ok) throw new Error(`Jeton kaydedilemedi: ${res.status} ${await res.text()}`);
  // Eski Firestore kaydı artık gereksiz; silinirse aynı kişiye iki bildirim gitmez.
  await deleteDoc(ctx, `push_tokens/${ctx.uid}`).catch(() => {});
  return json({ ok: true });
}

async function handleUnregister(ctx) {
  if (hasSupabase(ctx.env)) {
    await supabaseFetch(ctx.env, `push_tokens?uid=eq.${encodeURIComponent(ctx.uid)}`, { method: "DELETE" });
  }
  await deleteDoc(ctx, `push_tokens/${ctx.uid}`).catch(() => {});
  return json({ ok: true });
}

async function supabaseTokens(env, uids) {
  const out = new Map();
  if (!hasSupabase(env) || uids.length === 0) return out;
  try {
    const list = uids.map((u) => `"${String(u).replace(/["\\]/g, "")}"`).join(",");
    const res = await supabaseFetch(env, `push_tokens?select=uid,token&uid=in.(${encodeURIComponent(list)})`);
    if (!res.ok) return out;
    for (const row of await res.json()) if (row.uid && row.token) out.set(row.uid, row.token);
  } catch (e) {
    console.error("Jetonlar okunamadı", e && e.message ? e.message : e);
  }
  return out;
}

// ------------------------------------------------------------------------------------------------
// /media/* — fotoğraflar (2. Supabase projesi, özel "media" kovası)
// ------------------------------------------------------------------------------------------------

function mediaPath(body) {
  const chatId = String(body.chatId || "");
  const mediaId = String(body.mediaId || "");
  if (!/^[A-Za-z0-9_-]{1,200}$/.test(chatId) || !/^[A-Za-z0-9_-]{8,80}$/.test(mediaId)) {
    throw httpError(400, "Geçersiz istek");
  }
  return { chatId, mediaId, path: `${chatId}/${mediaId}` };
}

/** Sohbet üyeliğini doğrular; sohbet belgesi 5 dakika önbellekte tutulur. */
async function requireMember(ctx, chatId) {
  const cached = chatCache.get(chatId);
  let members = cached && cached.expires > Date.now() ? cached.members : null;
  if (!members) {
    const chat = await getDoc(ctx, `chats/${chatId}`);
    if (!chat) throw httpError(404, "Sohbet bulunamadı");
    members = Array.isArray(chat.memberUids) ? chat.memberUids : [];
    chatCache.set(chatId, { members, expires: Date.now() + 5 * 60_000 });
    if (chatCache.size > 500) chatCache.delete(chatCache.keys().next().value);
  }
  if (!members.includes(ctx.uid)) {
    chatCache.delete(chatId);
    throw httpError(403, "Bu sohbetin üyesi değilsiniz");
  }
}

/**
 * Fotoğraf depoları: 0 = fotoğraf projesi (MEDIA_URL), 1 = ana projenin kendi deposu (SUPABASE_URL).
 * Her sohbet sabit bir depoya düşer (veri çıkışı iki projeye bölünür); depo dolmaya yaklaşırsa
 * ya da yanıt vermezse diğerine geçilir.
 */
function mediaStores(env) {
  const out = [];
  if (env.MEDIA_URL && env.MEDIA_SERVICE_KEY) out.push({ id: 0, url: env.MEDIA_URL, key: env.MEDIA_SERVICE_KEY });
  if (env.SUPABASE_URL && env.SUPABASE_SERVICE_KEY && env.MEDIA_SECOND_STORE !== "off") {
    out.push({ id: 1, url: env.SUPABASE_URL, key: env.SUPABASE_SERVICE_KEY });
  }
  return out;
}

function storeById(env, id) {
  return mediaStores(env).find((s) => s.id === id) || null;
}

function storeFetch(store, path, init = {}) {
  return fetch(`${store.url}/storage/v1/${path}`, {
    ...init,
    headers: {
      apikey: store.key,
      Authorization: `Bearer ${store.key}`,
      "Content-Type": "application/json",
      ...(init.headers || {}),
    },
  });
}

function hashString(text) {
  let h = 2166136261;
  for (let i = 0; i < text.length; i++) {
    h ^= text.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

/** Sohbet için denenecek depolar: tercih edilen önce, dolmak üzere olan sona. */
async function storeOrder(env, chatId) {
  const stores = mediaStores(env);
  if (stores.length < 2) return stores;
  const preferred = hashString(chatId) % stores.length;
  const ordered = [stores[preferred], ...stores.filter((_, i) => i !== preferred)];
  const { summary } = await usageState(env);
  const month = summary.month || {};
  const full = (s) => (Number(month[`media_down_${s.id}`]) || 0) >= 0.85 * MEDIA_STORE_BUDGET;
  return [...ordered.filter((s) => !full(s)), ...ordered.filter(full)];
}

async function mediaRow(env, path) {
  const rows = await supabaseFetch(env, `media_objects?select=bytes,store&path=eq.${encodeURIComponent(path)}`)
    .then((r) => (r.ok ? r.json() : []))
    .catch(() => []);
  return Array.isArray(rows) && rows[0] ? rows[0] : null;
}

async function handleMediaUpload(request, ctx) {
  if (!hasMedia(ctx.env)) throw httpError(503, "Fotoğraf sunucusu yapılandırılmamış");
  const body = await readJson(request);
  const { chatId, path } = mediaPath(body);
  const bytes = Number(body.bytes) || 0;
  if (bytes <= 0 || bytes > MAX_MEDIA_BYTES) throw httpError(413, "Fotoğraf çok büyük");
  await requireMember(ctx, chatId);

  let lastError = "";
  for (const store of await storeOrder(ctx.env, chatId)) {
    try {
      const res = await storeFetch(store, `object/upload/sign/media/${path}`, { method: "POST", body: "{}" });
      const text = await res.text();
      if (!res.ok) {
        lastError = `${store.id}: ${res.status} ${text.slice(0, 200)}`;
        continue;
      }
      const signed = JSON.parse(text);
      await supabaseFetch(ctx.env, "media_objects?on_conflict=path", {
        method: "POST",
        headers: { Prefer: "resolution=merge-duplicates,return=minimal" },
        body: JSON.stringify({ path, chat_id: chatId, sender_uid: ctx.uid, bytes, store: store.id }),
      }).catch(() => {});
      addUsage(ctx.env, "media_up", bytes);
      return json({ ok: true, url: `${store.url}/storage/v1${signed.url}`, store: store.id });
    } catch (e) {
      lastError = `${store.id}: ${String((e && e.message) || e).slice(0, 200)}`;
    }
  }
  throw new Error(`Yükleme adresi alınamadı: ${lastError}`);
}

async function handleMediaDownload(request, ctx) {
  if (!hasMedia(ctx.env)) throw httpError(503, "Fotoğraf sunucusu yapılandırılmamış");
  const body = await readJson(request);
  const { chatId, path } = mediaPath(body);
  await requireMember(ctx, chatId);

  const row = await mediaRow(ctx.env, path);
  const known = row ? storeById(ctx.env, Number(row.store) || 0) : null;
  const candidates = known ? [known] : mediaStores(ctx.env);
  for (const store of candidates) {
    const res = await storeFetch(store, `object/sign/media/${path}`, {
      method: "POST",
      body: JSON.stringify({ expiresIn: 600 }),
    });
    if (res.status === 400 || res.status === 404) continue;
    const text = await res.text();
    if (!res.ok) throw new Error(`İndirme adresi alınamadı: ${res.status} ${text.slice(0, 300)}`);
    const signed = JSON.parse(text);
    const bytes = row ? Number(row.bytes) || 0 : 0;
    addUsage(ctx.env, "media_down", bytes);
    addUsage(ctx.env, `media_down_${store.id}`, bytes);
    return json({ ok: true, url: `${store.url}/storage/v1${signed.signedURL}` });
  }
  return json({ ok: false, expired: true }, 404);
}

async function handleMediaDelete(request, ctx) {
  if (!hasMedia(ctx.env)) return json({ ok: true });
  const body = await readJson(request);
  const { chatId, path } = mediaPath(body);
  await requireMember(ctx, chatId);
  const row = await mediaRow(ctx.env, path);
  await deleteMediaPaths(ctx.env, [{ path, store: row ? Number(row.store) || 0 : null }]);
  return json({ ok: true });
}

/** items: [{ path, store }]; store null ise iki depodan da silinir. */
async function deleteMediaPaths(env, items) {
  if (items.length === 0) return;
  for (const store of mediaStores(env)) {
    const paths = items.filter((i) => i.store === null || i.store === undefined || i.store === store.id).map((i) => i.path);
    if (paths.length === 0) continue;
    const res = await storeFetch(store, "object/media", { method: "DELETE", body: JSON.stringify({ prefixes: paths }) });
    if (!res.ok && res.status !== 404) throw new Error(`Fotoğraf silinemedi: ${res.status} ${await res.text()}`);
  }
  const list = items.map((i) => `"${i.path}"`).join(",");
  await supabaseFetch(env, `media_objects?path=in.(${encodeURIComponent(list)})`, { method: "DELETE" }).catch(() => {});
}

// ------------------------------------------------------------------------------------------------
// Kullanım sayacı ve otomatik tasarruf (kota bekçisi)
// ------------------------------------------------------------------------------------------------

/** Sayaçları arka planda artırır; hata isteği bozmaz. */
function addUsage(env, metric, value) {
  if (!hasSupabase(env) || !value) return;
  supabaseFetch(env, "rpc/dakk_usage_add", {
    method: "POST",
    body: JSON.stringify({ p_metric: metric, p_value: Math.round(value) }),
  }).catch(() => {});
  usageCache.expires = 0;
}

/**
 * Tasarruf seviyesi: 0 = normal, 1 = fotoğraflar daha küçük, 2 = fotoğraflar küçük + "yazıyor" kapalı.
 * Her fotoğraf deposunun aylık 5 GB veri çıkışından MEDIA_STORE_BUDGET kadarı fotoğraflara ayrılır ve
 * günlere bölünür; bütçe aşılmaya başlarsa seviye yükselir.
 */
async function usageState(env) {
  if (usageCache.value && Date.now() < usageCache.expires) return usageCache.value;
  let summary = { today: {}, month: {} };
  try {
    const res = await supabaseFetch(env, "rpc/dakk_usage_summary", { method: "POST", body: "{}" });
    if (res.ok) summary = (await res.json()) || summary;
  } catch (_) {}
  const budget = MEDIA_STORE_BUDGET * Math.max(1, mediaStores(env).length);
  const today = Number((summary.today || {}).media_down) || 0;
  const month = Number((summary.month || {}).media_down) || 0;
  let saver = 0;
  if (today >= (0.7 * budget) / 30 || month >= 0.6 * budget) saver = 1;
  if (today >= budget / 30 || month >= 0.85 * budget) saver = 2;
  usageCache = { value: { saver, summary }, expires: Date.now() + 60_000 };
  if (Date.now() - lastAlertCheck > 60 * 60_000) {
    lastAlertCheck = Date.now();
    checkQuotaAlerts(null, env).catch(() => {});
  }
  return usageCache.value;
}

/** Ücretsiz kotaların bu ayki doluluk oranları (0–1). */
async function quotaLevels(env) {
  const { summary } = await usageState(env);
  const month = summary.month || {};
  const n = (k) => Number(month[k]) || 0;
  const unassigned = Math.max(0, n("media_down") - n("media_down_0") - n("media_down_1"));
  const levels = {
    "Fotoğraf deposu 1 (veri çıkışı)": (n("media_down_0") + unassigned) / FREE_EGRESS_BYTES,
    "Fotoğraf deposu 2 (veri çıkışı)": n("media_down_1") / FREE_EGRESS_BYTES,
    "Sunucu çağrıları": n("calls") / FREE_EDGE_CALLS,
  };
  try {
    const res = await supabaseFetch(env, "rpc/dakk_db_size", { method: "POST", body: "{}" });
    if (res.ok) levels["Veritabanı boyutu"] = (Number(await res.json()) || 0) / FREE_DB_BYTES;
  } catch (_) {}
  return levels;
}

/**
 * Bir kota %70'i geçince kuruculara bir kez (ay başına, kota başına) bildirim gönderir.
 * Kurucular, Kurucu panelini açtıklarında app_config/founders belgesine kendilerini yazar.
 */
async function checkQuotaAlerts(ctx, env = ctx && ctx.env) {
  if (!hasSupabase(env) || !isConfigured(env)) return [];
  const levels = await quotaLevels(env);
  const monthKey = new Date().toISOString().slice(0, 7);
  const sent = [];
  for (const [name, level] of Object.entries(levels)) {
    if (!(level >= QUOTA_ALERT_LEVEL)) continue;
    const res = await supabaseFetch(env, "rpc/dakk_flag_once", {
      method: "POST",
      body: JSON.stringify({ p_key: `quota70:${name}:${monthKey}` }),
    });
    if (!res.ok || (await res.json()) !== true) continue;
    const c = ctx || { env, sa: serviceAccount(env), uid: null, db: env.FIRESTORE_DATABASE_ID || DEFAULT_DATABASE_ID };
    await notifyFounders(c, "Dakk kota uyarısı ⚠️", `${name}: bu ayın ücretsiz kotasının %${Math.round(level * 100)}'i kullanıldı.`);
    sent.push(name);
  }
  return sent;
}

async function notifyFounders(ctx, title, body) {
  const doc = (await getDoc(ctx, "app_config/founders").catch(() => null)) || {};
  const uids = Array.isArray(doc.uids) ? doc.uids.filter((u) => typeof u === "string").slice(0, 10) : [];
  if (uids.length === 0) return 0;
  const tokens = (await allPushTokens(ctx)).filter((t) => uids.includes(t.uid));
  const url = `https://fcm.googleapis.com/v1/projects/${ctx.sa.project_id}/messages:send`;
  let sent = 0;
  for (const { token } of tokens) {
    const res = await googleFetch(ctx, url, {
      method: "POST",
      body: JSON.stringify({
        message: {
          token,
          notification: { title, body },
          data: { type: "announce" },
          android: {
            priority: "HIGH",
            notification: { channel_id: "dakk_messages", icon: "ic_stat_dakk", color: "#FFCC00", tag: "dakk_quota" },
          },
        },
      }),
    });
    if (res.ok) sent++;
  }
  return sent;
}

// ------------------------------------------------------------------------------------------------
// /admin/* — yalnızca kurucuların bildiği sunucu anahtarıyla
// ------------------------------------------------------------------------------------------------

async function handleAdmin(request, env, path) {
  await requireAdminKey(request, env);
  const sa = serviceAccount(env);
  const ctx = { env, sa, uid: null, db: env.FIRESTORE_DATABASE_ID || DEFAULT_DATABASE_ID };
  if (path.endsWith("/admin/broadcast")) return await handleBroadcast(request, ctx);
  if (path.endsWith("/admin/rules")) return await handleRules(request, ctx);
  if (path.endsWith("/admin/config")) return await handleConfig(request, ctx);
  if (path.endsWith("/admin/usage")) return json({ ok: true, ...(await usageState(env)), quotas: await quotaLevels(env) });
  if (path.endsWith("/admin/alerts")) return json({ ok: true, sent: await checkQuotaAlerts(ctx) });
  if (path.endsWith("/admin/cleanup")) return await handleCleanup(ctx);
  return json({ error: "Bulunamadı" }, 404);
}

async function requireAdminKey(request, env) {
  const expected = String(env.DAKK_ADMIN_KEY || "");
  if (expected.length < 32) throw httpError(404, "Bulunamadı");
  const given = String(request.headers.get("x-dakk-admin") || "");
  // Sabit süreli karşılaştırma: iki değerin özetini bayt bayt karşılaştır.
  const [a, b] = await Promise.all([sha256(given), sha256(expected)]);
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
  if (diff !== 0 || !given) throw httpError(401, "Yetkisiz");
}

async function sha256(text) {
  return new Uint8Array(await crypto.subtle.digest("SHA-256", textEncode(text)));
}

/**
 * Bildirim jetonu olan herkese bildirim gönderir. Eski sürümler de gösterir (bildirim yükü kullanılır).
 * Gövde: { title, body, dryRun? }
 */
async function handleBroadcast(request, ctx) {
  const body = await readJson(request);
  const title = String(body.title || "").trim();
  const text = String(body.body || "").trim();
  if (!title || title.length > 80 || !text || text.length > 400) throw httpError(400, "Başlık veya metin geçersiz");
  const dryRun = body.dryRun === true;
  // İsteğe bağlı: yalnızca bu sürüm kodunun ALTINDAKİ (eski) uygulamalara gönder.
  // Supabase'de sürümü bu koda eşit/büyük olanlar atlanır; Firestore'daki sürümsüz jetonlar
  // (2.4.0 öncesi) Supabase'de kaydı yoksa eski sayılır.
  const below = body.belowVersionCode == null ? null : Number(body.belowVersionCode);
  if (below != null && (!Number.isInteger(below) || below < 1)) throw httpError(400, "belowVersionCode geçersiz");

  const tokens = await allPushTokens(ctx, below);
  const result = { ok: true, dryRun, belowVersionCode: below, total: tokens.length, sent: 0, failed: 0, removed: 0 };
  const url = `https://fcm.googleapis.com/v1/projects/${ctx.sa.project_id}/messages:send`;

  let next = 0;
  async function workerLoop() {
    while (next < tokens.length) {
      const { uid, token, fromSupabase } = tokens[next++];
      const res = await googleFetch(ctx, url, {
        method: "POST",
        body: JSON.stringify({
          validate_only: dryRun,
          message: {
            token,
            notification: { title, body: text },
            data: { type: "announce" },
            android: {
              priority: "HIGH",
              ttl: "604800s",
              notification: { channel_id: "dakk_messages", icon: "ic_stat_dakk", color: "#FFCC00", tag: "dakk_announce" },
            },
          },
        }),
      });
      if (res.ok) {
        result.sent++;
        continue;
      }
      const errText = await res.text();
      if (res.status === 404 || errText.includes("UNREGISTERED")) {
        if (!dryRun && fromSupabase) {
          await supabaseFetch(ctx.env, `push_tokens?uid=eq.${encodeURIComponent(uid)}`, { method: "DELETE" }).catch(() => {});
        } else if (!dryRun) await deleteDoc(ctx, `push_tokens/${uid}`).catch(() => {});
        result.removed++;
      } else {
        result.failed++;
        if (result.failed <= 3) console.error("Duyuru bildirimi hatası", res.status, errText.slice(0, 300));
      }
    }
  }
  await Promise.all(Array.from({ length: Math.min(8, tokens.length) }, workerLoop));
  return json(result);
}

/**
 * Supabase (2.4.0+) ve Firestore'daki (eski sürümler) jetonlar; aynı kişi bir kez sayılır.
 * belowVersionCode verilirse yalnızca o sürüm kodunun altındaki uygulamalar döner.
 */
async function allPushTokens(ctx, belowVersionCode = null) {
  const out = [];
  const seen = new Set();
  if (hasSupabase(ctx.env)) {
    const res = await supabaseFetch(ctx.env, "push_tokens?select=uid,token,app_version");
    if (!res.ok && belowVersionCode != null) throw new Error(`Supabase jetonları okunamadı: ${res.status}`);
    if (res.ok) {
      for (const row of await res.json()) {
        if (row.uid && row.token && !seen.has(row.uid)) {
          seen.add(row.uid);
          const version = row.app_version == null ? null : Number(row.app_version);
          if (belowVersionCode != null && version != null && version >= belowVersionCode) continue;
          out.push({ uid: row.uid, token: row.token, fromSupabase: true });
        }
      }
    }
  }
  for (const t of await listPushTokens(ctx)) {
    if (!seen.has(t.uid)) {
      seen.add(t.uid);
      out.push(t);
    }
  }
  return out;
}

async function listPushTokens(ctx) {
  const out = [];
  let pageToken = "";
  do {
    const params = new URLSearchParams({ pageSize: "300", "mask.fieldPaths": "token" });
    if (pageToken) params.set("pageToken", pageToken);
    const res = await googleFetch(ctx, `${docUrl(ctx, "push_tokens")}?${params}`);
    if (!res.ok) throw new Error(`Jetonlar okunamadı: ${res.status} ${await res.text()}`);
    const page = await res.json();
    for (const d of page.documents || []) {
      const token = d.fields && d.fields.token && d.fields.token.stringValue;
      if (token) out.push({ uid: d.name.split("/").pop(), token });
    }
    pageToken = page.nextPageToken || "";
  } while (pageToken && out.length < 50000);
  return out;
}

/**
 * app_config/public belgesini döndürür; gövdede { set: {...} } varsa yalnızca izinli alanları değiştirir.
 * Örnek: { "set": { "min_supported_version": "2.3.0" } }
 * Özellik anahtarı: { "set": { "feature_sesli_mesaj": true } }
 */
async function handleConfig(request, ctx) {
  const body = await readJson(request);
  const set = body.set && typeof body.set === "object" ? body.set : null;
  if (set) {
    const fields = {};
    for (const [k, v] of Object.entries(set)) {
      const type = configFieldType(k);
      if (!type || typeof v !== type) throw httpError(400, `İzin verilmeyen alan: ${k}`);
      if (type === "string" && v.length > 2000) throw httpError(400, `Çok uzun: ${k}`);
      fields[k] = v;
    }
    if (Object.keys(fields).length === 0) throw httpError(400, "Değiştirilecek alan yok");
    await patchDoc(ctx, "app_config/public", fields);
    configCache.expires = 0;
  }
  const pub = (await getDoc(ctx, "app_config/public")) || {};
  const view = {};
  for (const k of Object.keys(pub)) if (configFieldType(k)) view[k] = pub[k];
  return json({ ok: true, config: view });
}

/** Sunucudan açılıp kapatılan özellikler (2.6.0+): `feature_<ad>` alanları her zaman boolean'dır. */
const FEATURE_KEY = /^feature_[a-z0-9_]{1,40}$/;

function configFieldType(key) {
  if (Object.prototype.hasOwnProperty.call(CONFIG_FIELDS, key)) return CONFIG_FIELDS[key];
  return FEATURE_KEY.test(key) ? "boolean" : null;
}

/** 7 günden eski fotoğrafları ve 60 günden eski sayaçları siler. */
async function handleCleanup(ctx) {
  const result = { ok: true, media: 0 };
  if (hasMedia(ctx.env)) {
    const cutoff = new Date(Date.now() - MEDIA_TTL_MS).toISOString();
    for (let round = 0; round < 20; round++) {
      const res = await supabaseFetch(
        ctx.env,
        `media_objects?select=path,store&created_at=lt.${encodeURIComponent(cutoff)}&limit=100`
      );
      if (!res.ok) break;
      const rows = await res.json();
      if (!rows.length) break;
      await deleteMediaPaths(ctx.env, rows.map((r) => ({ path: r.path, store: Number(r.store) || 0 })));
      result.media += rows.length;
    }
    // Fotoğraf projesi boşta kalıp duraklatılmasın diye her gün bir istek de yapılmış olur.
    await mediaFetch(ctx.env, "bucket/media", { method: "GET" }).catch(() => {});
  }
  result.alerts = await checkQuotaAlerts(ctx).catch((e) => String((e && e.message) || e).slice(0, 200));
  if (hasSupabase(ctx.env)) {
    const old = new Date(Date.now() - 60 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
    await supabaseFetch(ctx.env, `usage_daily?day=lt.${old}`, { method: "DELETE" }).catch(() => {});
  }
  return json(result);
}

/**
 * Firestore kurallarını derler; dryRun değilse yayınlar. Hatalı kural yayınlanmaz.
 * Gövde: { source, dryRun? }
 */
async function handleRules(request, ctx) {
  const body = await readJson(request);
  const source = String(body.source || "");
  if (!source.includes("service cloud.firestore") || source.length > 64000) throw httpError(400, "Geçersiz kural metni");
  const base = `https://firebaserules.googleapis.com/v1/projects/${ctx.sa.project_id}`;

  const created = await googleFetch(ctx, `${base}/rulesets`, {
    method: "POST",
    body: JSON.stringify({ source: { files: [{ name: "firestore.rules", content: source }] } }),
  });
  const createdText = await created.text();
  if (!created.ok) return json({ ok: false, stage: "compile", status: created.status, detail: createdText.slice(0, 4000) }, 400);
  const rulesetName = JSON.parse(createdText).name;
  if (body.dryRun === true) return json({ ok: true, dryRun: true, ruleset: rulesetName });

  const releaseName = `projects/${ctx.sa.project_id}/releases/cloud.firestore/${ctx.db}`;
  const current = await googleFetch(ctx, `https://firebaserules.googleapis.com/v1/${releaseName}`);
  const previous = current.ok ? (await current.json()).rulesetName : null;

  const res = current.status === 404
    ? await googleFetch(ctx, `${base}/releases`, { method: "POST", body: JSON.stringify({ name: releaseName, rulesetName }) })
    : await googleFetch(ctx, `https://firebaserules.googleapis.com/v1/${releaseName}`, {
        method: "PATCH",
        body: JSON.stringify({ release: { name: releaseName, rulesetName } }),
      });
  const text = await res.text();
  if (!res.ok) return json({ ok: false, stage: "release", status: res.status, detail: text.slice(0, 2000), previous }, 502);
  return json({ ok: true, ruleset: rulesetName, previous, release: releaseName });
}

// ------------------------------------------------------------------------------------------------
// /assistant — OpenAI uyumlu uç noktaya vekillik
// ------------------------------------------------------------------------------------------------

async function handleAssistant(request, ctx) {
  const { pub, priv } = await loadConfig(ctx);
  if (pub.assistant_enabled === false) throw httpError(403, "Dakk Asistan şu an kapalı");
  const base = String(priv.custom_api_base_url || "").trim().replace(/\/+$/, "");
  const key = String(priv.custom_api_key || "").trim();
  const model = String(priv.custom_api_model || "").trim();
  if (!base.startsWith("https://") || !key || !model) throw httpError(503, "Özel API yapılandırılmamış");

  const body = await readJson(request);
  const system = String(body.system || "").slice(0, 8000);
  const input = Array.isArray(body.messages) ? body.messages.slice(-40) : [];
  const messages = [];
  if (system) messages.push({ role: "system", content: system });
  for (const m of input) {
    const role = m && m.role === "assistant" ? "assistant" : "user";
    const content = String((m && m.content) || "").slice(0, 8000);
    if (content) messages.push({ role, content });
  }
  if (!messages.some((m) => m.role === "user")) throw httpError(400, "Mesaj boş");

  const res = await fetch(`${base}/chat/completions`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${key}` },
    body: JSON.stringify({ model, messages }),
  });
  const text = await res.text();
  if (!res.ok) {
    console.error("Özel API hatası", res.status, text.slice(0, 500));
    throw httpError(502, `Özel API hata verdi (HTTP ${res.status})`);
  }
  let reply = "";
  try {
    reply = JSON.parse(text).choices[0].message.content || "";
  } catch (_) {
    throw httpError(502, "Özel API yanıtı anlaşılamadı");
  }
  return json({ text: reply });
}

async function loadConfig(ctx) {
  if (configCache.pub && Date.now() < configCache.expires) return configCache;
  const [pub, priv] = await Promise.all([getDoc(ctx, "app_config/public"), getDoc(ctx, "app_config/private")]);
  configCache = { pub: pub || {}, priv: priv || {}, expires: Date.now() + 60_000 };
  return configCache;
}

// ------------------------------------------------------------------------------------------------
// Firebase kimlik doğrulama (ID token)
// ------------------------------------------------------------------------------------------------

async function verifyIdToken(request, projectId) {
  const header = request.headers.get("Authorization") || "";
  const match = header.match(/^Bearer\s+(.+)$/i);
  if (!match) throw httpError(401, "Kimlik doğrulama gerekli");
  const parts = match[1].split(".");
  if (parts.length !== 3) throw httpError(401, "Geçersiz kimlik");

  const head = JSON.parse(textDecode(b64urlDecode(parts[0])));
  const payload = JSON.parse(textDecode(b64urlDecode(parts[1])));
  if (head.alg !== "RS256" || !head.kid) throw httpError(401, "Geçersiz kimlik");

  const keys = await getJwks();
  const jwk = keys.find((k) => k.kid === head.kid);
  if (!jwk) throw httpError(401, "Geçersiz kimlik");
  const key = await crypto.subtle.importKey(
    "jwk",
    jwk,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["verify"]
  );
  const ok = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5",
    key,
    b64urlDecode(parts[2]),
    new TextEncoder().encode(`${parts[0]}.${parts[1]}`)
  );
  if (!ok) throw httpError(401, "Geçersiz kimlik");

  const now = Math.floor(Date.now() / 1000);
  if (payload.aud !== projectId) throw httpError(401, "Geçersiz kimlik (aud)");
  if (payload.iss !== `https://securetoken.google.com/${projectId}`) throw httpError(401, "Geçersiz kimlik (iss)");
  if (typeof payload.exp !== "number" || payload.exp < now) throw httpError(401, "Oturum süresi doldu");
  if (typeof payload.iat !== "number" || payload.iat > now + 300) throw httpError(401, "Geçersiz kimlik (iat)");
  if (typeof payload.sub !== "string" || !payload.sub || payload.sub.length > 128) throw httpError(401, "Geçersiz kimlik");
  return payload.sub;
}

async function getJwks() {
  if (jwksCache.keys && Date.now() < jwksCache.expires) return jwksCache.keys;
  const res = await fetch(JWKS_URL);
  if (!res.ok) throw new Error(`JWKS alınamadı: ${res.status}`);
  const body = await res.json();
  const maxAge = Number((res.headers.get("Cache-Control") || "").match(/max-age=(\d+)/)?.[1] || 3600);
  jwksCache = { keys: body.keys || [], expires: Date.now() + maxAge * 1000 };
  return jwksCache.keys;
}

// ------------------------------------------------------------------------------------------------
// Google hizmet hesabı erişim jetonu
// ------------------------------------------------------------------------------------------------

function serviceAccount(env) {
  if (!env.FIREBASE_SERVICE_ACCOUNT) {
    throw Object.assign(new Error("FIREBASE_SERVICE_ACCOUNT tanımlı değil"), {
      status: 500,
      publicMessage: "Sunucu yapılandırılmamış",
    });
  }
  const sa = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT);
  if (!sa.client_email || !sa.private_key || !sa.project_id) throw new Error("Hizmet hesabı JSON'u eksik");
  return sa;
}

async function getAccessToken(sa) {
  if (accessTokenCache.token && Date.now() < accessTokenCache.expires) return accessTokenCache.token;
  const now = Math.floor(Date.now() / 1000);
  const header = b64urlEncode(textEncode(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const claims = b64urlEncode(
    textEncode(
      JSON.stringify({
        iss: sa.client_email,
        scope: SCOPES,
        aud: "https://oauth2.googleapis.com/token",
        iat: now,
        exp: now + 3600,
      })
    )
  );
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToDer(sa.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, textEncode(`${header}.${claims}`));
  const assertion = `${header}.${claims}.${b64urlEncode(new Uint8Array(signature))}`;

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=${encodeURIComponent("urn:ietf:params:oauth:grant-type:jwt-bearer")}&assertion=${assertion}`,
  });
  if (!res.ok) throw new Error(`Erişim jetonu alınamadı: ${res.status} ${await res.text()}`);
  const body = await res.json();
  accessTokenCache = { token: body.access_token, expires: Date.now() + (body.expires_in - 120) * 1000 };
  return accessTokenCache.token;
}

async function googleFetch(ctx, url, init = {}) {
  const token = await getAccessToken(ctx.sa);
  return fetch(url, {
    ...init,
    headers: { ...(init.headers || {}), Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
  });
}

// ------------------------------------------------------------------------------------------------
// Firestore REST
// ------------------------------------------------------------------------------------------------

function docUrl(ctx, path) {
  const encoded = path.split("/").map(encodeURIComponent).join("/");
  return `https://firestore.googleapis.com/v1/projects/${ctx.sa.project_id}/databases/${encodeURIComponent(ctx.db)}/documents/${encoded}`;
}

async function getDoc(ctx, path) {
  const res = await googleFetch(ctx, docUrl(ctx, path));
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`Firestore okuma hatası (${path}): ${res.status} ${await res.text()}`);
  const body = await res.json();
  return decodeFields(body.fields || {});
}

async function deleteDoc(ctx, path) {
  const res = await googleFetch(ctx, docUrl(ctx, path), { method: "DELETE" });
  if (!res.ok && res.status !== 404) throw new Error(`Firestore silme hatası: ${res.status}`);
}

async function patchDoc(ctx, path, fields) {
  const params = new URLSearchParams();
  for (const k of Object.keys(fields)) params.append("updateMask.fieldPaths", k);
  const encoded = {};
  for (const [k, v] of Object.entries(fields)) encoded[k] = typeof v === "boolean" ? { booleanValue: v } : { stringValue: String(v) };
  const res = await googleFetch(ctx, `${docUrl(ctx, path)}?${params}`, {
    method: "PATCH",
    body: JSON.stringify({ fields: encoded }),
  });
  if (!res.ok) throw new Error(`Firestore yazma hatası (${path}): ${res.status} ${await res.text()}`);
}

function decodeFields(fields) {
  const out = {};
  for (const [k, v] of Object.entries(fields)) out[k] = decodeValue(v);
  return out;
}

function decodeValue(v) {
  if (v == null) return null;
  if ("stringValue" in v) return v.stringValue;
  if ("booleanValue" in v) return v.booleanValue;
  if ("integerValue" in v) return Number(v.integerValue);
  if ("doubleValue" in v) return v.doubleValue;
  if ("timestampValue" in v) return v.timestampValue;
  if ("nullValue" in v) return null;
  if ("arrayValue" in v) return (v.arrayValue.values || []).map(decodeValue);
  if ("mapValue" in v) return decodeFields(v.mapValue.fields || {});
  return null;
}

// ------------------------------------------------------------------------------------------------
// Yardımcılar
// ------------------------------------------------------------------------------------------------

function hasSupabase(env) {
  return Boolean(env.SUPABASE_URL && env.SUPABASE_SERVICE_KEY);
}

function hasMedia(env) {
  return hasSupabase(env) && Boolean(env.MEDIA_URL && env.MEDIA_SERVICE_KEY);
}

/** Bu projenin veritabanı (PostgREST), gizli anahtarla. */
function supabaseFetch(env, path, init = {}) {
  return fetch(`${env.SUPABASE_URL}/rest/v1/${path}`, {
    ...init,
    headers: {
      apikey: env.SUPABASE_SERVICE_KEY,
      Authorization: `Bearer ${env.SUPABASE_SERVICE_KEY}`,
      "Content-Type": "application/json",
      ...(init.headers || {}),
    },
  });
}

/** Fotoğraf projesinin depolama API'si, gizli anahtarla. */
function mediaFetch(env, path, init = {}) {
  return fetch(`${env.MEDIA_URL}/storage/v1/${path}`, {
    ...init,
    headers: {
      apikey: env.MEDIA_SERVICE_KEY,
      Authorization: `Bearer ${env.MEDIA_SERVICE_KEY}`,
      "Content-Type": "application/json",
      ...(init.headers || {}),
    },
  });
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });
}

function httpError(status, message) {
  return Object.assign(new Error(message), { status });
}

async function readJson(request) {
  const text = await request.text();
  if (text.length > 200_000) throw httpError(413, "İstek çok büyük");
  try {
    return JSON.parse(text || "{}");
  } catch (_) {
    throw httpError(400, "Geçersiz JSON");
  }
}

function textEncode(s) {
  return new TextEncoder().encode(s);
}

function textDecode(bytes) {
  return new TextDecoder().decode(bytes);
}

function b64urlDecode(s) {
  const b64 = s.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((s.length + 3) % 4);
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function b64urlEncode(bytes) {
  let bin = "";
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pemToDer(pem) {
  const b64 = pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out.buffer;
}
