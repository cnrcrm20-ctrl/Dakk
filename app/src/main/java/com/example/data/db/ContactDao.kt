package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Query("SELECT * FROM contacts WHERE isBlocked = 0 ORDER BY lastMessageTime DESC, addedTimestamp DESC")
    fun getActiveContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE isBlocked = 1 ORDER BY addedTimestamp DESC")
    fun getBlockedContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE userId = :userId LIMIT 1")
    suspend fun getContact(userId: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(contact: ContactEntity)

    @Update
    suspend fun update(contact: ContactEntity)

    @Query("UPDATE contacts SET lastMessage = :msg, lastMessageTime = :time WHERE userId = :userId")
    suspend fun updateLastMessage(userId: String, msg: String, time: Long)

    @Query("UPDATE contacts SET isBlocked = :blocked WHERE userId = :userId")
    suspend fun setBlocked(userId: String, blocked: Boolean)

    @Query("DELETE FROM contacts WHERE userId = :userId")
    suspend fun deleteContact(userId: String)
}
