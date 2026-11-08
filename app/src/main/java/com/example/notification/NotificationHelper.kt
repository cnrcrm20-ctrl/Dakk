package com.example.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class NotificationHelper(context: Context) {

    private val appContext = context.applicationContext

    companion object {
        const val CHANNEL_MESSAGES = "dakk_messages"
        const val EXTRA_OPEN_CHAT_UID = "dakk.extra.OPEN_CHAT_UID"
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = appContext.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Mesajlar",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Yeni mesaj bildirimleri"
                enableVibration(true)
            }
            manager?.createNotificationChannel(channel)
        }
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }

    fun showMessage(peerUid: String, senderName: String, text: String) {
        if (!canNotify()) return
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_CHAT_UID, peerUid)
        }
        val pending = PendingIntent.getActivity(
            appContext,
            peerUid.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_dakk)
            .setColor(0xFFFFCC00.toInt())
            .setContentTitle(senderName)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try {
            NotificationManagerCompat.from(appContext).notify(peerUid.hashCode(), notification)
        } catch (_: SecurityException) {
        }
    }

    fun cancelFor(peerUid: String) {
        NotificationManagerCompat.from(appContext).cancel(peerUid.hashCode())
    }
}
