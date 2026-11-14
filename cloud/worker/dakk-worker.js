/**
 * Dakk sunucusu.
 *
 *   POST /notify     Uygulama kapalıyken mesaj bildirimi.
 *   POST /assistant  Kurucu panelinde "Özel API" seçildiyse OpenAI uyumlu uç noktaya vekillik.
 *   POST /admin/broadcast  (yalnızca kurucular) Herkese bildirim, ör. "yeni sürüm çıktı".
 *   POST /admin/rules      (yalnızca kurucular) Firestore kurallarını derleyip yayınlar.
 *
 * Güvenlik:
 *   - Her istek Firebase kimlik jetonuyla doğrulanır.
 *   - /notify yalnızca isteği yapan kişinin gerçekten gönderdiği bir mesaj için, sohbetin diğer
 *     üyelerine (grupta tüm üyelere) bildirim gönderir. İçerik şifreli iletilir; sunucu okuyamaz.
 *   - Alıcı göndereni engellediyse bildirim gönderilmez.
 *   - Özel API anahtarı uygulamalara gitmez.
 *   - /admin/* uçları yalnızca sunucu gizlisinde (DAKK_ADMIN_KEY) tutulan anahtarla çalışır; anahtar
 *     depoda veya uygulamada yer almaz. Anahtar tanımlı değilse bu uçlar tamamen kapalıdır.
 */

const DEFAULT_DATABASE_ID = "ai-studio-android-e2d067e8-8aba-4fce-a486-b836129f76c1";
const JWKS_URL = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
const SCOPES =
  "https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/firebase.messaging https://www.googleapis.com/auth/firebase";
const MAX_FCM_CONTENT = 3000;
const MAX_MESSAGE_AGE_MS = 15 * 60 * 1000;
const MAX_GROUP_MEMBERS = 50;

// İzolat (Worker örneği) yaşadığı sürece tutulan önbellekler.
let jwksCache = { keys: null, expires: 0 };
let accessTokenCache = { token: null, expires: 0 };
let configCache = { pub: null, priv: null, expires: 0 };
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
      if (info.configured && url.searchParams.get("check") === "deep") info.checks = await deepCheck(env);
      return json(info);
    }
    if (request.method !== "POST") return json({ error: "Yöntem desteklenmiyor" }, 405);

    try {
      if (path.includes("/admin/")) return await handleAdmin(request, env, path);
      const sa = serviceAccount(env);
      const uid = await verifyIdToken(request, sa.project_id);
      const ctx = { env, sa, uid, db: env.FIRESTORE_DATABASE_ID || DEFAULT_DATABASE_ID };
      if (path.endsWith("/notify")) return await handleNotify(request, ctx);
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
  if (isGroup) {
    // Grup içeriği her üye için ayrı sarılmış anahtarla şifrelidir; telefon mesajı Firestore'dan okur.
    data.group = "1";
    data.groupName = String(chat.name || "Grup").slice(0, 60);
  } else {
    const content = String(msg.content || "");
    // FCM veri sınırı 4 KB; sığmayan içeriği telefon Firestore'dan kendisi okur.
    if (content.length + data.senderKey.length + data.recipientKey.length <= MAX_FCM_CONTENT) {
      data.content = content;
    }
  }

  const results = await Promise.all(recipients.map((r) => pushTo(ctx, r, data)));
  const sent = results.filter((r) => r === "sent").length;
  if (sent === 0 && results.includes("error")) throw httpError(502, "Bildirim gönderilemedi");

  notifiedMessages.set(dedupeKey, Date.now());
  if (notifiedMessages.size > 2000) {
    const cutoff = Date.now() - MAX_MESSAGE_AGE_MS;
    for (const [k, t] of notifiedMessages) if (t < cutoff) notifiedMessages.delete(k);
  }
  return json({ ok: true, sent });
}

/** Tek bir alıcıya bildirim gönderir. Alıcı göndereni engellediyse gönderilmez. */
async function pushTo(ctx, recipient, data) {
  try {
    const contact = await getDoc(ctx, `users/${recipient}/contacts/${ctx.uid}`);
    if (contact && contact.isBlocked === true) return "blocked";

    const tokenDoc = await getDoc(ctx, `push_tokens/${recipient}`);
    const token = tokenDoc && typeof tokenDoc.token === "string" ? tokenDoc.token : "";
    if (!token) return "no_token";

    const res = await googleFetch(ctx, `https://fcm.googleapis.com/v1/projects/${ctx.sa.project_id}/messages:send`, {
      method: "POST",
      body: JSON.stringify({
        message: {
          token,
          data,
          android: { priority: "HIGH", ttl: "86400s" },
        },
      }),
    });
    if (res.ok) return "sent";
    const text = await res.text();
    if (res.status === 404 || text.includes("UNREGISTERED")) {
      await deleteDoc(ctx, `push_tokens/${recipient}`).catch(() => {});
      return "unregistered";
    }
    console.error("FCM hatası", res.status, text);
    return "error";
  } catch (e) {
    console.error("Bildirim hatası", e && e.stack ? e.stack : e);
    return "error";
  }
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

  const tokens = await listPushTokens(ctx);
  const result = { ok: true, dryRun, total: tokens.length, sent: 0, failed: 0, removed: 0 };
  const url = `https://fcm.googleapis.com/v1/projects/${ctx.sa.project_id}/messages:send`;

  let next = 0;
  async function workerLoop() {
    while (next < tokens.length) {
      const { uid, token } = tokens[next++];
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
        if (!dryRun) await deleteDoc(ctx, `push_tokens/${uid}`).catch(() => {});
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
