package com.example.util

/** Sohbette Dakk Asistan'ı çağıran "@dakk" etiketini tanır. */
object AssistantMention {
    const val TAG = "@dakk"

    /** Mesaj "@dakk" ile başlıyor ve ardından boşluk/noktalama geliyorsa true ("@dakkus" gibi kelimeler hariç). */
    fun isMention(text: String): Boolean {
        val t = text.trimStart()
        if (!t.startsWith(TAG, ignoreCase = true)) return false
        if (t.length == TAG.length) return false
        return !t[TAG.length].isLetterOrDigit() && t.substring(TAG.length).isNotBlank()
    }

    /** "@dakk" etiketini çıkarıp asistana sorulacak kısmı döner. */
    fun question(text: String): String =
        text.trimStart().drop(TAG.length).trimStart(',', ':', ' ', '\n').trim()
}
