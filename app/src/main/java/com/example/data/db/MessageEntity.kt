package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val id: String, // Benzersiz mesaj UUID
    val peerId: String, // İletişim kurulan 512-karakterlik karşı taraf veya Grup ID
    val content: String, // Şifresi çözülmüş metin (yerel saklama)
    val timestamp: Long = System.currentTimeMillis(),
    val isFromMe: Boolean,
    val status: String = "SENT", // PENDING, SENT, DELIVERED, FAILED
    val sequenceNumber: Long = 0L,
    val senderAlias: String = "" // Grupta mesajı gönderenin ismi (ör. "Ahmet")
)
