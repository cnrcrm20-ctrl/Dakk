package com.example.data.repository

import android.content.Context
import com.example.data.db.AppDatabase
import com.example.data.db.ContactEntity
import com.example.data.db.MessageEntity
import com.example.data.db.NetworkLogEntity
import kotlinx.coroutines.flow.Flow

class DakkRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val contactDao = db.contactDao()
    private val messageDao = db.messageDao()
    private val networkLogDao = db.networkLogDao()

    val activeContacts: Flow<List<ContactEntity>> = contactDao.getActiveContacts()
    val blockedContacts: Flow<List<ContactEntity>> = contactDao.getBlockedContacts()
    val networkLogs: Flow<List<NetworkLogEntity>> = networkLogDao.getAllLogs()

    suspend fun getContact(userId: String): ContactEntity? = contactDao.getContact(userId)

    suspend fun saveContact(contact: ContactEntity) {
        contactDao.insertOrUpdate(contact)
    }

    suspend fun setBlocked(userId: String, isBlocked: Boolean) {
        contactDao.setBlocked(userId, isBlocked)
    }

    suspend fun deleteContact(userId: String) {
        contactDao.deleteContact(userId)
        messageDao.clearChatForPeer(userId)
    }

    fun getMessagesForPeer(peerId: String): Flow<List<MessageEntity>> =
        messageDao.getMessagesForPeer(peerId)

    suspend fun saveMessage(message: MessageEntity) {
        messageDao.insertMessage(message)
        contactDao.updateLastMessage(message.peerId, message.content, message.timestamp)
    }

    suspend fun updateMessageStatus(id: String, status: String) {
        messageDao.updateStatus(id, status)
    }

    suspend fun deleteMessage(id: String) {
        messageDao.deleteMessage(id)
    }

    suspend fun clearChat(peerId: String) {
        messageDao.clearChatForPeer(peerId)
        contactDao.updateLastMessage(peerId, "", System.currentTimeMillis())
    }

    suspend fun addNetworkLog(log: NetworkLogEntity) {
        networkLogDao.insertLog(log)
    }

    suspend fun clearNetworkLogs() {
        networkLogDao.clearLogs()
    }
}
