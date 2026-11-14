package com.example.push

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.FirestoreRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Uygulamanın ön planda olup olmadığını süreç genelinde tutar (bildirim tekrarını önlemek için). */
object AppState {
    @Volatile
    var foreground: Boolean = false

    /** Sunucu üzerinden anlık bildirim alınabiliyorsa true; bu durumda arka plandaki yerel bildirimler kapatılır. */
    @Volatile
    var pushActive: Boolean = false

    private const val PREFS = "dakk_push"
    private const val KEY_SERVER = "server_url"
    private const val KEY_BLOCKED = "blocked_uids"

    /** Hazır olduğu doğrulanan sunucu adresini saklar (bildirimden yanıt verirken kullanılır). */
    fun rememberServer(context: Context, serverUrl: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_SERVER, "") != serverUrl) prefs.edit().putString(KEY_SERVER, serverUrl).apply()
    }

    fun rememberedServer(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SERVER, "").orEmpty()

    /**
     * Engellediğim kişiler. Sunucu yeni jeton kaydında engel listesine bakmaz (Firestore okuması yapmamak için);
     * engellenen kişiden gelen bildirim telefonda sessizce düşürülür.
     */
    fun rememberBlocked(context: Context, uids: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getStringSet(KEY_BLOCKED, emptySet()) != uids) prefs.edit().putStringSet(KEY_BLOCKED, uids).apply()
    }

    fun isBlocked(context: Context, uid: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_BLOCKED, emptySet())?.contains(uid) == true
}

/**
 * Uygulama kapalıyken mesaj bildirimi.
 *
 * Gönderen cihaz, mesaj sunucuya ulaşınca Dakk sunucusuna yalnızca
 * sohbet ve mesaj kimliğini bildirir. Sunucu kimliği doğrular, mesajın gerçekten bu kişiden
 * geldiğini Firestore'dan kontrol eder ve alıcıya Firebase Cloud Messaging ile ŞİFRELİ içeriği iletir.
 * Mesaj, alıcının telefonunda çözülür; sunucu içeriği okuyamaz.
 */
class PushClient(private val repo: FirestoreRepository) {

    /** Sunucunun bildirdiği anlık sinyal ("yazıyor…") kanalı bilgisi. */
    data class RealtimeInfo(val url: String, val publicKey: String)

    @Volatile
    var realtime: RealtimeInfo? = null
        private set

    /** Sunucu jetonları kendi veritabanında tutabiliyor mu? (Firestore yazması yerine) */
    @Volatile
    var supportsRegister: Boolean = false
        private set

    /** Fotoğraflar ayrı fotoğraf sunucusuna yüklenebiliyor mu? */
    @Volatile
    var supportsMedia: Boolean = false
        private set

    /** Sunucunun önerdiği tasarruf seviyesi: 0 normal, 1 küçük fotoğraf, 2 küçük fotoğraf + "yazıyor…" kapalı. */
    @Volatile
    var serverSaver: Int = 0
        private set

    @Volatile
    private var lastCheck = 0L

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Bildirim jetonunu kaydeder. Sunucu destekliyorsa jeton Dakk sunucusunda tutulur (Firestore kotası harcanmaz);
     * desteklemiyorsa eskisi gibi Firestore'a yazılır.
     */
    suspend fun registerToken(uid: String, serverUrl: String): Boolean = try {
        val token = FirebaseMessaging.getInstance().token.await()
        val viaServer = supportsRegister && serverUrl.startsWith("https://") &&
            postAuthed(serverUrl, "/register", JSONObject().put("token", token).put("appVersion", BuildConfig.VERSION_CODE)) != null
        if (!viaServer) repo.savePushToken(uid, token)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Bildirim jetonu kaydedilemedi", e)
        false
    }

    suspend fun unregister(uid: String, serverUrl: String) {
        val viaServer = serverUrl.startsWith("https://") && postAuthed(serverUrl, "/unregister", JSONObject()) != null
        if (!viaServer) runCatching { repo.deletePushToken(uid) }
        runCatching { FirebaseMessaging.getInstance().deleteToken().await() }
    }

