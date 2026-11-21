package com.example.ui.components

import org.json.JSONObject

/**
 * Sohbet arka planı seçimleri (2.6.1+, yalnızca bu telefonda). [default] tüm sohbetler için, [perChat]
 * sohbete özel seçimdir. Kimlikler [WallpaperStyle.id] değerleridir; bilinmeyen kimlik "none" sayılır.
 */
data class ChatWallpapers(
    val default: String = NONE,
    val perChat: Map<String, String> = emptyMap()
) {
    fun forChat(chatId: String?): String =
        (chatId?.let { perChat[it] } ?: default).takeIf { it in IDS } ?: NONE

    /** [chatId] null ise varsayılanı değiştirir ve sohbete özel seçimleri temizler. */
    fun with(chatId: String?, id: String): ChatWallpapers {
        val safe = id.takeIf { it in IDS } ?: NONE
        return if (chatId == null) ChatWallpapers(safe, emptyMap())
        else copy(perChat = if (safe == default) perChat - chatId else perChat + (chatId to safe))
    }

    fun toJson(): String {
        val chats = JSONObject()
        perChat.forEach { (k, v) -> chats.put(k, v) }
        return JSONObject().put("d", default).put("c", chats).toString()
    }

    companion object {
        const val NONE = "none"
        val IDS: List<String> = listOf(NONE, "sunrise", "ocean", "forest", "night", "candy", "dots")

        fun load(raw: String?): ChatWallpapers {
            if (raw.isNullOrBlank()) return ChatWallpapers()
            return try {
                val o = JSONObject(raw)
                val chats = o.optJSONObject("c")
                val map = LinkedHashMap<String, String>()
                chats?.keys()?.forEach { k -> chats.optString(k).takeIf { it in IDS }?.let { map[k] = it } }
                ChatWallpapers(o.optString("d", NONE).takeIf { it in IDS } ?: NONE, map)
            } catch (_: Exception) {
                ChatWallpapers()
            }
        }
    }
}
