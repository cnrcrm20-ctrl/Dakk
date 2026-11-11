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
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.util.Reactions

class NotificationHelper(context: Context) {

    private val appContext = context.applicationContext

    companion object {
        const val CHANNEL_MESSAGES = "dakk_messages"
        const val EXTRA_OPEN_CHAT_UID = "dakk.extra.OPEN_CHAT_UID"

        const val ACTION_REPLY = "com.example.dakk.action.REPLY"
        const val ACTION_MARK_READ = "com.example.dakk.action.MARK_READ"
        const val ACTION_HEART = "com.example.dakk.action.HEART"
        const val EXTRA_PEER_UID = "dakk.extra.PEER_UID"
        const val EXTRA_CHAT_ID = "dakk.extra.CHAT_ID"
        const val EXTRA_MESSAGE_ID = "dakk.extra.MESSAGE_ID"
        const val EXTRA_SENDER_NAME = "dakk.extra.SENDER_NAME"
        const val KEY_REPLY_TEXT = "dakk.reply.TEXT"

        fun notificationId(peerUid: String): Int = peerUid.hashCode()
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
                // Kilit ekranında içerik, telefonun "hassas içeriği gizle" ayarına göre gösterilir.
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
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

    /**
     * Mesaj bildirimi gösterir. [chatId] biliniyorsa "Cevapla", "Görüldü" ve "❤️" düğmeleri eklenir.
     * [messageId] verilirse ❤️ o mesaja bırakılır; verilmezse karşı tarafın son mesajına.
     */
    fun showMessage(
        peerUid: String,
        senderName: String,
        text: String,
        chatId: String? = null,
        messageId: String? = null
    ) {
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

        // Kilit ekranında "hassas içeriği gizle" açıksa yalnızca bu sürüm görünür.
        val publicVersion = NotificationCompat.Builder(appContext, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_dakk)
            .setColor(0xFFFFCC00.toInt())
            .setContentTitle("Dakk")
            .setContentText("Yeni mesaj")
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        val builder = NotificationCompat.Builder(appContext, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_dakk)
            .setColor(0xFFFFCC00.toInt())
            .setContentTitle(senderName)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setContentIntent(pending)

        if (!chatId.isNullOrBlank()) {
            builder.addAction(replyAction(peerUid, chatId, senderName))
            builder.addAction(
                NotificationCompat.Action.Builder(
                    0, "Görüldü", actionIntent(ACTION_MARK_READ, 1, peerUid, chatId, messageId, senderName)
                )
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                    .setShowsUserInterface(false)
                    .build()
            )
            builder.addAction(
                NotificationCompat.Action.Builder(
                    0, Reactions.HEART, actionIntent(ACTION_HEART, 2, peerUid, chatId, messageId, senderName)
                )
                    .setShowsUserInterface(false)
                    .build()
            )
        }

        try {
            NotificationManagerCompat.from(appContext).notify(notificationId(peerUid), builder.build())
        } catch (_: SecurityException) {
        }
    }

    private fun replyAction(peerUid: String, chatId: String, senderName: String): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(KEY_REPLY_TEXT)
            .setLabel("Mesaj yaz")
            .build()
        val intent = Intent(appContext, NotificationActionReceiver::class.java).apply {
            action = ACTION_REPLY
            putExtra(EXTRA_PEER_UID, peerUid)
            putExtra(EXTRA_CHAT_ID, chatId)
            putExtra(EXTRA_SENDER_NAME, senderName)
        }
        // RemoteInput metni ekleyebilsin diye bu PendingIntent değiştirilebilir olmalı (Android 12+).
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pending = PendingIntent.getBroadcast(
            appContext,
            requestCode(peerUid, 0),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutable
        )
        return NotificationCompat.Action.Builder(0, "Cevapla", pending)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
    }

    private fun actionIntent(
        action: String,
        index: Int,
        peerUid: String,
        chatId: String,
        messageId: String?,
        senderName: String
    ): PendingIntent {
        val intent = Intent(appContext, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_PEER_UID, peerUid)
            putExtra(EXTRA_CHAT_ID, chatId)
            putExtra(EXTRA_SENDER_NAME, senderName)
            if (!messageId.isNullOrBlank()) putExtra(EXTRA_MESSAGE_ID, messageId)
        }
        return PendingIntent.getBroadcast(
            appContext,
            requestCode(peerUid, index),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestCode(peerUid: String, index: Int): Int = peerUid.hashCode() * 31 + index + 1

    /** Bildirim düğmesi işlenemediğinde kullanıcıya kısa bir bilgi gösterir. */
    fun showActionResult(peerUid: String, senderName: String, message: String) {
        if (!canNotify()) return
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_CHAT_UID, peerUid)
        }
        val pending = PendingIntent.getActivity(
            appContext, peerUid.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_dakk)
            .setColor(0xFFFFCC00.toInt())
            .setContentTitle(senderName)
            .setContentText(message)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try {
            NotificationManagerCompat.from(appContext).notify(notificationId(peerUid), notification)
        } catch (_: SecurityException) {
        }
    }

    fun cancelFor(peerUid: String) {
        NotificationManagerCompat.from(appContext).cancel(notificationId(peerUid))
    }
}
