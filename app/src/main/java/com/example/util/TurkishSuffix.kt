package com.example.util

/** Adlara kesme işaretiyle eklenen Türkçe ekler (ünlü uyumuna göre). */
object TurkishSuffix {

    private const val BACK = "aıou"
    private const val VOWELS = "aıoueiöü"

    private fun lastVowel(word: String): Char? =
        word.lowercase(java.util.Locale("tr")).lastOrNull { it in VOWELS }

    private fun endsWithVowel(word: String): Boolean =
        word.lowercase(java.util.Locale("tr")).lastOrNull()?.let { it in VOWELS } == true

    /** Yönelme hâli: "Ali'ye", "Ahmet'e", "Can'a", "Ayşe'ye". */
    fun dative(name: String): String {
        val n = name.trim().ifEmpty { return name }
        val v = lastVowel(n) ?: 'e'
        val a = if (v in BACK) "a" else "e"
        return if (endsWithVowel(n)) "$n'y$a" else "$n'$a"
    }

    /** İlgi hâli: "Ali'nin", "Ahmet'in", "Can'ın", "Burcu'nun". */
    fun genitive(name: String): String {
        val n = name.trim().ifEmpty { return name }
        val vowel = when (lastVowel(n) ?: 'e') {
            'a', 'ı' -> "ı"
            'o', 'u' -> "u"
            'e', 'i' -> "i"
            else -> "ü"
        }
        return if (endsWithVowel(n)) "$n'n${vowel}n" else "$n'${vowel}n"
    }
}
