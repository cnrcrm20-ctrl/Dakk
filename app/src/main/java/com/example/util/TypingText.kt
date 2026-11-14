package com.example.util

/** "yazıyor…" metinleri. */
object TypingText {
    /** Sohbet başlığı ve listesi için: birebirde "yazıyor…", grupta "ronaldoo7 yazıyor…" / "ronaldoo7 ve 2 kişi yazıyor…". */
    fun label(names: List<String>, isGroup: Boolean): String? {
        if (names.isEmpty()) return null
        if (!isGroup) return "yazıyor…"
        val first = names.first()
        return if (names.size == 1) "$first yazıyor…" else "$first ve ${names.size - 1} kişi yazıyor…"
    }
}
