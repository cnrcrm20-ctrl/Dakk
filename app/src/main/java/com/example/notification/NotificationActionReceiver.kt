package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.example.crypto.CryptoEngine
import com.example.data.FirestoreRepository
import com.example.push.AppState
import com.example.push.PushClient
import com.example.util.Reactions
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Mesaj bildirimindeki düğmeler: "Cevapla" (bildirimden yanıt), "Görüldü" (okundu yap) ve "❤️"
 * (son mesaja kalp tepkisi). Uygulama kapalıyken de çalışır; tüm içerik telefonda şifrelenir.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val peerUid = intent.getStringExtra(NotificationHelper.EXTRA_PEER_UID) ?: return
        val chatId = intent.getStringExtra(NotificationHelper.EXTRA_CHAT_ID) ?: return
        val senderName = intent.getStringExtra(NotificationHelper.EXTRA_SENDER_NAME) ?: "Dakk"
        val messageId = intent.getStringExtra(NotificationHelper.EXTRA_MESSAGE_ID)
        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NotificationHelper.KEY_REPLY_TEXT)
            ?.toString()?.trim()
        val action = intent.action ?: return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val notifications = NotificationHelper(app)
            try {
                withTimeout(TIMEOUT_MS) {
                    when (action) {
                        NotificationHelper.ACTION_REPLY -> reply(app, peerUid, chatId, replyText.orEmpty())
                        NotificationHelper.ACTION_MARK_READ -> markRead(app, peerUid, chatId)
                        NotificationHelper.ACTION_HEART -> heart(app, peerUid, chatId, messageId)
                    }
                }
                notifications.cancelFor(peerUid)
            } catch (e: TimeoutCancellationException) {
                // Firestore yazmayı cihazda saklar; bağlantı gelince gönderilir.
                notifications.showActionResult(
                    peerUid, senderName,
                    if (action == NotificationHelper.ACTION_REPLY) "Yanıtın bağlantı gelince gönderilecek."
                    else "İnternet bağlantısı yok. Bağlantı gelince işlenecek."
                )
            } catch (e: ActionFailed) {
                notifications.showActionResult(peerUid, senderName, e.message ?: "İşlem tamamlanamadı.")
            } catch (e: Exception) {
                Log.w(TAG, "Bildirim düğmesi işlenemedi: $action", e)
                notifications.showActionResult(peerUid, senderName, "İşlem tamamlanamadı. Dakk'ı açıp tekrar deneyin.")
            } finally {
                pending.finish()
            }
        }
    }

    private class ActionFailed(message: String) : Exception(message)

    private fun myUid(): String =
        Firebase.auth.currentUser?.uid ?: throw ActionFailed("Dakk'ta oturum açık değil.")

    private suspend fun reply(app: Context, peerUid: String, chatId: String, text: String) {
        if (text.isEmpty()) return
        if (text.toByteArray(Charsets.UTF_8).size > MAX_REPLY_BYTES) throw ActionFailed("Mesaj çok uzun.")
        val myUid = myUid()
        val repo = FirestoreRepository(app)
        val crypto = CryptoEngine(app)
        val peer = repo.getProfile(peerUid)
        if (peer == null || peer.publicKey.isBlank()) throw ActionFailed("Bu kişiye şu an şifreli mesaj gönderilemiyor.")
        val myName = repo.getProfile(myUid)?.displayName?.ifBlank { null }
            ?: Firebase.auth.currentUser?.displayName ?: "Dakk Kullanıcısı"
        val single = text.replace('\n', ' ')
        val preview = if (single.length > 100) single.take(100) + "…" else single
        val messageId = repo.sendMessageAndWait(
            chatId = chatId,
            myUid = myUid,
            peerUid = peerUid,
            senderName = myName,
            encryptedContent = crypto.encrypt(text, peer.publicKey),
            encryptedPreview = crypto.encrypt(preview, peer.publicKey),
            senderKey = crypto.publicKeyBase64(),
            recipientKey = peer.publicKey
        )
        val server = AppState.rememberedServer(app)
        if (server.isNotBlank()) PushClient(repo).notifyMessage(server, chatId, messageId)
        // Yanıt veren kişi mesajları görmüş sayılır.
        runCatching { markRead(app, peerUid, chatId) }
    }

    private suspend fun markRead(app: Context, peerUid: String, chatId: String) {
        myUid()
        FirestoreRepository(app).markAllReadFrom(chatId, peerUid)
        // Sohbet listesindeki "okunmadı" işaretini de kaldır (uygulama açılınca okunur).
        val prefs = app.getSharedPreferences(MAIN_PREFS, Context.MODE_PRIVATE)
        val key = PREFIX_READ + chatId
        prefs.edit().putLong(key, maxOf(prefs.getLong(key, 0L), System.currentTimeMillis())).apply()
    }

    private suspend fun heart(app: Context, peerUid: String, chatId: String, messageId: String?) {
        val myUid = myUid()
        val repo = FirestoreRepository(app)
        val target = messageId?.let { repo.getMessage(chatId, it) } ?: repo.latestMessageFrom(chatId, peerUid)
            ?: throw ActionFailed("Tepki verilecek mesaj bulunamadı.")
        val cipher = CryptoEngine(app).encryptForMessage(Reactions.HEART, target.senderKey, target.recipientKey)
            ?: throw ActionFailed("Bu mesaja tepki verilemiyor.")
        repo.setReaction(chatId, target.id, myUid, cipher)
        runCatching { markRead(app, peerUid, chatId) }
    }

    companion object {
        private const val TAG = "DakkNotifAction"
        private const val TIMEOUT_MS = 9_000L
        private const val MAX_REPLY_BYTES = 3_500
        private const val MAIN_PREFS = "dakk_prefs"
        private const val PREFIX_READ = "read_"
    }
}