    /** Sunucu ayakta ve bildirim göndermeye hazır mı? (GET → {"ok":true,"configured":true}) */
    suspend fun isServerReady(serverUrl: String): Boolean {
        if (!serverUrl.startsWith("https://")) return false
        return try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(serverUrl).get().build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use false
                    val body = JSONObject(response.body?.string().orEmpty())
                    body.optJSONObject("realtime")?.let { rt ->
                        val url = rt.optString("url")
                        val key = rt.optString("key")
                        if (url.startsWith("https://") && key.isNotBlank()) realtime = RealtimeInfo(url, key)
                    }
                    val features = body.optJSONObject("features")
                    supportsRegister = features?.optBoolean("register") == true
                    supportsMedia = features?.optBoolean("media") == true
                    serverSaver = body.optInt("saver", 0).coerceIn(0, 2)
                    lastCheck = System.currentTimeMillis()
                    body.optBoolean("ok") && body.optBoolean("configured")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bildirim sunucusuna ulaşılamadı", e)
            false
        }
    }

    /** Tasarruf seviyesini arada bir (en fazla 10 dakikada bir) tazeler. */
    suspend fun refreshIfStale(serverUrl: String) {
        if (System.currentTimeMillis() - lastCheck > 10 * 60_000L) isServerReady(serverUrl)
    }

    // Fotoğraflar: sunucu yalnızca kısa ömürlü, imzalı adresler verir; şifreli veri doğrudan fotoğraf deposuna gider.

    /** Şifreli fotoğrafı yükler. Başarılıysa true. */
    suspend fun uploadMedia(serverUrl: String, chatId: String, mediaId: String, encrypted: ByteArray): Boolean {
        val res = postAuthed(
            serverUrl, "/media/upload",
            JSONObject().put("chatId", chatId).put("mediaId", mediaId).put("bytes", encrypted.size)
        ) ?: return false
        val url = res.optString("url").takeIf { it.startsWith("https://") } ?: return false
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .put(encrypted.toRequestBody("application/octet-stream".toMediaType()))
                .build()
            mediaHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) Log.w(TAG, "Fotoğraf yüklenemedi: HTTP ${response.code}")
                response.isSuccessful
            }
        }
    }

    sealed class MediaFetch {
        class Ok(val bytes: ByteArray) : MediaFetch()
        object Expired : MediaFetch()
        object Failed : MediaFetch()
    }

    suspend fun downloadMedia(serverUrl: String, chatId: String, mediaId: String): MediaFetch {
        val token = idToken() ?: return MediaFetch.Failed
        return try {
            withContext(Dispatchers.IO) {
                val body = JSONObject().put("chatId", chatId).put("mediaId", mediaId).toString()
                val request = Request.Builder()
                    .url("$serverUrl/media/download")
                    .header("Authorization", "Bearer $token")
                    .post(body.toRequestBody(JSON))
                    .build()
                val url = http.newCall(request).execute().use { response ->
                    if (response.code == 404) return@withContext MediaFetch.Expired
                    if (!response.isSuccessful) return@withContext MediaFetch.Failed
                    JSONObject(response.body?.string().orEmpty()).optString("url")
                }
                if (!url.startsWith("https://")) return@withContext MediaFetch.Failed
                mediaHttp.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                    when {
                        response.code == 400 || response.code == 404 -> MediaFetch.Expired
                        !response.isSuccessful -> MediaFetch.Failed
                        else -> {
                            val bytes = response.body?.bytes() ?: return@use MediaFetch.Failed
                            if (bytes.size > MAX_MEDIA_BYTES) MediaFetch.Failed else MediaFetch.Ok(bytes)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fotoğraf indirilemedi", e)
            MediaFetch.Failed
        }
    }

    suspend fun deleteMedia(serverUrl: String, chatId: String, mediaId: String) {
        postAuthed(serverUrl, "/media/delete", JSONObject().put("chatId", chatId).put("mediaId", mediaId))
    }

    private suspend fun idToken(): String? =
        runCatching { Firebase.auth.currentUser?.getIdToken(false)?.await()?.token }.getOrNull()

    /** Kimliği doğrulanmış POST. Başarılıysa yanıt gövdesi, değilse null. */
    private suspend fun postAuthed(serverUrl: String, path: String, body: JSONObject): JSONObject? {
        if (!serverUrl.startsWith("https://")) return null
        val token = idToken() ?: return null
        return try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(serverUrl + path)
                    .header("Authorization", "Bearer $token")
                    .post(body.toString().toRequestBody(JSON))
                    .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Sunucu $path: HTTP ${response.code}")
                        null
                    } else {
                        runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrDefault(JSONObject())
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sunucuya ulaşılamadı ($path)", e)
            null
        }
    }

    suspend fun notifyMessage(serverUrl: String, chatId: String, messageId: String) {
        if (!serverUrl.startsWith("https://")) return
        try {
            val token = Firebase.auth.currentUser?.getIdToken(false)?.await()?.token ?: return
            withContext(Dispatchers.IO) {
                val body = JSONObject().put("chatId", chatId).put("messageId", messageId).toString()
                val request = Request.Builder()
                    .url("$serverUrl/notify")
                    .header("Authorization", "Bearer $token")
                    .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) Log.w(TAG, "Bildirim sunucusu: HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bildirim gönderilemedi", e)
        }
    }

    private val mediaHttp = http.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "DakkPush"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        const val MAX_MEDIA_BYTES = 2 * 1024 * 1024
    }
}
