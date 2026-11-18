package com.example.media

import org.json.JSONObject

/**
 * Sesli mesajın şifreli içerikte taşınan bilgisi (2.6.0+, mesaj türü "AUDIO").
 * Ses kaydı fotoğraflar gibi şifrelenip Dakk'ın medya deposuna (olmazsa Firestore parçalarına) yüklenir.
 */
data class VoiceMeta(
    val mediaId: String,
    val chunks: Int,
    val durationMs: Long,
    val size: Long,
    /** Dalga formu: her çubuk 0–31 ([Waveform]). */
    val waveform: List<Int>,
    /** true: medya deposunda; false: Firestore parçalarında. */
    val remote: Boolean = false
) {
    fun toJson(): String = JSONObject()
        .put("v", 1)
        .put("type", TYPE)
        .put("mediaId", mediaId)
        .put("chunks", chunks)
        .apply { if (remote) put("st", "r") }
        .put("mime", MIME)
        .put("dur", durationMs)
        .put("size", size)
        .put("wave", Waveform.encode(waveform))
        .toString()

    companion object {
        const val MESSAGE_TYPE = "AUDIO"
        const val TYPE = "voice"
        const val MIME = "audio/mp4"
        /** En uzun kayıt: 5 dakika (≈1 MB). */
        const val MAX_DURATION_MS = 5 * 60 * 1000L
        /** Bundan kısa kayıtlar gönderilmez (yanlışlıkla dokunma). */
        const val MIN_DURATION_MS = 800L

        fun parse(json: String?): VoiceMeta? = try {
            if (json == null) null else {
                val o = JSONObject(json)
                val id = o.optString("mediaId")
                if (o.optString("type") != TYPE || !id.matches(Regex("[A-Za-z0-9-]{8,64}"))) null
                else VoiceMeta(
                    mediaId = id,
                    chunks = o.optInt("chunks", 1).coerceIn(1, MediaManager.MAX_CHUNKS),
                    durationMs = o.optLong("dur").coerceIn(0L, MAX_DURATION_MS + 5_000L),
                    size = o.optLong("size"),
                    waveform = Waveform.decode(o.optString("wave")),
                    remote = o.optString("st") == "r"
                )
            }
        } catch (_: Exception) {
            null
        }

        /** "0:07", "1:23" */
        fun formatDuration(ms: Long): String {
            val total = ((ms.coerceAtLeast(0) + 500) / 1000)
            return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
        }

        /** Sohbet listesi ve bildirim önizlemesi. */
        fun preview(durationMs: Long): String = "🎤 Sesli mesaj (${formatDuration(durationMs)})"

        const val LABEL = "🎤 Sesli mesaj"
    }
}

/** Ses dalga formunun kısa metin hali: her çubuk 0–31 arası tek bir karakter (32'lik taban). */
object Waveform {
    const val BARS = 40
    const val MAX = 31
    private const val DIGITS = "0123456789abcdefghijklmnopqrstuv"

    fun encode(bars: List<Int>): String = buildString(bars.size) {
        bars.take(BARS * 2).forEach { append(DIGITS[it.coerceIn(0, MAX)]) }
    }

    fun decode(text: String?): List<Int> =
        text.orEmpty().take(BARS * 2).mapNotNull { c -> DIGITS.indexOf(c.lowercaseChar()).takeIf { it >= 0 } }

    /**
     * Kayıt sırasında toplanan genlik örneklerini (0–32767) [bars] çubuğa indirir. Konuşma sesi
     * genelde düşük genlikte olduğu için karekök ölçeği kullanılır; sessiz yerler bile ince bir çubuk olur.
     */
    fun fromAmplitudes(samples: List<Int>, bars: Int = BARS): List<Int> {
        if (samples.isEmpty()) return List(bars) { 2 }
        val peak = samples.maxOrNull()?.coerceAtLeast(1) ?: 1
        val reference = maxOf(peak, 6_000).toDouble()
        return List(bars) { i ->
            val from = i * samples.size / bars
            val to = maxOf(from + 1, (i + 1) * samples.size / bars).coerceAtMost(samples.size)
            val value = if (from >= samples.size) 0 else samples.subList(from, to).max()
            (Math.sqrt(value.coerceAtLeast(0) / reference) * MAX).toInt().coerceIn(2, MAX)
        }
    }
}
