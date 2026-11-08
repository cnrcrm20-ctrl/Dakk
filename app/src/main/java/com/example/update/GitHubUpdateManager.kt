package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.util.VersionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    val version: String,
    val title: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long
)

sealed class UpdateCheckResult {
    data class Available(val release: ReleaseInfo) : UpdateCheckResult()
    object UpToDate : UpdateCheckResult()
    data class Failed(val message: String) : UpdateCheckResult()
}

/**
 * GitHub Releases üzerinden uygulama içi güncelleme.
 * Tüm sürümler aynı sabit anahtarla imzalandığı için APK mevcut kurulumun üzerine sorunsuz yüklenir.
 */
class GitHubUpdateManager(context: Context) {

    private val appContext = context.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val currentVersion: String = BuildConfig.VERSION_NAME

    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Dakk-Android/${BuildConfig.VERSION_NAME}")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 404) return@withContext UpdateCheckResult.UpToDate
                if (!response.isSuccessful) {
                    return@withContext UpdateCheckResult.Failed("Sunucu yanıtı: HTTP ${response.code}")
                }
                val json = JSONObject(response.body?.string().orEmpty())
                val tag = json.optString("tag_name")
                val assets = json.optJSONArray("assets")
                var apkUrl: String? = null
                var apkSize = 0L
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url")
                            apkSize = asset.optLong("size")
                            if (name.startsWith("Dakk", ignoreCase = true)) break
                        }
                    }
                }
                if (apkUrl.isNullOrBlank() || !VersionUtils.isNewer(tag, currentVersion)) {
                    UpdateCheckResult.UpToDate
                } else {
                    UpdateCheckResult.Available(
                        ReleaseInfo(
                            version = tag.removePrefix("v"),
                            title = json.optString("name").ifBlank { "Dakk ${tag}" },
                            notes = json.optString("body"),
                            apkUrl = apkUrl,
                            apkSize = apkSize
                        )
                    )
                }
            }
        } catch (e: Exception) {
            UpdateCheckResult.Failed("Güncelleme denetlenemedi. İnternet bağlantınızı kontrol edin.")
        }
    }

    /** APK'yı indirir; ilerleme 0..100 olarak bildirilir. */
    suspend fun download(release: ReleaseInfo, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(appContext.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "Dakk-${release.version}.apk")
        val request = Request.Builder()
            .url(release.apkUrl)
            .header("User-Agent", "Dakk-Android/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("İndirme başarısız (HTTP ${response.code})")
            val body = response.body ?: error("Boş yanıt")
            val total = body.contentLength().takeIf { it > 0 } ?: release.apkSize
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            val percent = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                withContext(Dispatchers.Main) { onProgress(percent) }
                            }
                        }
                    }
                }
            }
        }
        target
    }

    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || appContext.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${appContext.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
        }
    }

    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }
}
