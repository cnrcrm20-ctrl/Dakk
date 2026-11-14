package com.example.push

import android.content.Context
import android.util.Log
import com.example.crypto.CryptoEngine
import com.example.data.ChatIds
import com.example.data.ChatPrefs
import com.example.data.FirestoreRepository
import com.example.data.MessageDoc
import com.example.notification.NotificationHelper
import com.example.util.MuteOptions
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
        // Sessize alınan sohbetler bildirim göstermez.
        val mainPrefs = getSharedPreferences(MAIN_PREFS, Context.MODE_PRIVATE)
        if (MuteOptions.isMuted(mainPrefs.getLong(ChatPrefs.PREFIX_MUTE + chatId, 0L), System.currentTimeMillis())) return
        if (!markSeen(messageId)) return

        val isGroup = data["group"] == "1" || ChatIds.isGroup(chatId)
        var content = data["content"].orEmpty()
        var senderKey = data["senderKey"].orEmpty()
        var recipientKey = data["recipientKey"].orEmpty()
        var messageType = data["messageType"] ?: "TEXT"
        var isAssistant = data["assistant"] == "1"
        var doc: MessageDoc? = null

        // Grup mesajları ve bildirim sınırına sığmayan içerik doğrudan Firestore'dan okunur.
        if (isGroup || content.isBlank()) {
            doc = runBlocking {
                withTimeoutOrNull(8_000) {
                    runCatching { FirestoreRepository(applicationContext).getMessage(chatId, messageId) }.getOrNull()
                }
            }
            doc?.let {
                content = it.content
                senderKey = it.senderKey
                recipientKey = it.recipientKey
                messageType = it.messageType
                isAssistant = it.isAssistant
            }
        }
        // Ekran görüntüsü ve sistem notları sohbet içinde gösterilir; ayrı bildirim çıkarmaz.
        if (messageType == "SCREENSHOT" || messageType == "SYSTEM") return

        val crypto = CryptoEngine(applicationContext)
        val plain = runCatching {
            if (isGroup) {
                val wrapped = doc?.keys?.get(myUid)
                val key = wrapped?.let { crypto.unwrapKey(it, senderKey) }
                key?.let { crypto.decryptWithKey(content, it) }
            } else {
                crypto.decryptMessage(content, senderKey, recipientKey)
            }
        }.getOrNull()
        val text = when {
            messageType == "IMAGE" -> "📷 Fotoğraf"
            messageType == "POLL" -> "📊 Anket"
            plain == null -> "Yeni şifreli mesaj"
            isAssistant -> "🤖 $plain"
            else -> plain
        }
        val name = data["senderName"]?.ifBlank { null } ?: doc?.senderName?.ifBlank { null } ?: "Dakk"
        val title = if (isGroup) "${data["groupName"]?.ifBlank { null } ?: "Grup"} · $name" else name
        val key = if (isGroup) chatId else senderId
        NotificationHelper(applicationContext).showMessage(key, title, text, chatId = chatId, messageId = messageId)
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
        private const val MAIN_PREFS = "dakk_prefs"
        private const val KEY_SEEN = "seen_ids"
    }
}
