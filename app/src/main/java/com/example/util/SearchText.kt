package com.example.util

/**
 * Sohbet ve kişi araması için metin karşılaştırma.
 * Büyük/küçük harf ve Türkçe karakter farkını yok sayar ("cagri" → "Çağrı" bulunur).
 * Her karakter tek bir karaktere eşlendiği için eşleşmenin konumu orijinal metinde aynen kullanılabilir.
 */
object SearchText {

    fun fold(c: Char): Char = when (c) {
        'İ', 'I', 'ı', 'i', 'Î', 'î' -> 'i'
        'Ç', 'ç' -> 'c'
        'Ğ', 'ğ' -> 'g'
        'Ö', 'ö' -> 'o'
        'Ş', 'ş' -> 's'
        'Ü', 'ü', 'Û', 'û' -> 'u'
        'Â', 'â' -> 'a'
        else -> c.lowercaseChar()
    }

    fun fold(text: String): String = buildString(text.length) { text.forEach { append(fold(it)) } }

    /** Aranan metnin [text] içindeki ilk konumu; yoksa null. Boş arama hiçbir şeyi vurgulamaz. */
    fun matchRange(text: String, query: String): IntRange? {
        val q = fold(query.trim())
        if (q.isEmpty()) return null
        val index = fold(text).indexOf(q)
        return if (index < 0) null else index until index + q.length
    }

    /** Boş arama her şeyle eşleşir. */
    fun matches(text: String, query: String): Boolean =
        query.isBlank() || matchRange(text, query) != null

    fun matchesAny(query: String, vararg texts: String): Boolean =
        query.isBlank() || texts.any { matchRange(it, query) != null }
}
