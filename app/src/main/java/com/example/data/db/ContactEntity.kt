package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey
    val userId: String, // 512 karakterlik benzersiz kimlik veya Grup ID
    val alias: String, // Kişi Adı veya Grup Adı
    val publicKey: String,
    val addedTimestamp: Long = System.currentTimeMillis(),
    val isBlocked: Boolean = false,
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L,
    val unreadCount: Int = 0,
    val isGroup: Boolean = false, // Grup sohbeti mi?
    val groupMembers: String = "" // Katılımcıların rumuzları virgülle ayrılmış
)
