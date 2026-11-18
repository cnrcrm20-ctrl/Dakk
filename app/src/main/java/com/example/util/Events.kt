package com.example.util

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

/** Etkinlik kartı: uygulamanın içindeki yılbaşı kartlarından biri ya da sunucudaki bir görsel. */
sealed class EventCard {
    data class Builtin(val index: Int) : EventCard()
    data class Remote(val url: String) : EventCard()
}

/**
 * Bir etkinlik (yılbaşı, bayram, özel gün…). Süsler [start] ile [end] arasında görünür.
 * Uygulamadaki yerleşik yılbaşı dışında hepsi sunucudan (app_config/public → event_json) gelir;
 * güncelleme gerekmeden eklenir, değiştirilir, kaldırılır.
 */
data class DakkEvent(
    val id: String,
    val start: Long,
    /** Bu andan itibaren süsler kalkar (dahil değil). */
    val end: Long,
    /** Geri sayımın hedefi; null ise geri sayım kartı yok. */
    val countdownTo: Long? = null,
    /** Geri sayım kartındaki üst yazı: "2027'ye kalan", "Bayrama kalan". */
    val countdownTitle: String = "",
    /** Geri sayım bitince kartta: "Mutlu Yıllar! 2027". */
    val doneTitle: String = "",
    val doneText: String = "",
    val emoji: String = "🎉",
    val doneEmoji: String = "🎉",
    /** Geri sayım bitince bir kez gösterilen tam ekran kutlama. */
    val celebrate: Boolean = false,
    val celebrateTitle: String = "",
    /** Kutlamadaki büyük yazı (ör. yıl). */
    val celebrateBig: String = "",
    val celebrateText: String = "",
    val snow: Boolean = false,
    val confetti: Boolean = true,
    /** Konfeti patlatan sözler ([Festive.normalize] edilmiş). */
    val greetings: List<String> = emptyList(),
    val cards: List<EventCard> = emptyList(),
    /** Geri sayım kartının degrade renkleri (ARGB). */
    val colors: List<Long> = emptyList(),
    /** Ördek Noel Baba şapkası taksın mı (açılış animasyonu ve logo). */
    val hat: Boolean = false
) {
    fun isActive(now: Long): Boolean = now in start until end

    /** Hedefe kalan süre; hedef geçtiyse veya yoksa null. */
    fun countdown(now: Long): Festive.Countdown? {
        val target = countdownTo ?: return null
        val left = target - now
        if (left <= 0) return null
        val total = (left + 999) / 1000
        return Festive.Countdown(total / 86_400, (total % 86_400) / 3_600, (total % 3_600) / 60, total % 60)
    }

    /** Geri sayım bitti ve kutlama zamanı mı? */
    fun celebrating(now: Long): Boolean = celebrate && countdownTo != null && now >= countdownTo && now < end

    fun isGreeting(text: String?): Boolean = confetti && Festive.matchesAny(text, greetings)

    /** Son bir saatte saniyede bir, öncesinde 30 sn'de bir. */
    fun tickMillis(now: Long): Long {
        val target = countdownTo
        val left = if (target == null) -1 else target - now
        val untilEnd = end - now
        return when {
            left in 1..3_600_000 -> 1_000L - (now % 1_000L).coerceIn(0, 999)
            else -> minOf(30_000L, untilEnd.coerceAtLeast(1_000L))
        }
    }
}

object Events {

    const val MAX_EVENTS = 30
    const val MAX_CARDS = 8
    const val MAX_GREETINGS = 40
    const val MAX_DAYS = 62L
    private val ID = Regex("[a-z0-9_-]{1,40}")
    private const val DAY_MS = 86_400_000L

    /** Sunucudaki etkinlik listesi. [newYear] false ise yerleşik yılbaşı da kapanır. */
    data class Spec(val newYear: Boolean = true, val events: List<DakkEvent> = emptyList())

