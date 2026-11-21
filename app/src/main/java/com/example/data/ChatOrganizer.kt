package com.example.data

import android.content.Context

/**
 * Sohbet listesinin yalnızca bu telefonda tutulan düzeni (2.6.1+): üste sabitlenenler, arşiv ve gizli
 * sohbetler. Karşı taraf hiçbirini görmez. Gizli sohbetler parmak izi / ekran kilidiyle açılır ve
 * bildirimlerinde ad ya da içerik gösterilmez.
 */
data class ChatOrganizer(
    /** Sabitlenen sohbetler, sabitlenme sırasıyla (ilk sabitlenen en üstte). */
    val pinned: List<String> = emptyList(),
    val archived: Set<String> = emptySet(),
    val hidden: Set<String> = emptySet()
) {
    /** Ana listede, arşivde ve gizli bölümde gösterilecek sohbetler. */
    data class Lists(
        val main: List<ChatSummary>,
        val archived: List<ChatSummary>,
        val hidden: List<ChatSummary>
    )

    fun isPinned(chatId: String) = chatId in pinned
    fun isArchived(chatId: String) = chatId in archived
    fun isHidden(chatId: String) = chatId in hidden

    /**
     * Gizli sohbetler yalnızca gizli bölümde, arşivlenenler yalnızca arşivde görünür. Ana listede sabitlenenler
     * en üstte (sabitlenme sırasıyla), diğerleri son mesaja göre kalır.
     */
    fun organize(chats: List<ChatSummary>): Lists {
        val hiddenChats = chats.filter { it.chatId in hidden }
        val visible = chats.filter { it.chatId !in hidden }
        val archivedChats = visible.filter { it.chatId in archived }
        val rest = visible.filter { it.chatId !in archived }
        val order = pinned.withIndex().associate { (i, id) -> id to i }
        val top = rest.filter { it.chatId in order }.sortedBy { order[it.chatId] }.map { it.copy(isPinned = true) }
        val others = rest.filter { it.chatId !in order }
        return Lists(main = top + others, archived = archivedChats, hidden = hiddenChats)
    }

    /** Sabitler; en fazla [MAX_PINNED]. Sınır doluysa null döner. Arşivden çıkarır. */
    fun pin(chatId: String): ChatOrganizer? {
        if (chatId in pinned) return this
        if (pinned.size >= MAX_PINNED) return null
        return copy(pinned = pinned + chatId, archived = archived - chatId)
    }

    fun unpin(chatId: String) = copy(pinned = pinned - chatId)

    fun archive(chatId: String) = copy(archived = archived + chatId, pinned = pinned - chatId)

    fun unarchive(chatId: String) = copy(archived = archived - chatId)

    /** Gizli sohbete taşır: sabit ve arşivden çıkar. */
    fun hide(chatId: String) = copy(hidden = hidden + chatId, pinned = pinned - chatId, archived = archived - chatId)

    fun unhide(chatId: String) = copy(hidden = hidden - chatId)

    /** Artık var olmayan sohbetleri temizler (sohbet silindi, gruptan çıkıldı). */
    fun retainOnly(existing: Set<String>): ChatOrganizer {
        val p = pinned.filter { it in existing }
        val a = archived.intersect(existing)
        val h = hidden.intersect(existing)
        return if (p == pinned && a == archived && h == hidden) this else ChatOrganizer(p, a, h)
    }

    fun save(context: Context) {
        context.applicationContext.getSharedPreferences(ChatPrefs.PREFS, Context.MODE_PRIVATE).edit()
            .putString(PREF_PINNED, pinned.joinToString(","))
            .putStringSet(PREF_ARCHIVED, archived)
            .putStringSet(ChatPrefs.PREF_HIDDEN, hidden)
            .apply()
    }

    companion object {
        const val MAX_PINNED = 3
        private const val PREF_PINNED = "pinned_chats"
        private const val PREF_ARCHIVED = "archived_chats"

        fun load(context: Context): ChatOrganizer {
            val prefs = context.applicationContext.getSharedPreferences(ChatPrefs.PREFS, Context.MODE_PRIVATE)
            return ChatOrganizer(
                pinned = prefs.getString(PREF_PINNED, "").orEmpty().split(',').filter { it.isNotBlank() }.distinct(),
                archived = prefs.getStringSet(PREF_ARCHIVED, emptySet()).orEmpty().toSet(),
                hidden = prefs.getStringSet(ChatPrefs.PREF_HIDDEN, emptySet()).orEmpty().toSet()
            )
        }
    }
}
