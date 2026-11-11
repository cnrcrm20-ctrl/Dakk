package com.example.data

/** Firestore: users/{uid} */
data class UserProfile(
    val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String,
    val publicKey: String
)

/** Firestore: users/{me}/contacts/{uid} */
data class Contact(
    val uid: String,
    val alias: String,
    val email: String,
    val photoUrl: String,
    val isBlocked: Boolean
)

/** Firestore: chats/{chatId} */
data class ChatDoc(
    val chatId: String,
    val memberUids: List<String>,
    val lastMessage: String,
    val lastMessageSenderId: String,
    val lastMessageSenderKey: String,
    val lastMessageRecipientKey: String,
    val lastMessageTime: Long
)

/** Firestore: chats/{chatId}/messages/{id} */
data class MessageDoc(
    val id: String,
    val senderId: String,
    val content: String,
    val senderKey: String,
    val recipientKey: String,
    val deliveryStatus: String,
    val createdAt: Long,
    val isPending: Boolean,
    val messageType: String = "TEXT",
    val isAssistant: Boolean = false,
    /** Kullanıcı kimliği → şifreli tepki emojisi. */
    val reactions: Map<String, String> = emptyMap(),
    /** Yanıtlanan mesajın kimliği (yoksa null). */
    val replyTo: String? = null,
    /** Yanıtlanan mesajı gönderen kişinin kimliği. */
    val replyToSender: String? = null,
    /** Yanıtlanan mesajdan kısa, şifreli alıntı. */
    val replyPreview: String? = null
)

enum class MessageStatus { PENDING, SENT, READ }

/** Fotoğrafın bu cihazdaki durumu. */
enum class ImageState { READY, UPLOADING, DOWNLOADING, EXPIRED, FAILED }

/** Mesajdaki fotoğraf. localPath doluysa fotoğraf cihazda hazırdır. */
data class ImageAttachment(
    val mediaId: String,
    val chunks: Int,
    val width: Int,
    val height: Int,
    val size: Long,
    val thumbBase64: String?,
    val localPath: String?,
    val state: ImageState
)

/**
 * Arayüzde gösterilen mesaj. text == null ve image == null ise mesaj bu cihazda çözülemedi.
 * isAssistant: mesaj Dakk Asistan'ın sohbete eklediği bir yanıttır.
 */
data class UiMessage(
    val id: String,
    val text: String?,
    val isFromMe: Boolean,
    val timestamp: Long,
    val status: MessageStatus,
    val image: ImageAttachment? = null,
    val isAssistant: Boolean = false,
    /** Tepkiler: kullanıcı kimliği → emoji (çözülmüş). */
    val reactions: Map<String, String> = emptyMap(),
    /** Bu mesaj bir yanıtsa alıntılanan mesajın özeti. */
    val reply: ReplyQuote? = null,
    /** "Ekran görüntüsü aldı" gibi sohbet içi sistem satırı. */
    val isScreenshotNotice: Boolean = false
)

/** Yanıtlanan mesajın baloncukta gösterilen alıntısı. */
data class ReplyQuote(
    val messageId: String,
    val fromMe: Boolean,
    val text: String
)

/** Sohbet listesi satırı. */
data class ChatSummary(
    val chatId: String,
    val peerUid: String,
    val peerName: String,
    val peerPhotoUrl: String,
    val preview: String,
    val lastMessageTime: Long,
    val lastFromMe: Boolean,
    val isUnread: Boolean
)

object ChatIds {
    fun of(a: String, b: String): String = listOf(a, b).sorted().joinToString("_")
}