    /** Şu an gösterilecek etkinlik: sunucudakiler önceliklidir, yoksa dönemse yerleşik yılbaşı. */
    fun active(spec: Spec?, now: Long, newYearAllowed: Boolean, tz: TimeZone = TimeZone.getDefault()): DakkEvent? {
        spec?.events?.firstOrNull { it.isActive(now) }?.let { return it }
        if (!newYearAllowed || spec?.newYear == false || !Festive.isSeason(now, tz)) return null
        return newYear(now, tz)
    }

    /** Uygulamaya gömülü yılbaşı (15 Aralık – 1 Ocak). */
    fun newYear(now: Long, tz: TimeZone = TimeZone.getDefault()): DakkEvent {
        val year = Festive.newYear(now, tz)
        val at = Festive.newYearAt(now, tz)
        val start = Calendar.getInstance(tz).apply { clear(); set(year - 1, Calendar.DECEMBER, Festive.START_DAY_DECEMBER, 0, 0, 0) }.timeInMillis
        val end = Calendar.getInstance(tz).apply { clear(); set(year, Calendar.JANUARY, Festive.END_DAY_JANUARY, 0, 0, 0) }.timeInMillis
        return DakkEvent(
            id = "yilbasi-$year",
            start = start,
            end = end,
            countdownTo = at,
            countdownTitle = "${TurkishSuffix.dativeNumber(year.toLong())} kalan",
            doneTitle = "Mutlu Yıllar! $year",
            doneText = "Yeni yılın kutlu olsun 🥳",
            emoji = "🎄",
            doneEmoji = "🎉",
            celebrate = true,
            celebrateTitle = "Mutlu Yıllar!",
            celebrateBig = "$year",
            celebrateText = "Dakk ailesi olarak sağlık, huzur ve bol kahkahalı bir yıl dileriz 🦆",
            snow = true,
            confetti = true,
            greetings = Festive.GREETINGS,
            cards = List(4) { EventCard.Builtin(it) },
            colors = listOf(0xFF1B2A4A, 0xFF3A1D3F),
            hat = true
        )
    }

    /**
     * event_json'u çözer. Boşsa veya bozuksa null (yerleşik yılbaşı yine çalışır). Hatalı tek bir etkinlik
     * diğerlerini bozmaz; yalnızca o atlanır.
     *
     * Biçim:
     * {"yilbasi": true, "events": [{"id": "ramazan-2027", "start": "2027-03-08", "end": "2027-03-12",
     *   "countdown_to": "2027-03-10T00:00", "countdown_title": "Bayrama kalan", "done_title": "İyi Bayramlar!",
     *   "done_text": "…", "emoji": "🌙", "celebrate": true, "celebrate_title": "İyi Bayramlar!",
     *   "celebrate_text": "…", "snow": false, "confetti": true, "greetings": ["iyi bayramlar"],
     *   "cards": ["https://…/kart1.webp"], "colors": ["#1B2A4A", "#3A1D3F"]}]}
     *
     * Tarihler telefonun saat dilimine göre okunur. Yalnızca tarih verilen "end" o günün sonuna kadar sürer.
     */
    fun parse(json: String?, tz: TimeZone = TimeZone.getDefault()): Spec? {
        if (json.isNullOrBlank()) return null
        val root = try {
            JSONObject(json)
        } catch (_: Exception) {
            return null
        }
        val list = root.optJSONArray("events") ?: JSONArray()
        val events = (0 until minOf(list.length(), MAX_EVENTS)).mapNotNull { i ->
            list.optJSONObject(i)?.let { runCatching { event(it, tz) }.getOrNull() }
        }.sortedBy { it.start }
        return Spec(newYear = root.optBoolean("yilbasi", true), events = events)
    }

