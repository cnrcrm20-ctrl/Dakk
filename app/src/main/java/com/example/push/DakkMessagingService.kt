package com.example.push

import android.content.Context
import android.util.Log
import com.example.crypto.CryptoEngine
import com.example.data.FirestoreRepository
import com.example.notification.NotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Uygulama kapalıyken gelen mesaj bildirimlerini işler.
 * Sunucudan gelen veri şifrelidir; içerik burada, cihazdaki anahtarla çözülür.
 */
class DakkMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        scope.launch {
            runCatching { FirestoreRepository(applicationContext).savePushToken(uid, token) }
                .onFailure { Log.w(TAG, "Yeni bildirim jetonu kaydedilemedi", it) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] != "msg") return
        // Uygulama açıksa bildirimi uygulamanın kendisi yönetir.
        if (AppState.foreground) return

        val myUid = Firebase.auth.currentUser?.uid ?: return
        val chatId = data["chatId"].orEmpty()
        val messageId = data["messageId"].orEmpty()
        val senderId = data["senderId"].orEmpty()
        if (chatId.isBlank() || messageId.isBlank() || senderId.isBlank() || senderId == myUid) return
        if (!markSeen(messageId)) return

        var content = data["content"].orEmpty()
        var senderKey = data["senderKey"].orEmpty()
        var recipientKey = data["recipientKey"].orEmpty()
        var messageType = data["messageType"] ?: "TEXT"
        var isAssistant = data["assistant"] == "1"

        // İçerik bildirim sınırına sığmadıysa mesajı doğrudan Firestore'dan oku.
        if (content.isBlank()) {
            val doc = runBlocking {
                withTimeoutOrNull(8_000) {
                    runCatching { FirestoreRepository(applicationContext).getMessage(chatId, messageId) }.getOrNull()
                }
            }
            if (doc != null) {
                content = doc.content
                senderKey = doc.senderKey
                recipientKey = doc.recipientKey
                messageType = doc.messageType
                isAssistant = doc.isAssistant
            }
        }

        val plain = runCatching {
            CryptoEngine(applicationContext).decryptMessage(content, senderKey, recipientKey)
        }.getOrNull()
        // Ekran görüntüsü notları sohbet içinde gösterilir; ayrı bildirim çıkarmaz.
        if (messageType == "SCREENSHOT") return
        val text = when {
            messageType == "IMAGE" -> "📷 Fotoğraf"
            plain == null -> "Yeni şifreli mesaj"
            isAssistant -> "🤖 $plain"
            else -> plain
        }
        val name = data["senderName"]?.ifBlank { null } ?: "Dakk"
        NotificationHelper(applicationContext).showMessage(senderId, name, text, chatId = chatId, messageId = messageId)
    }

    /** Aynı mesaj için ikinci kez bildirim göstermemek üzere son mesaj kimliklerini tutar. */
    private fun markSeen(messageId: String): Boolean {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = prefs.getString(KEY_SEEN, "").orEmpty().split(',').filter { it.isNotBlank() }
        if (messageId in seen) return false
        prefs.edit().putString(KEY_SEEN, (seen.takeLast(49) + messageId).joinToString(",")).apply()
        return true
    }

    companion object {
        private const val TAG = "DakkPushService"
        private const val PREFS = "dakk_push"
        private const val KEY_SEEN = "seen_ids"
    }
}
