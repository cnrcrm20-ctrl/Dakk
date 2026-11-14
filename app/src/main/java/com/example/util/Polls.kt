package com.example.util

import org.json.JSONArray
import org.json.JSONObject
import java.util.Random

/**
 * Grup anketleri.
 *
 * Anketin sorusu ve seçenekleri mesaj içeriği olarak (uçtan uca şifreli) taşınır. Her üyenin oyu
 * mesajın `votes.<uid>` alanına, yine şifreli olarak yazılır; sunucu kimin neyi seçtiğini göremez.
 */
object Polls {

    const val MESSAGE_TYPE = "POLL"
    const val MIN_OPTIONS = 2
    const val MAX_OPTIONS = 10
    const val MAX_QUESTION = 200
    const val MAX_OPTION = 80

    /** Maç anketinin hazır seçenekleri. "Takım kur" ilk seçeneği ("Geliyorum") işaretleyenleri böler. */
    const val MATCH_QUESTION = "Maça geliyor musun? ⚽"
    val MATCH_OPTIONS = listOf("Geliyorum", "Belki", "Gelmiyorum")

    data class Spec(val question: String, val options: List<String>, val multi: Boolean, val isMatch: Boolean) {
        fun toJson(): String = JSONObject()
            .put("v", 1)
            .put("type", "poll")
            .put("q", question)
            .put("o", JSONArray(options))
            .put("m", multi)
            .put("k", if (isMatch) "match" else "")
            .toString()
    }

    /** Kullanıcının girdiği anketi temizler; geçersizse null. */
    fun build(question: String, options: List<String>, multi: Boolean, isMatch: Boolean): Spec? {
        val q = question.trim().take(MAX_QUESTION)
        val opts = cleanOptions(options)
        if (q.isEmpty() || opts.size < MIN_OPTIONS) return null
        return Spec(q, opts, multi && !isMatch, isMatch)
    }

    fun cleanOptions(options: List<String>): List<String> {
        val seen = HashSet<String>()
        return options.map { it.trim().take(MAX_OPTION) }
            .filter { it.isNotEmpty() && seen.add(it.lowercase()) }
            .take(MAX_OPTIONS)
    }

    fun parse(json: String?): Spec? = try {
        if (json == null) null else {
            val o = JSONObject(json)
            if (o.optString("type") != "poll") null else {
                val arr = o.optJSONArray("o") ?: JSONArray()
                val options = (0 until arr.length()).map { arr.optString(it) }
                build(o.optString("q"), options, o.optBoolean("m"), o.optString("k") == "match")
            }
        }
    } catch (_: Exception) {
        null
    }

    /** Oy: seçilen seçenek sıraları, virgülle ayrılmış (ör. "0,2"). Boş küme oyu geri çekmek demektir. */
    fun encodeVote(choices: Set<Int>): String = choices.sorted().joinToString(",")

    fun decodeVote(raw: String?, optionCount: Int): Set<Int> =
        raw.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 0 until optionCount }
            .toSet()

    /** Bir seçeneğe dokunulduğunda oluşacak yeni seçim. */
    fun toggle(current: Set<Int>, option: Int, multi: Boolean): Set<Int> = when {
        option in current -> current - option
        multi -> current + option
        else -> setOf(option)
    }

    /**
     * Gelen oyuncuları rastgele iki dengeli takıma böler (fark en fazla 1 kişi).
     */
    fun splitTeams(players: List<String>, random: Random = java.security.SecureRandom()): Pair<List<String>, List<String>> {
        val shuffled = players.distinct().toMutableList()
        for (i in shuffled.indices.reversed()) {
            val j = random.nextInt(i + 1)
            val t = shuffled[i]; shuffled[i] = shuffled[j]; shuffled[j] = t
        }
        val half = (shuffled.size + 1) / 2
        return shuffled.take(half) to shuffled.drop(half)
    }

    /** Takım kur sonucunun gruba gönderilen metni. */
    fun teamsMessage(teamA: List<String>, teamB: List<String>): String = buildString {
        append("⚽ Takımlar hazır!\n\n")
        append("🟡 Takım 1 (${teamA.size})\n")
        teamA.forEach { append("• ").append(it).append('\n') }
        append("\n⚫ Takım 2 (${teamB.size})\n")
        teamB.forEachIndexed { i, name ->
            append("• ").append(name)
            if (i != teamB.lastIndex) append('\n')
        }
    }
}
