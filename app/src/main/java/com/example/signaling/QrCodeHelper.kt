package com.example.signaling

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.*
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

object QrCodeHelper {

    /**
     * Yüksek kontrastlı ve net QR Kod Bitmap'i üretir.
     */
    fun generateQrBitmap(
        content: String,
        width: Int = 512,
        height: Int = 512,
        darkColor: Int = Color.BLACK,
        lightColor: Int = Color.WHITE
    ): Bitmap {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.MARGIN, 2)
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
        }

        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height, hints)

        val matrixWidth = bitMatrix.width
        val matrixHeight = bitMatrix.height
        val pixels = IntArray(matrixWidth * matrixHeight)

        for (y in 0 until matrixHeight) {
            val offset = y * matrixWidth
            for (x in 0 until matrixWidth) {
                pixels[offset + x] = if (bitMatrix[x, y]) darkColor else lightColor
            }
        }

        val bitmap = Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, matrixWidth, 0, 0, matrixWidth, matrixHeight)
        return bitmap
    }

    /**
     * Kamera önizlemesi veya görselden QR kod içeriğini yüksek hassasiyetle çözer.
     */
    fun decodeQrFromBitmap(bitmap: Bitmap): String? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val reader = MultiFormatReader()
            val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
                put(DecodeHintType.TRY_HARDER, java.lang.Boolean.TRUE)
                put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
                put(DecodeHintType.CHARACTER_SET, "UTF-8")
            }

            // 1. HybridBinarizer dene
            try {
                val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                val result = reader.decode(binaryBitmap, hints)
                if (!result.text.isNullOrBlank()) return result.text
            } catch (_: Exception) {}

            // 2. GlobalHistogramBinarizer dene
            try {
                val binaryBitmap = BinaryBitmap(GlobalHistogramBinarizer(source))
                val result = reader.decode(binaryBitmap, hints)
                if (!result.text.isNullOrBlank()) return result.text
            } catch (_: Exception) {}

            // 3. Ters kontrast dene (Inverted Luminance)
            try {
                val invertedSource = source.invert()
                val binaryBitmap = BinaryBitmap(HybridBinarizer(invertedSource))
                val result = reader.decode(binaryBitmap, hints)
                if (!result.text.isNullOrBlank()) return result.text
            } catch (_: Exception) {}

            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Galeriden seçilen görsel URI'sından QR kod çözer.
     */
    fun decodeQrFromUri(context: android.content.Context, uri: android.net.Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (bitmap != null) decodeQrFromBitmap(bitmap) else null
        } catch (e: Exception) {
            null
        }
    }
}
