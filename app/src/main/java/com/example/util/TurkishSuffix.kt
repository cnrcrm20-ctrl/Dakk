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

    /** Sayıların yönelme hâli, okunuşuna göre: "2027'ye", "2028'e", "2029'a", "2030'a", "100'e". */
    fun dativeNumber(number: Long): String {
        val n = kotlin.math.abs(number)
        val lastWord = when {
            n == 0L -> "sıfır"
            n % 10 != 0L -> listOf("", "bir", "iki", "üç", "dört", "beş", "altı", "yedi", "sekiz", "dokuz")[(n % 10).toInt()]
            n % 100 != 0L -> listOf("", "on", "yirmi", "otuz", "kırk", "elli", "altmış", "yetmiş", "seksen", "doksan")[((n / 10) % 10).toInt()]
            n % 1000 != 0L -> "yüz"
            n % 1_000_000 != 0L -> "bin"
            else -> "milyon"
        }
        val v = lastVowel(lastWord) ?: 'e'
        val a = if (v in BACK) "a" else "e"
        return if (endsWithVowel(lastWord)) "$number'y$a" else "$number'$a"
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
