package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "network_logs")
data class NetworkLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val peerId: String,
    val operatorName: String, // Turkcell, Vodafone, Türk Telekom, Wi-Fi vb.
    val networkType: String, // MOBILE, WIFI
    val iceState: String, // CHECKING, CONNECTED, COMPLETED, FAILED, DISCONNECTED
    val stunLatencyMs: Long,
    val rttMs: Long,
    val candidateTypes: String, // host, srflx
    val statusSummary: String
)
