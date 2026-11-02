package com.example.github

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.FileProvider
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val description: String,
    val apkDownloadUrl: String?,
    val publishedAt: String
)

class GitHubUpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "DakkUpdate"
        const val CURRENT_VERSION = "1.0.5"
        const val DEFAULT_REPO = "cnrcrm20-ctrl/Dakk"
    }

    private val client = OkHttpClient()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var downloadCall: Call? = null

    fun checkForUpdate(
        repo: String = DEFAULT_REPO,
        onResult: (hasUpdate: Boolean, releaseInfo: ReleaseInfo?, error: String?) -> Unit
    ) {
        val url = "https://api.github.com/repos/$repo/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post {
                    onResult(false, null, "Güncelleme denetlenemedi: ${e.localizedMessage}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        mainHandler.post {
                            onResult(false, null, "GitHub yanıtı: HTTP ${response.code}")
                        }
                        return
                    }

                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val tag = json.optString("tag_name", "").removePrefix("v").trim()
                    val name = json.optString("name", "Dakk Yeni Sürüm")
                    val bodyNotes = json.optString("body", "")
                    val publishedAt = json.optString("published_at", "")

                    var apkUrl: String? = null
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val downloadUrl = asset.optString("browser_download_url")
                            if (downloadUrl.endsWith(".apk", ignoreCase = true)) {
                                apkUrl = downloadUrl
                                break
                            }
                        }
                    }

                    val info = ReleaseInfo(
                        tagName = tag,
                        name = name,
                        description = bodyNotes,
                        apkDownloadUrl = apkUrl,
                        publishedAt = publishedAt
                    )

                    val isNewer = isVersionNewer(tag, CURRENT_VERSION)
                    mainHandler.post {
                        onResult(isNewer, info, null)
                    }
                } catch (e: Exception) {
                    mainHandler.post {
                        onResult(false, null, "Hata: ${e.message}")
                    }
                } finally {
                    response.close()
                }
            }
        })
    }

    private fun isVersionNewer(remote: String, local: String): Boolean {
        if (remote.isEmpty()) return false
        val rParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val lParts = local.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until minOf(rParts.size, lParts.size)) {
            if (rParts[i] > lParts[i]) return true
            if (rParts[i] < lParts[i]) return false
        }
        return rParts.size > lParts.size
    }

    fun downloadAndPromptInstall(
        downloadUrl: String,
        onProgress: (percent: Int) -> Unit,
        onComplete: (File?) -> Unit,
        onError: (String) -> Unit
    ) {
        val request = Request.Builder().url(downloadUrl).build()
        val call = client.newCall(request)
        downloadCall = call

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { onError("İndirme başarısız: ${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    mainHandler.post { onError("İndirme hatası: HTTP ${response.code}") }
                    response.close()
                    return
                }

                try {
                    val body = response.body ?: return
                    val totalLength = body.contentLength()
                    val updateDir = context.getExternalFilesDir(null) ?: context.cacheDir
                    val destFile = File(updateDir, "dakk_update.apk")
                    val inputStream = body.byteStream()
                    val outputStream = FileOutputStream(destFile)

                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalDownloaded = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead
                        if (totalLength > 0) {
                            val progress = ((totalDownloaded * 100) / totalLength).toInt()
                            mainHandler.post { onProgress(progress) }
                        }
                    }

                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()
                    response.close()

                    mainHandler.post {
                        onComplete(destFile)
                        promptInstall(destFile)
                    }
                } catch (e: Exception) {
                    mainHandler.post { onError("Kayıt hatası: ${e.message}") }
                }
            }
        })
    }

    fun promptInstall(file: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                }
            }

            val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } else {
                Uri.fromFile(file)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            val resInfoList = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(packageName, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Install prompt failed: ${e.message}")
        }
    }

    fun cancelDownload() {
        downloadCall?.cancel()
        downloadCall = null
    }
}
