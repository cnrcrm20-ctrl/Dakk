package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Firestore erişim katmanı. Tüm yazma işlemleri depodaki firestore.rules ile uyumludur.
 * Mesaj içerikleri bu katmana her zaman şifrelenmiş olarak gelir.
 */
class FirestoreRepository(context: Context) {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(
        context.applicationContext.getString(R.string.firestore_database_id)
    )

    private val estimate = DocumentSnapshot.ServerTimestampBehavior.ESTIMATE

    // ---------------------------------------------------------------- Profiller

    suspend fun publishProfile(uid: String, displayName: String, email: String, photoUrl: String, publicKey: String) {
        val payload = mapOf(
            "userId" to uid,
            "displayName" to displayName.trim().ifBlank { "Dakk Kullanıcısı" }.take(60),
            "email" to email.trim().lowercase().take(120),
            "photoUrl" to photoUrl.take(1000),
            "status" to "online",
            "publicKey" to publicKey,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("users").document(uid).set(payload).await()
    }

    fun observeProfile(uid: String): Flow<UserProfile?> =
        db.collection("users").document(uid).snapshots().map { it.toProfile() }

    suspend fun getProfile(uid: String): UserProfile? =
        db.collection("users").document(uid).get().await().toProfile()

    suspend fun findProfileByEmail(email: String): UserProfile? =
        db.collection("users")
            .whereEqualTo("email", email.trim().lowercase())
            .limit(1)
            .get().await()
            .documents.firstOrNull()?.toProfile()

    private fun DocumentSnapshot.toProfile(): UserProfile? {
        if (!exists()) return null
        return UserProfile(
            uid = id,
            displayName = getString("displayName").orEmpty(),
            email = getString("email").orEmpty(),
            photoUrl = getString("photoUrl").orEmpty(),
            publicKey = getString("publicKey").orEmpty()
        )
    }

    // ---------------------------------------------------------------- Kişiler

    fun observeContacts(myUid: String): Flow<List<Contact>> =
        db.collection("users").document(myUid).collection("contacts")
            .snapshots()
            .map { snap ->
                snap.documents.map { d ->
                    Contact(
                        uid = d.getString("contactUserId") ?: d.id,
                        alias = d.getString("alias").orEmpty(),
                        email = d.getString("email").orEmpty(),
                        photoUrl = d.getString("photoUrl").orEmpty(),
                        isBlocked = d.getBoolean("isBlocked") ?: false
                    )
                }
            }

    suspend fun saveContact(myUid: String, profile: UserProfile) {
        val payload = mapOf(
            "contactUserId" to profile.uid,
            "alias" to profile.displayName.take(60),
            "email" to profile.email.take(120),
            "photoUrl" to profile.photoUrl.take(1000),
            "isBlocked" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("users").document(myUid).collection("contacts").document(profile.uid)
            .set(payload).await()
    }

    suspend fun setBlocked(myUid: String, peer: UserProfile, blocked: Boolean) {
        val payload = mapOf(
            "contactUserId" to peer.uid,
            "alias" to peer.displayName.ifBlank { "Dakk Kullanıcısı" }.take(60),
            "email" to peer.email.take(120),
            "photoUrl" to peer.photoUrl.take(1000),
            "isBlocked" to blocked,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("users").document(myUid).collection("contacts").document(peer.uid)
            .set(payload).await()
    }

    suspend fun deleteContact(myUid: String, peerUid: String) {
        db.collection("users").document(myUid).collection("contacts").document(peerUid)
            .delete().await()
    }

    // ---------------------------------------------------------------- Sohbetler

    fun observeChats(myUid: String): Flow<List<ChatDoc>> =
        db.collection("chats")
            .whereArrayContains("memberUids", myUid)
            .snapshots()
            .map { snap ->
                snap.documents.map { d ->
                    @Suppress("UNCHECKED_CAST")
                    ChatDoc(
                        chatId = d.id,
                        memberUids = (d.get("memberUids") as? List<String>).orEmpty(),
                        lastMessage = d.getString("lastMessage").orEmpty(),
                        lastMessageSenderId = d.getString("lastMessageSenderId").orEmpty(),
                        lastMessageSenderKey = d.getString("lastMessageSenderKey").orEmpty(),
                        lastMessageRecipientKey = d.getString("lastMessageRecipientKey").orEmpty(),
                        lastMessageTime = d.getTimestamp("lastMessageTimestamp", estimate)?.toDate()?.time ?: 0L
                    )
                }
            }

    fun observeMessages(chatId: String): Flow<List<MessageDoc>> =
        db.collection("chats").document(chatId).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots(MetadataChanges.INCLUDE)
            .map { snap ->
                snap.documents.map { d -> d.toMessage() }
            }

    suspend fun getMessage(chatId: String, messageId: String): MessageDoc? {
        val d = db.collection("chats").document(chatId).collection("messages").document(messageId)
            .get().await()
        return if (d.exists()) d.toMessage() else null
    }

    private fun DocumentSnapshot.toMessage(): MessageDoc = MessageDoc(
        id = id,
        senderId = getString("senderId").orEmpty(),
        content = getString("content").orEmpty(),
        senderKey = getString("senderKey").orEmpty(),
        recipientKey = getString("recipientKey").orEmpty(),
        deliveryStatus = getString("deliveryStatus") ?: "SENT",
        createdAt = getTimestamp("createdAt", estimate)?.toDate()?.time ?: System.currentTimeMillis(),
        isPending = metadata.hasPendingWrites(),
        messageType = getString("messageType") ?: "TEXT",
        isAssistant = getBoolean("isAssistant") ?: false,
        reactions = (get("reactions") as? Map<*, *>)
            ?.mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }
            ?.toMap()
            .orEmpty(),
        replyTo = getString("replyTo")?.ifBlank { null },
        replyToSender = getString("replyToSender")?.ifBlank { null },
        replyPreview = getString("replyPreview")?.ifBlank { null }
    )

    /** Yanıtlanan mesaj bilgisi (alıntı metni şifrelidir). */
    data class ReplyFields(val messageId: String, val senderUid: String, val encryptedPreview: String)

    /**
     * Mesaj gönderir. Önce sohbet belgesi (üyelik + şifreli önizleme), ardından mesaj yazılır.
     * Yazmalar sırayla kuyruğa alındığı için çevrimdışıyken de güvenle çalışır.
     * [updateChat] false ise sohbet listesi önizlemesi değişmez (ör. ekran görüntüsü bildirimi).
     */
    fun sendMessage(
        chatId: String,
        myUid: String,
        peerUid: String,
        senderName: String,
        encryptedContent: String,
        encryptedPreview: String,
        senderKey: String,
        recipientKey: String,
        messageType: String = "TEXT",
        isAssistant: Boolean = false,
        reply: ReplyFields? = null,
        updateChat: Boolean = true,
        onSent: (messageId: String) -> Unit = {},
        onError: (Exception) -> Unit
    ): String {
        val (chatTask, messageRef, messageTask) = writeMessage(
            chatId, myUid, peerUid, senderName, encryptedContent, encryptedPreview,
            senderKey, recipientKey, messageType, isAssistant, reply, updateChat
        )
        chatTask?.addOnFailureListener { e -> Log.e(TAG, "Sohbet güncellenemedi", e); onError(e) }
        messageTask
            .addOnSuccessListener { onSent(messageRef.id) }
            .addOnFailureListener { e -> Log.e(TAG, "Mesaj gönderilemedi", e); onError(e) }
        return messageRef.id
    }

    /** [sendMessage] ile aynı, ancak sunucu yazmayı onaylayana kadar bekler (bildirimden yanıt için). */
    suspend fun sendMessageAndWait(
        chatId: String,
        myUid: String,
        peerUid: String,
        senderName: String,
        encryptedContent: String,
        encryptedPreview: String,
        senderKey: String,
        recipientKey: String
    ): String {
        val (chatTask, messageRef, messageTask) = writeMessage(
            chatId, myUid, peerUid, senderName, encryptedContent, encryptedPreview,
            senderKey, recipientKey, "TEXT", false, null, true
        )
        chatTask?.await()
        messageTask.await()
        return messageRef.id
    }

    private fun writeMessage(
        chatId: String,
        myUid: String,
        peerUid: String,
        senderName: String,
        encryptedContent: String,
        encryptedPreview: String,
        senderKey: String,
        recipientKey: String,
        messageType: String,
        isAssistant: Boolean,
        reply: ReplyFields?,
        updateChat: Boolean
    ): Triple<Task<Void>?, DocumentReference, Task<Void>> {
        val chatRef = db.collection("chats").document(chatId)
        val chatTask = if (updateChat) {
            val chatPayload = mapOf(
                "chatId" to chatId,
                "memberUids" to listOf(myUid, peerUid).sorted(),
                "lastMessage" to encryptedPreview,
                "lastMessageSenderId" to myUid,
                "lastMessageSenderKey" to senderKey,
                "lastMessageRecipientKey" to recipientKey,
                "lastMessageTimestamp" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            chatRef.set(chatPayload, SetOptions.merge())
        } else null

        val messagePayload = buildMap<String, Any> {
            put("chatId", chatId)
            put("senderId", myUid)
            put("senderName", senderName.ifBlank { "Dakk" }.take(60))
            put("content", encryptedContent)
            put("senderKey", senderKey)
            put("recipientKey", recipientKey)
            put("deliveryStatus", "SENT")
            put("messageType", messageType)
            put("createdAt", FieldValue.serverTimestamp())
            if (isAssistant) put("isAssistant", true)
            if (reply != null) {
                put("replyTo", reply.messageId)
                put("replyToSender", reply.senderUid)
                put("replyPreview", reply.encryptedPreview)
            }
        }
        val messageRef = chatRef.collection("messages").document()
        return Triple(chatTask, messageRef, messageRef.set(messagePayload))
    }

    /** Sohbet belgesinin var olduğundan emin olur (fotoğraf parçaları yüklenmeden önce gerekli). */
    suspend fun ensureChat(chatId: String, myUid: String, peerUid: String) {
        val payload = mapOf(
            "chatId" to chatId,
            "memberUids" to listOf(myUid, peerUid).sorted(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("chats").document(chatId).set(payload, SetOptions.merge()).await()
    }

    // ---------------------------------------------------------------- Fotoğraf parçaları
    // chats/{chatId}/blobs/{mediaId}_{index} — şifreli fotoğraf verisi (Base64).
    // Alıcı fotoğrafı indirince parçaları siler; gönderici 7 gün sonra kalanları temizler.

    private fun blobRef(chatId: String, mediaId: String, index: Int) =
        db.collection("chats").document(chatId).collection("blobs").document("${mediaId}_$index")

    suspend fun uploadBlob(chatId: String, mediaId: String, index: Int, total: Int, myUid: String, data: String) {
        val payload = mapOf(
            "mediaId" to mediaId,
            "index" to index,
            "total" to total,
            "senderId" to myUid,
            "data" to data,
            "createdAt" to FieldValue.serverTimestamp()
        )
        blobRef(chatId, mediaId, index).set(payload).await()
    }

    /** Parça yoksa (süresi dolmuş/silinmiş) null döner. */
    suspend fun getBlob(chatId: String, mediaId: String, index: Int): String? {
        val doc = blobRef(chatId, mediaId, index).get(Source.SERVER).await()
        return if (doc.exists()) doc.getString("data") else null
    }

    suspend fun deleteBlobs(chatId: String, mediaId: String, total: Int) {
        val batch = db.batch()
        for (i in 0 until total.coerceIn(1, 50)) batch.delete(blobRef(chatId, mediaId, i))
        batch.commit().await()
    }

    // ---------------------------------------------------------------- Anlık bildirim jetonu

    suspend fun savePushToken(uid: String, token: String) {
        val payload = mapOf(
            "token" to token,
            "platform" to "android",
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("push_tokens").document(uid).set(payload).await()
    }

    suspend fun deletePushToken(uid: String) {
        db.collection("push_tokens").document(uid).delete().await()
    }

    /** Sohbet listesi önizlemesini günceller (ör. son mesaj silindiğinde). */
    fun sendPreviewOnly(
        chatId: String,
        myUid: String,
        peerUid: String,
        encryptedPreview: String,
        senderKey: String,
        recipientKey: String
    ) {
        val payload = mapOf(
            "chatId" to chatId,
            "memberUids" to listOf(myUid, peerUid).sorted(),
            "lastMessage" to encryptedPreview,
            "lastMessageSenderId" to myUid,
            "lastMessageSenderKey" to senderKey,
            "lastMessageRecipientKey" to recipientKey,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("chats").document(chatId).set(payload, SetOptions.merge())
            .addOnFailureListener { e -> Log.w(TAG, "Önizleme güncellenemedi", e) }
    }

    fun markRead(chatId: String, messageIds: List<String>) {
        if (messageIds.isEmpty()) return
        val batch = db.batch()
        val messages = db.collection("chats").document(chatId).collection("messages")
        messageIds.take(400).forEach { id -> batch.update(messages.document(id), "deliveryStatus", "READ") }
        batch.commit().addOnFailureListener { e -> Log.w(TAG, "Okundu bilgisi yazılamadı", e) }
    }

    /**
     * Bu kişinin bir mesaja bıraktığı tepkiyi yazar ([encryptedEmoji] null ise kaldırır).
     * Kurallar herkesin yalnızca kendi tepkisini değiştirmesine izin verir.
     */
    suspend fun setReaction(chatId: String, messageId: String, myUid: String, encryptedEmoji: String?) {
        db.collection("chats").document(chatId).collection("messages").document(messageId)
            .update(FieldPath.of("reactions", myUid), encryptedEmoji ?: FieldValue.delete())
            .await()
    }

    /** Karşı taraftan gelen ve henüz okunmamış tüm mesajları "okundu" yapar; işaretlenen sayısını döner. */
    suspend fun markAllReadFrom(chatId: String, peerUid: String): Int {
        val messages = db.collection("chats").document(chatId).collection("messages")
        val unread = messages
            .whereEqualTo("senderId", peerUid)
            .whereEqualTo("deliveryStatus", "SENT")
            .limit(400)
            .get().await()
        if (unread.isEmpty) return 0
        val batch = db.batch()
        unread.documents.forEach { batch.update(it.reference, "deliveryStatus", "READ") }
        batch.commit().await()
        return unread.size()
    }

    /** Karşı taraftan gelen en son mesaj (bildirimdeki ❤️ düğmesi için). */
    suspend fun latestMessageFrom(chatId: String, peerUid: String): MessageDoc? =
        db.collection("chats").document(chatId).collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(20)
            .get().await()
            .documents
            .map { it.toMessage() }
            .firstOrNull { it.senderId == peerUid && it.messageType != "SCREENSHOT" }

    suspend fun deleteMessage(chatId: String, messageId: String) {
        db.collection("chats").document(chatId).collection("messages").document(messageId)
            .delete().await()
    }

    companion object {
        private const val TAG = "DakkFirestore"
    }
}