    private fun event(o: JSONObject, tz: TimeZone): DakkEvent? {
        val id = o.optString("id").trim().lowercase()
        if (!ID.matches(id)) return null
        val start = time(o.opt("start"), tz, endOfDay = false) ?: return null
        val end = time(o.opt("end"), tz, endOfDay = true) ?: return null
        if (end <= start || end - start > MAX_DAYS * DAY_MS) return null
        val countdownTo = time(o.opt("countdown_to"), tz, endOfDay = false)
        return DakkEvent(
            id = id,
            start = start,
            end = end,
            countdownTo = countdownTo,
            countdownTitle = text(o, "countdown_title", 60),
            doneTitle = text(o, "done_title", 60),
            doneText = text(o, "done_text", 120),
            emoji = text(o, "emoji", 16).ifEmpty { "🎉" },
            doneEmoji = text(o, "done_emoji", 16).ifEmpty { text(o, "emoji", 16).ifEmpty { "🎉" } },
            celebrate = o.optBoolean("celebrate", countdownTo != null) && countdownTo != null,
            celebrateTitle = text(o, "celebrate_title", 60).ifEmpty { text(o, "done_title", 60) },
            celebrateBig = text(o, "celebrate_big", 20),
            celebrateText = text(o, "celebrate_text", 240),
            snow = o.optBoolean("snow", false),
            confetti = o.optBoolean("confetti", true),
            greetings = strings(o.optJSONArray("greetings"), MAX_GREETINGS, 60).map { Festive.normalize(it) }.filter { it.isNotEmpty() },
            cards = strings(o.optJSONArray("cards"), MAX_CARDS, 500).mapNotNull { card(it) },
            colors = strings(o.optJSONArray("colors"), 2, 9).mapNotNull { color(it) }.takeIf { it.size == 2 }.orEmpty(),
            hat = o.optBoolean("hat", false)
        )
    }

    private fun text(o: JSONObject, key: String, max: Int): String =
        o.optString(key, "").trim().take(max)

    private fun strings(a: JSONArray?, max: Int, maxLength: Int): List<String> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { a.optString(it, "").trim().takeIf { s -> s.isNotEmpty() && s.length <= maxLength } }
            .distinct().take(max)
    }

    /** Yalnızca https adresleri veya "yerlesik:1…4" (uygulamadaki yılbaşı kartları). */
    internal fun card(value: String): EventCard? = when {
        value.startsWith("yerlesik:") -> value.removePrefix("yerlesik:").toIntOrNull()?.takeIf { it in 1..4 }?.let { EventCard.Builtin(it - 1) }
        value.startsWith("https://") && !value.contains(' ') -> EventCard.Remote(value)
        else -> null
    }

    /** "#RRGGBB" veya "#AARRGGBB" → ARGB. */
    internal fun color(value: String): Long? {
        val hex = value.removePrefix("#")
        val v = hex.toLongOrNull(16) ?: return null
        return when (hex.length) {
            6 -> 0xFF000000L or v
            8 -> v
            else -> null
        }
    }

    /**
     * Zaman: epoch milisaniye (sayı) ya da yerel "2027-03-08" / "2027-03-08T21:30" / "2027-03-08 21:30".
     * [endOfDay]: yalnızca tarih verildiyse o günün sonu (ertesi gün 00:00).
     */
    fun time(value: Any?, tz: TimeZone = TimeZone.getDefault(), endOfDay: Boolean = false): Long? {
        when (value) {
            null, JSONObject.NULL -> return null
            is Number -> return value.toLong().takeIf { it > 0 }
        }
        val s = value.toString().trim()
        if (s.isEmpty()) return null
        s.toLongOrNull()?.let { return it.takeIf { v -> v > 0 } }
        val m = Regex("""(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2}))?""").matchEntire(s) ?: return null
        val (y, mo, d) = Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        if (mo !in 1..12 || d !in 1..31) return null
        val hasTime = m.groupValues[4].isNotEmpty()
        val h = if (hasTime) m.groupValues[4].toInt() else 0
        val min = if (hasTime) m.groupValues[5].toInt() else 0
        if (h !in 0..23 || min !in 0..59) return null
        val c = Calendar.getInstance(tz).apply {
            isLenient = false
            clear()
            set(y, mo - 1, d, h, min, 0)
        }
        val at = runCatching { c.timeInMillis }.getOrNull() ?: return null
        return if (endOfDay && !hasTime) at + DAY_MS else at
    }
}
