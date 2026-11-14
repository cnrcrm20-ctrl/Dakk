package com.example.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.MainActivity
import com.example.R

/**
 * Dakk Güncelleyici'nin kurulum sonuçları.
 *  - Sistem onay isterse (eski Android veya cihaz politikası) tek dokunuşluk onay ekranı açılır.
 *  - Kurulum başarısız olursa klasik kurulum ekranına dönülür.
 *  - Güncelleme bitince (MY_PACKAGE_REPLACED) "Dakk güncellendi" bildirimi gösterilir; dokununca uygulama açılır.
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_STATUS -> onStatus(context, intent)
            Intent.ACTION_MY_PACKAGE_REPLACED -> showUpdated(context)
        }
    }

    private fun onStatus(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm != null) {
                    runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { Log.w(TAG, "Onay ekranı açılamadı", it) }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Log.i(TAG, "Güncelleme kuruldu")
            PackageInstaller.STATUS_FAILURE_ABORTED -> Unit
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.w(TAG, "Güncelleme kurulamadı ($status): $message")
                Toast.makeText(context, "Güncelleme kurulamadı. Kurulum ekranı açılıyor…", Toast.LENGTH_LONG).show()
                java.io.File(context.cacheDir, "updates").listFiles()
                    ?.filter { it.name.endsWith(".apk") }
                    ?.maxByOrNull { it.lastModified() }
                    ?.let { apk -> runCatching { GitHubUpdateManager(context).install(apk) } }
            }
        }
    }

    private fun showUpdated(context: Context) {
        java.io.File(context.cacheDir, "updates").listFiles()?.forEach { it.delete() }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_UPDATES, "Dakk Güncelleyici", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_dakk)
            .setContentTitle("Dakk güncellendi ✓")
            .setContentText("Sürüm ${BuildConfig.VERSION_NAME} hazır. Açmak için dokun.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    companion object {
        const val ACTION_STATUS = "com.example.dakk.action.INSTALL_STATUS"
        private const val CHANNEL_UPDATES = "dakk_updates"
        private const val NOTIFICATION_ID = 4242
        private const val TAG = "DakkUpdater"
    }
}
