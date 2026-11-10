package com.example.push

import android.util.Log
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

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun registerToken(uid: String): Boolean = try {
        val token = FirebaseMessaging.getInstance().token.await()
        repo.savePushToken(uid, token)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Bildirim jetonu kaydedilemedi", e)
        false
    }

    suspend fun unregister(uid: String) {
        runCatching { repo.deletePushToken(uid) }
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
                    body.optBoolean("ok") && body.optBoolean("configured")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bildirim sunucusuna ulaşılamadı", e)
            false
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

    companion object {
        private const val TAG = "DakkPush"
    }
}
