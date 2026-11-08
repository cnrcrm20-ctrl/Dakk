package com.example.data.firebase

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun saveUserProfile(displayName: String, email: String, photoUrl: String = "") {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val payload = mapOf(
            "userId" to uid,
            "displayName" to displayName,
            "email" to email,
            "photoUrl" to photoUrl,
            "status" to "online",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun getUserProfile(userId: String): FirestoreUserProfile? {
        val docRef = db.collection("users").document(userId)
        return try {
            val snapshot = docRef.get().await()
            snapshot.toObject(FirestoreUserProfile::class.java)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            null
        }
    }

    fun observeContacts(): Flow<List<FirestoreContact>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/contacts"
        emitAll(
            db.collection("users").document(uid).collection("contacts")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(FirestoreContact::class.java) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveContact(contactUserId: String, alias: String, email: String = "", photoUrl: String = "") {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("contacts").document(contactUserId)
        val payload = mapOf(
            "contactUserId" to contactUserId,
            "alias" to alias,
            "email" to email,
            "photoUrl" to photoUrl,
            "isBlocked" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteContact(contactUserId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("contacts").document(contactUserId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun ensureChatExists(chatId: String, memberUids: List<String>) {
        val docRef = db.collection("chats").document(chatId)
        try {
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                val payload = mapOf(
                    "chatId" to chatId,
                    "memberUids" to memberUids,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    fun observeMessages(chatId: String): Flow<List<FirestoreChatMessage>> = flow {
        requireUserId()
        val path = "chats/$chatId/messages"
        emitAll(
            db.collection("chats").document(chatId).collection("messages")
                .orderBy("createdAt")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(FirestoreChatMessage::class.java) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun sendMessage(
        chatId: String,
        senderName: String,
        content: String,
        messageType: String = "TEXT",
        mediaUrl: String = ""
    ): String {
        val uid = requireUserId()
        val msgDoc = db.collection("chats").document(chatId).collection("messages").document()
        val chatDoc = db.collection("chats").document(chatId)

        val payload = mapOf(
            "chatId" to chatId,
            "senderId" to uid,
            "senderName" to senderName,
            "content" to content,
            "deliveryStatus" to "SENT",
            "messageType" to messageType,
            "mediaUrl" to mediaUrl,
            "createdAt" to FieldValue.serverTimestamp()
        )

        try {
            msgDoc.set(payload).await()
            chatDoc.update(
                mapOf(
                    "lastMessage" to content,
                    "lastMessageSenderId" to uid,
                    "lastMessageTimestamp" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            return msgDoc.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgDoc.path)
            throw e
        }
    }

    suspend fun updateMessageDeliveryStatus(chatId: String, messageId: String, status: String) {
        val docRef = db.collection("chats").document(chatId).collection("messages").document(messageId)
        try {
            docRef.update("deliveryStatus", status).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun publishRendezvousOffer(roomId: String, offerSdp: String, senderName: String, recipientUid: String = "") {
        val uid = requireUserId()
        val docRef = db.collection("rendezvous").document(roomId)
        val payload = mapOf(
            "roomId" to roomId,
            "senderUid" to uid,
            "senderName" to senderName,
            "recipientUid" to recipientUid,
            "status" to "PENDING",
            "signalOffer" to offerSdp,
            "signalAnswer" to "",
            "iceCandidates" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun answerRendezvous(roomId: String, answerSdp: String) {
        requireUserId()
        val docRef = db.collection("rendezvous").document(roomId)
        val updates = mapOf(
            "signalAnswer" to answerSdp,
            "status" to "ACCEPTED",
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    fun observeRendezvous(roomId: String): Flow<FirestoreRendezvous?> = flow {
        requireUserId()
        val path = "rendezvous/$roomId"
        emitAll(
            db.collection("rendezvous").document(roomId)
                .snapshots()
                .map { snapshot -> snapshot.toObject(FirestoreRendezvous::class.java) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }
}
