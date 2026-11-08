package com.example.data.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class FirestoreUserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val status: String = "online",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreContact(
    @DocumentId val id: String = "",
    val contactUserId: String = "",
    val alias: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val lastMessage: String = "",
    @ServerTimestamp val lastMessageTime: Timestamp? = null,
    val isBlocked: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreChat(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val memberUids: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageSenderId: String = "",
    @ServerTimestamp val lastMessageTimestamp: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreChatMessage(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val content: String = "",
    val deliveryStatus: String = "SENT", // SENT, DELIVERED, READ
    val messageType: String = "TEXT",    // TEXT, AUDIO, IMAGE
    val mediaUrl: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreRendezvous(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val recipientUid: String = "",
    val status: String = "PENDING", // PENDING, ACCEPTED, REJECTED
    val signalOffer: String = "",
    val signalAnswer: String = "",
    val iceCandidates: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
