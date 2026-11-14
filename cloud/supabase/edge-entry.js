// Supabase Edge giriş noktası. Yayınlanan işlev = dakk-worker.js ("export default" satırı çıkarılarak) + bu dosya.
// Derlemek için: node cloud/supabase/build-edge.mjs > /tmp/dakk-edge.js

let lastKeepAlive = 0;

/** Ücretsiz projeler bir hafta boşta kalınca duraklatılır; ara sıra veritabanına küçük bir istek atılır. */
async function keepAlive() {
  const url = Deno.env.get("SUPABASE_URL");
  const key = Deno.env.get("SUPABASE_ANON_KEY");
  if (!url || !key || Date.now() - lastKeepAlive < 60 * 60 * 1000) return;
  lastKeepAlive = Date.now();
  try {
    await fetch(url + "/rest/v1/", { headers: { apikey: key, Authorization: "Bearer " + key } });
  } catch (_) {}
}

function readServiceAccount() {
  const raw = Deno.env.get("FIREBASE_SERVICE_ACCOUNT");
  if (raw) return raw;
  const b64 = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_B64");
  if (!b64) return undefined;
  try {
    const bin = atob(b64.replace(/\s+/g, ""));
    return new TextDecoder().decode(Uint8Array.from(bin, (c) => c.charCodeAt(0)));
  } catch (_) {
    return undefined;
  }
}

const SERVICE_ACCOUNT = readServiceAccount();

Deno.serve(async (request) => {
  const env = {
    FIREBASE_SERVICE_ACCOUNT: SERVICE_ACCOUNT,
    FIRESTORE_DATABASE_ID: Deno.env.get("FIRESTORE_DATABASE_ID"),
    DAKK_ADMIN_KEY: Deno.env.get("DAKK_ADMIN_KEY"),
    REALTIME_URL: Deno.env.get("SUPABASE_URL"),
    REALTIME_PUBLIC_KEY: Deno.env.get("DAKK_REALTIME_KEY") || Deno.env.get("SUPABASE_ANON_KEY"),
    SUPABASE_URL: Deno.env.get("SUPABASE_URL"),
    SUPABASE_SERVICE_KEY: Deno.env.get("SUPABASE_SERVICE_ROLE_KEY"),
    MEDIA_URL: Deno.env.get("MEDIA_URL"),
    MEDIA_SERVICE_KEY: Deno.env.get("MEDIA_SERVICE_KEY"),
  };
  if (request.method === "GET") await keepAlive();
  return worker.fetch(request, env);
});
