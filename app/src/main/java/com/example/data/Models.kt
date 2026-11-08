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
    val isPending: Boolean
)

enum class MessageStatus { PENDING, SENT, READ }

/** Arayüzde gösterilen mesaj. text == null ise mesaj bu cihazda çözülemedi. */
data class UiMessage(
    val id: String,
    val text: String?,
    val isFromMe: Boolean,
    val timestamp: Long,
    val status: MessageStatus
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
