package com.example.util

import java.util.Calendar
import java.util.TimeZone

/**
 * Yılbaşı dönemi (2.6.0+): 15 Aralık 00:00 – 2 Ocak 00:00 arası, telefonun saatine göre.
 *
 * Dönem içinde: kar yağışı, geri sayım, Noel Baba şapkalı ördek, yılbaşı kartları ve "mutlu yıllar"
 * konfetisi açılır. Dönem bitince her şey kendiliğinden kaybolur; güncelleme veya internet gerekmez.
 * Sunucudaki `feature_yilbasi` false yapılırsa tüm yılbaşı öğeleri anında kapanır.
 */
object Festive {

    /** Sunucudaki aç/kapa anahtarı: app_config/public → feature_yilbasi (yoksa açık sayılır). */
    const val FEATURE = "yilbasi"

    const val START_DAY_DECEMBER = 15
    /** Bu günün 00:00'ında dönem biter (2 Ocak 00:00). */
    const val END_DAY_JANUARY = 2

    private fun cal(now: Long, tz: TimeZone): Calendar = Calendar.getInstance(tz).apply { timeInMillis = now }

    fun isSeason(now: Long, tz: TimeZone = TimeZone.getDefault()): Boolean {
        val c = cal(now, tz)
        val month = c.get(Calendar.MONTH)
        val day = c.get(Calendar.DAY_OF_MONTH)
        return (month == Calendar.DECEMBER && day >= START_DAY_DECEMBER) ||
            (month == Calendar.JANUARY && day < END_DAY_JANUARY)
    }

    /** Karşılanan yıl: Aralık 2026 → 2027, 1 Ocak 2027 → 2027. */
    fun newYear(now: Long, tz: TimeZone = TimeZone.getDefault()): Int {
        val c = cal(now, tz)
        val year = c.get(Calendar.YEAR)
        return if (c.get(Calendar.MONTH) == Calendar.DECEMBER) year + 1 else year
    }

    /** [newYear] yılının 1 Ocak 00:00 anı. */
    fun newYearAt(now: Long, tz: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(tz).apply {
            clear()
            set(newYear(now, tz), Calendar.JANUARY, 1, 0, 0, 0)
        }.timeInMillis

    /** Yeni yıla girildi mi (1 Ocak, dönemin son günü)? */
    fun isNewYearsDay(now: Long, tz: TimeZone = TimeZone.getDefault()): Boolean {
        val c = cal(now, tz)
        return c.get(Calendar.MONTH) == Calendar.JANUARY && c.get(Calendar.DAY_OF_MONTH) == 1
    }

    data class Countdown(val days: Long, val hours: Long, val minutes: Long, val seconds: Long) {
        val totalSeconds: Long get() = ((days * 24 + hours) * 60 + minutes) * 60 + seconds
    }

    /** Yeni yıla kalan süre; yeni yıla girildiyse null. */
    fun countdown(now: Long, tz: TimeZone = TimeZone.getDefault()): Countdown? {
        val left = newYearAt(now, tz) - now
        if (left <= 0) return null
        // Kalan süre yukarı yuvarlanır: 00:00'a 0,4 sn kala "1 saniye" yazar, "0" yazmaz.
        val total = (left + 999) / 1000
        return Countdown(total / 86_400, (total % 86_400) / 3_600, (total % 3_600) / 60, total % 60)
    }

    /** "23 gün 4 saat", "5 saat 12 dakika", "3 dakika 20 saniye", "9 saniye". */
    fun countdownLabel(c: Countdown): String = when {
        c.days > 0 -> if (c.hours > 0) "${c.days} gün ${c.hours} saat" else "${c.days} gün"
        c.hours > 0 -> if (c.minutes > 0) "${c.hours} saat ${c.minutes} dakika" else "${c.hours} saat"
        c.minutes > 0 -> "${c.minutes} dakika ${c.seconds} saniye"
        else -> "${c.seconds} saniye"
    }

    /** Son bir saatte geri sayım her saniye, öncesinde dakikada bir yenilenir. */
    fun tickMillis(now: Long, tz: TimeZone = TimeZone.getDefault()): Long {
        val left = newYearAt(now, tz) - now
        return if (left in 1..3_600_000) 1_000L - (now % 1_000L).coerceIn(0, 999) else 30_000L
    }

    // ------------------------------------------------------------------ "Mutlu yıllar" konfetisi

    private val GREETINGS = listOf(
        "mutlu yillar",
        "mutlu yeni yillar",
        "mutlu seneler",
        "iyi yillar",
        "iyi seneler",
        "yeni yilin kutlu",
        "yeni yiliniz kutlu",
        "yeni yilin mutlu",
        "yeni yiliniz mutlu",
        "yeni yila mutlu",
        "nice yillara",
        "nice mutlu yillara",
        "happy new year"
    ).map { normalize(it) }

    /**
     * Metin bir yılbaşı kutlaması mı? Büyük/küçük harf, Türkçe karakter, noktalama ve uzatılan harfler
     * ("mutluuu yıllaaar") yok sayılır.
     */
    fun isGreeting(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val t = " " + normalize(text) + " "
        return GREETINGS.any { t.contains(" $it") }
    }

    /** Türkçe katlama, harf dışını tek boşluğa indirme ve art arda aynı harfleri teke indirme. */
    internal fun normalize(text: String): String {
        val folded = SearchText.fold(text)
        val out = StringBuilder(folded.length)
        var lastSpace = true
        for (ch in folded) {
            if (ch.isLetter()) {
                if (out.isEmpty() || out.last() != ch) out.append(ch)
                lastSpace = false
            } else if (!lastSpace) {
                out.append(' ')
                lastSpace = true
            }
        }
        return out.toString().trim()
    }
}
