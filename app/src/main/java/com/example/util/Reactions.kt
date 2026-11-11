package com.example.util

/**
 * Mesaj tepkileri. Yalnızca bu dört emoji kullanılabilir; her kişi bir mesaja tek tepki bırakır.
 * Aynı emojiye yeniden dokunmak tepkiyi kaldırır.
 */
object Reactions {
    const val LAUGH = "😂"
    const val HEART = "❤️"
    const val STAR = "⭐"
    const val OK = "👌"

    val ALL: List<String> = listOf(LAUGH, HEART, STAR, OK)

    fun isAllowed(emoji: String?): Boolean = emoji != null && emoji in ALL

    /**
     * Kullanıcının yeni tepkisini hesaplar: aynı emoji seçildiyse kaldırılır (null),
     * değilse yeni emoji döner.
     */
    fun toggle(current: String?, chosen: String): String? {
        require(isAllowed(chosen)) { "Desteklenmeyen tepki: $chosen" }
        return if (current == chosen) null else chosen
    }

    /** Baloncuğun altında gösterilecek özet: emoji, kaç kişi, benim tepkim mi. */
    data class Chip(val emoji: String, val count: Int, val mine: Boolean)

    fun summarize(reactions: Map<String, String>, myUid: String?): List<Chip> =
        ALL.mapNotNull { emoji ->
            val owners = reactions.filterValues { it == emoji }.keys
            if (owners.isEmpty()) null else Chip(emoji, owners.size, myUid != null && myUid in owners)
        }
}

/** Yanıt alıntısı için kısa, tek satırlık metin üretir. */
object ReplySnippets {
    const val MAX_CHARS = 120
    const val IMAGE = "📷 Fotoğraf"

    fun of(text: String?, isImage: Boolean): String {
        if (isImage) return IMAGE
        val single = text.orEmpty().replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        if (single.isEmpty()) return "Mesaj"
        return if (single.length > MAX_CHARS) single.take(MAX_CHARS).trimEnd() + "…" else single
    }
}

/** Sohbet içi "ekran görüntüsü aldı" satırının metni. */
object ScreenshotNotice {
    const val MESSAGE_TYPE = "SCREENSHOT"

    /** Eski sürümlerde düz mesaj gibi görünecek şifreli içerik. */
    const val LEGACY_TEXT = "📸 Ekran görüntüsü aldı"

    /** Aynı sohbette art arda bildirimi önlemek için en kısa aralık. */
    const val MIN_INTERVAL_MS = 10_000L

    fun label(fromMe: Boolean, senderName: String): String =
        if (fromMe) "Ekran görüntüsü aldın" else "${senderName.ifBlank { "Karşı taraf" }} ekran görüntüsü aldı"
}
