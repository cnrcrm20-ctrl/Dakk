package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_MESSAGES_ID = "dakk_p2p_messages_channel"
        const val CHANNEL_MESSAGES_NAME = "Dakk P2P Mesajlar"

        const val CHANNEL_CALLS_ID = "dakk_p2p_calls_channel"
        const val CHANNEL_CALLS_NAME = "Dakk P2P Aramalar"

        const val CHANNEL_FRIENDS_ID = "dakk_p2p_friends_channel"
        const val CHANNEL_FRIENDS_NAME = "Dakk Bağlantı ve Arkadaşlık"

        const val CALL_NOTIFICATION_ID = 9991
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Mesaj Kanalı
            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES_ID,
                CHANNEL_MESSAGES_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Dakk P2P Şifreli Mesaj Bildirimleri"
                enableVibration(true)
                enableLights(true)
            }

            // Arama Kanalı (Zil Sesi ve Titreşim)
            val callsChannel = NotificationChannel(
                CHANNEL_CALLS_ID,
                CHANNEL_CALLS_NAME,
                NotificationManager.IMPORTANCE_MAX
            ).apply {
                description = "Dakk P2P Sesli ve Görüntülü Arama Bildirimleri"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), null)
                enableLights(true)
            }

            // Arkadaşlık & Eşleşme Kanalı
            val friendsChannel = NotificationChannel(
                CHANNEL_FRIENDS_ID,
                CHANNEL_FRIENDS_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Dakk Eşleşme ve Arkadaşlık Bildirimleri"
                enableVibration(true)
                enableLights(true)
            }

            notificationManager.createNotificationChannels(listOf(messagesChannel, callsChannel, friendsChannel))
        }
    }

    private fun getAppLargeIcon() = try {
        BitmapFactory.decodeResource(context.resources, R.drawable.dakk_logo)
    } catch (e: Exception) {
        null
    }

    fun showMessageNotification(senderAlias: String, messageText: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayContent = when {
            messageText.startsWith("📷") -> "📷 Fotoğraf paylaşıldı"
            messageText.startsWith("🎙️") -> "🎙️ Sesli mesaj"
            messageText.startsWith("📄") -> "📄 Belge gönderildi"
            else -> messageText
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🐥 $senderAlias")
            .setContentText(displayContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayContent))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        getAppLargeIcon()?.let { builder.setLargeIcon(it) }

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    fun showFriendConnectedNotification(peerAlias: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_FRIENDS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🎉 $peerAlias ile Eşleşildi!")
            .setContentText("Uçtan uca şifreli doğrudan P2P tünel hazır. Vak vak! 🐥")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        getAppLargeIcon()?.let { builder.setLargeIcon(it) }

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    fun showIncomingCallNotification(callerAlias: String, isVideo: Boolean) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callTypeDesc = if (isVideo) "Görüntülü Arama" else "Sesli Arama"

        val builder = NotificationCompat.Builder(context, CHANNEL_CALLS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🐥 Dakk $callTypeDesc")
            .setContentText("$callerAlias seni arıyor... Vak vak!")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))

        getAppLargeIcon()?.let { builder.setLargeIcon(it) }

        notificationManager.notify(CALL_NOTIFICATION_ID, builder.build())
    }

    fun cancelCallNotification() {
        notificationManager.cancel(CALL_NOTIFICATION_ID)
    }

    fun showMissedCallNotification(callerAlias: String, isVideo: Boolean) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callTypeDesc = if (isVideo) "Görüntülü" else "Sesli"

        val builder = NotificationCompat.Builder(context, CHANNEL_CALLS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("📞 Cevapsız $callTypeDesc Arama")
            .setContentText("$callerAlias seni aradı ama ulaşılamadı.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        getAppLargeIcon()?.let { builder.setLargeIcon(it) }

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
