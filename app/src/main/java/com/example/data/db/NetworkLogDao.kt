package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkLogDao {

    @Query("SELECT * FROM network_logs ORDER BY timestamp DESC LIMIT 100")
    fun getAllLogs(): Flow<List<NetworkLogEntity>>

    @Insert
    suspend fun insertLog(log: NetworkLogEntity)

    @Query("DELETE FROM network_logs")
    suspend fun clearLogs()
}
