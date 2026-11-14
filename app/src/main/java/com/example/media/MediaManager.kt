package com.example.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import com.example.crypto.CryptoEngine
import com.example.data.FirestoreRepository
import com.example.push.PushClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** Gönderilmeye hazır, sıkıştırılmış fotoğraf. */
/**
 * Gönderime hazır fotoğraf. [jpeg] telefonda saklanan ve galeriye kaydedilen haldir. [transit] doluysa
 * yalnızca yolda kullanılan daha küçük WebP kopyasıdır; alıcı onu açıp JPEG olarak saklar (2.5.0+).
 */
class PreparedImage(
    val jpeg: ByteArray,
    val width: Int,
    val height: Int,
    val thumbBase64: String?,
    val transit: ByteArray? = null
)

/** Şifreli mesaj içeriğinde taşınan fotoğraf bilgisi. */
data class ImageMeta(
    val mediaId: String,
    val chunks: Int,
    val width: Int,
    val height: Int,
    val size: Long,
    val thumbBase64: String?,
    /** true: şifreli fotoğraf ayrı fotoğraf deposunda; false: Firestore parçalarında (eski yöntem). */
    val remote: Boolean = false,
    /** true: yolda WebP olarak taşındı; alıcı JPEG'e çevirip saklar. */
    val transitWebp: Boolean = false
) {
    fun toJson(): String = JSONObject()
        .put("v", 1)
        .put("type", "image")
        .put("mediaId", mediaId)
        .put("chunks", chunks)
        .apply { if (remote) put("st", "r") }
        .apply { if (transitWebp) put("tf", "webp") }
        .put("mime", "image/jpeg")
        .put("w", width)
        .put("h", height)
        .put("size", size)
        .apply { if (thumbBase64 != null) put("thumb", thumbBase64) }
        .toString()

    companion object {
        fun parse(json: String?): ImageMeta? = try {
            if (json == null) null else {
                val o = JSONObject(json)
                val id = o.optString("mediaId")
                if (o.optString("type") != "image" || !id.matches(Regex("[A-Za-z0-9-]{8,64}"))) null
                else ImageMeta(
                    mediaId = id,
                    chunks = o.optInt("chunks", 1).coerceIn(1, MediaManager.MAX_CHUNKS),
                    width = o.optInt("w"),
                    height = o.optInt("h"),
                    size = o.optLong("size"),
                    thumbBase64 = o.optString("thumb").ifBlank { null },
                    remote = o.optString("st") == "r",
                    transitWebp = o.optString("tf") == "webp"
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}

sealed class DownloadResult {
    data class Ok(val file: File) : DownloadResult()
    object Expired : DownloadResult()
    object Failed : DownloadResult()
}

/**
 * Fotoğraf gönderme/alma.
 *
 * Fotoğraf telefonda küçültülüp (1280 px, tasarrufta 960 px JPEG) şifrelenir ve Dakk'ın fotoğraf deposuna
 * yüklenir; sunucu yalnızca kısa ömürlü imzalı adres verir ve içeriği göremez. Birebir sohbette alıcı
 * indirince fotoğraf depodan silinir; gruplarda ve hiç indirilmeyenlerde sunucu 7 gün sonra siler.
 * Fotoğraf deposu kullanılamıyorsa eski yönteme (Firestore parçaları) dönülür.
 */
class MediaManager(
    context: Context,
    private val repo: FirestoreRepository,
    private val crypto: CryptoEngine,
    private val push: PushClient
) {

    /** Fotoğraf deposu için kullanılacak Dakk sunucusu; boşsa Firestore parçaları kullanılır. */
    @Volatile
    var mediaServer: String = ""

    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "media").apply { mkdirs() }
    private val prefs = appContext.getSharedPreferences("dakk_media", Context.MODE_PRIVATE)

    fun newMediaId(): String = UUID.randomUUID().toString()

    fun localFile(mediaId: String): File = File(dir, "$mediaId.jpg")

    fun hasLocal(mediaId: String): Boolean = localFile(mediaId).let { it.isFile && it.length() > 0 }

    /** [transitWebp]: alıcıların hepsi 2.5.0+ ise yol için ayrıca daha küçük bir WebP kopyası üretilir. */
    suspend fun prepare(uri: Uri, saver: Boolean = false, transitWebp: Boolean = false): PreparedImage = withContext(Dispatchers.IO) {
        val maxDimension = if (saver) SAVER_DIMENSION else MAX_DIMENSION
        val targetBytes = if (saver) SAVER_TARGET_BYTES else TARGET_BYTES
        val resolver = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Fotoğraf okunamadı" }

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Fotoğraf okunamadı")

        val rotation = try {
            resolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (_: Exception) {
            0f
        }

        val scaled = scaleDown(decoded, maxDimension)
        val oriented = if (rotation != 0f) {
            Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, Matrix().apply { postRotate(rotation) }, true)
        } else scaled

        // Hedef boyuta inene kadar kalite adım adım düşürülür (çoğu fotoğraf ilk denemede 60–150 KB olur).
        var quality = if (saver) SAVER_JPEG_QUALITY else JPEG_QUALITY
        val minQuality = if (saver) 45 else 50
        var jpeg = compress(oriented, quality)
        while ((jpeg.size > targetBytes || jpeg.size > MAX_JPEG_BYTES) && quality > minQuality) {
            quality -= 6
            jpeg = compress(oriented, quality)
        }

        val thumbBitmap = scaleDown(oriented, THUMB_DIMENSION)
        val thumb = Base64.encodeToString(compress(thumbBitmap, 45), Base64.NO_WRAP)
            .takeIf { it.length <= MAX_THUMB_CHARS }

        val transit = if (transitWebp) runCatching { webp(oriented, quality) }.getOrNull()?.takeIf { it.size < jpeg.size * 9 / 10 }
        else null
        val result = PreparedImage(jpeg, oriented.width, oriented.height, thumb, transit)
        if (thumbBitmap !== oriented) thumbBitmap.recycle()
        if (oriented !== scaled) oriented.recycle()
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        result
    }

    private fun scaleDown(source: Bitmap, maxDim: Int): Bitmap {
        val largest = maxOf(source.width, source.height)
        if (largest <= maxDim) return source
        val ratio = maxDim.toFloat() / largest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    /** JPEG kalitesine denk düşen WebP: aynı görüntü için genellikle %25–35 daha küçük. */
    @Suppress("DEPRECATION")
    private fun webp(bitmap: Bitmap, jpegQuality: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            bitmap.compress(format, (jpegQuality - 6).coerceIn(40, 90), out)
            out.toByteArray()
        }

    /** Yolda WebP gelen fotoğrafı telefonda saklamak için JPEG'e çevirir. */
    private fun webpToJpeg(bytes: ByteArray): ByteArray? {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return try {
            compress(bitmap, RECEIVED_JPEG_QUALITY)
        } finally {
            bitmap.recycle()
        }
    }

    private fun compress(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }

    /** Fotoğrafı şifreleyip parçalar halinde yükler ve mesajda taşınacak bilgiyi döner. */
    suspend fun upload(
        chatId: String,
        myUid: String,
        peerPublicKey: String,
        mediaId: String,
        image: PreparedImage
    ): ImageMeta = upload(chatId, myUid, mediaId, image) { crypto.encryptBytes(it, peerPublicKey) }

    /** Fotoğrafı verilen şifreleyiciyle (ör. grup mesaj anahtarı) şifreleyip yükler. */
    suspend fun upload(
        chatId: String,
        myUid: String,
        mediaId: String,
        image: PreparedImage,
        encrypt: (ByteArray) -> ByteArray
    ): ImageMeta {
        val wire = image.transit ?: image.jpeg
        val encrypted = withContext(Dispatchers.Default) { encrypt(wire) }
        withContext(Dispatchers.IO) { localFile(mediaId).writeBytes(image.jpeg) }
        val server = mediaServer
        if (server.isNotBlank() && encrypted.size <= PushClient.MAX_MEDIA_BYTES) {
            val uploaded = runCatching { push.uploadMedia(server, chatId, mediaId, encrypted) }.getOrDefault(false)
            if (uploaded) {
                return ImageMeta(
                    mediaId, 1, image.width, image.height, image.jpeg.size.toLong(), image.thumbBase64,
                    remote = true, transitWebp = image.transit != null
                )
            }
            Log.w(TAG, "Fotoğraf deposuna yüklenemedi; Firestore'a yükleniyor")
        }
        val parts = (encrypted.indices step CHUNK_BYTES).map { start ->
            val end = minOf(start + CHUNK_BYTES, encrypted.size)
            Base64.encodeToString(encrypted, start, end - start, Base64.NO_WRAP)
        }
        require(parts.size <= MAX_CHUNKS) { "Fotoğraf çok büyük" }
        parts.forEachIndexed { index, part ->
            repo.uploadBlob(chatId, mediaId, index, parts.size, myUid, part)
        }
        rememberUpload(chatId, mediaId, parts.size)
        return ImageMeta(
            mediaId, parts.size, image.width, image.height, image.jpeg.size.toLong(), image.thumbBase64,
            transitWebp = image.transit != null
        )
    }

    /** Grup veya kişi fotoğrafı için küçük, kare kırpılmış JPEG üretir. */
    suspend fun prepareAvatar(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val prepared = prepare(uri)
        val full = BitmapFactory.decodeByteArray(prepared.jpeg, 0, prepared.jpeg.size) ?: error("Fotoğraf okunamadı")
        val side = minOf(full.width, full.height)
        val square = Bitmap.createBitmap(full, (full.width - side) / 2, (full.height - side) / 2, side, side)
        val small = scaleDown(square, AVATAR_DIMENSION)
        var quality = 75
        var bytes = compress(small, quality)
        while (bytes.size > MAX_AVATAR_BYTES && quality > 35) {
            quality -= 10
            bytes = compress(small, quality)
        }
        if (small !== square) small.recycle()
        if (square !== full) square.recycle()
        full.recycle()
        require(bytes.size <= MAX_AVATAR_BYTES) { "Fotoğraf çok büyük" }
        bytes
    }

    private val customPhotoDir = File(appContext.filesDir, "chat_photos").apply { mkdirs() }

    /** Birebir sohbette kişiye bu cihazda verilen özel fotoğraf (yalnızca sende görünür). */
    fun customPhotoFile(peerUid: String): File = File(customPhotoDir, "$peerUid.jpg")

    fun hasCustomPhoto(peerUid: String): Boolean = customPhotoFile(peerUid).let { it.isFile && it.length() > 0 }

    suspend fun saveCustomPhoto(peerUid: String, uri: Uri) {
        val bytes = prepareAvatar(uri)
        withContext(Dispatchers.IO) { customPhotoFile(peerUid).writeBytes(bytes) }
    }

    suspend fun deleteCustomPhoto(peerUid: String) {
        withContext(Dispatchers.IO) { customPhotoFile(peerUid).delete() }
    }

    /**
     * Fotoğrafı indirir, çözer ve cihaza kaydeder.
     * deleteAfter = true ise (alıcı) parçalar indirildikten sonra sunucudan silinir.
     */
    suspend fun download(
        chatId: String,
        meta: ImageMeta,
        senderKey: String,
        recipientKey: String,
        deleteAfter: Boolean
    ): DownloadResult = download(chatId, meta, deleteAfter) { crypto.decryptBytes(it, senderKey, recipientKey) }

    /** Fotoğrafı verilen çözücüyle (ör. grup mesaj anahtarı) indirir. */
    suspend fun download(
        chatId: String,
        meta: ImageMeta,
        deleteAfter: Boolean,
        decrypt: (ByteArray) -> ByteArray?
    ): DownloadResult {
        if (hasLocal(meta.mediaId)) return DownloadResult.Ok(localFile(meta.mediaId))
        return try {
            val encrypted = if (meta.remote) {
                val server = mediaServer.ifBlank { null } ?: return DownloadResult.Failed
                when (val fetched = push.downloadMedia(server, chatId, meta.mediaId)) {
                    is PushClient.MediaFetch.Ok -> fetched.bytes
                    PushClient.MediaFetch.Expired -> return DownloadResult.Expired
                    PushClient.MediaFetch.Failed -> return DownloadResult.Failed
                }
            } else {
                val out = ByteArrayOutputStream()
                for (i in 0 until meta.chunks) {
                    val part = repo.getBlob(chatId, meta.mediaId, i) ?: return DownloadResult.Expired
                    out.write(Base64.decode(part, Base64.NO_WRAP))
                }
                out.toByteArray()
            }
            val decrypted = withContext(Dispatchers.Default) { decrypt(encrypted) } ?: return DownloadResult.Failed
            // Kaydedilen ve paylaşılan fotoğraflar her zaman JPEG'dir; WebP yalnızca yolda kullanılır.
            val plain = if (meta.transitWebp) {
                withContext(Dispatchers.Default) { webpToJpeg(decrypted) } ?: return DownloadResult.Failed
            } else decrypted
            val file = localFile(meta.mediaId)
            withContext(Dispatchers.IO) {
                val tmp = File(dir, "${meta.mediaId}.part")
                tmp.writeBytes(plain)
                tmp.renameTo(file)
            }
            if (deleteAfter) {
                runCatching { removeRemote(chatId, meta) }
                    .onFailure { Log.w(TAG, "Fotoğraf sunucudan silinemedi", it) }
            }
            DownloadResult.Ok(file)
        } catch (e: Exception) {
            Log.w(TAG, "Fotoğraf indirilemedi", e)
            DownloadResult.Failed
        }
    }

    private fun loadUploads(): JSONArray =
        runCatching { JSONArray(prefs.getString(PREF_UPLOADS, "[]")) }.getOrDefault(JSONArray())

    @Synchronized
    private fun rememberUpload(chatId: String, mediaId: String, chunks: Int) {
        val list = loadUploads()
        list.put(JSONObject().put("chat", chatId).put("id", mediaId).put("n", chunks).put("t", System.currentTimeMillis()))
        prefs.edit().putString(PREF_UPLOADS, list.toString()).apply()
    }

    /** Gönderilip 7 gün içinde indirilmemiş fotoğrafların parçalarını sunucudan siler. */
    suspend fun cleanupExpiredUploads() {
        val now = System.currentTimeMillis()
        val list = loadUploads()
        val keep = JSONArray()
        for (i in 0 until list.length()) {
            val o = list.optJSONObject(i) ?: continue
            if (now - o.optLong("t") < UPLOAD_TTL_MS) {
                keep.put(o)
                continue
            }
            val ok = runCatching { repo.deleteBlobs(o.getString("chat"), o.getString("id"), o.optInt("n", 1)) }.isSuccess
            if (!ok) keep.put(o)
        }
        synchronized(this) { prefs.edit().putString(PREF_UPLOADS, keep.toString()).apply() }
    }

    /** Silinen bir fotoğraf mesajının sunucudaki parçalarını ve yerel kopyasını kaldırır. */
    suspend fun deleteMedia(chatId: String, meta: ImageMeta) {
        runCatching { removeRemote(chatId, meta) }
        withContext(Dispatchers.IO) { localFile(meta.mediaId).delete() }
    }

    private suspend fun removeRemote(chatId: String, meta: ImageMeta) {
        if (meta.remote) {
            mediaServer.ifBlank { null }?.let { push.deleteMedia(it, chatId, meta.mediaId) }
        } else {
            repo.deleteBlobs(chatId, meta.mediaId, meta.chunks)
        }
    }

    /** Fotoğrafı telefonun galerisine (Pictures/Dakk) kopyalar. */
    suspend fun saveToGallery(mediaId: String): Boolean = withContext(Dispatchers.IO) {
        val source = localFile(mediaId)
        if (!source.isFile) return@withContext false
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "Dakk_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Dakk")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = appContext.contentResolver
            val target = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
            resolver.openOutputStream(target)?.use { out -> source.inputStream().use { it.copyTo(out) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(target, values, null, null)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Galeriye kaydedilemedi", e)
            false
        }
    }

    companion object {
        private const val TAG = "DakkMedia"
        private const val PREF_UPLOADS = "uploads"
        const val MAX_DIMENSION = 1280
        const val SAVER_DIMENSION = 960
        const val TARGET_BYTES = 180_000
        const val SAVER_TARGET_BYTES = 110_000
        const val THUMB_DIMENSION = 40
        const val JPEG_QUALITY = 78
        const val SAVER_JPEG_QUALITY = 68
        const val MAX_JPEG_BYTES = 1_200_000
        const val RECEIVED_JPEG_QUALITY = 88
        const val MAX_THUMB_CHARS = 2_400
        const val CHUNK_BYTES = 700_000
        const val MAX_CHUNKS = 4
        const val UPLOAD_TTL_MS = 7L * 24 * 60 * 60 * 1000
        const val AVATAR_DIMENSION = 256
        const val MAX_AVATAR_BYTES = 40_000
    }
}
