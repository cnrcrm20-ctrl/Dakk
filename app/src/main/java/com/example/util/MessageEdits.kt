package com.example.util

import org.json.JSONObject

/**
 * Mesaj düzenleme (2.6.1+).
 *
 * Düzenleme, asıl mesajın üzerine yazılmaz; sohbete görünmeyen, uçtan uca şifreli küçük bir "EDIT" kaydı
 * eklenir: {"t": düzenlenen mesajın kimliği, "x": yeni metin}. Böylece mesaj sunucudan çoktan teslim
 * edilip kaldırılmış olsa da (posta kutusu) düzenleme herkese ulaşır ve telefonlardaki arşivde kalır.
 * Ekranda her mesaj için o mesajı gönderen kişinin en son düzenlemesi gösterilir.
 */
object MessageEdits {
    const val MESSAGE_TYPE = "EDIT"

    /** Mesaj gönderildikten sonra bu süre boyunca düzenlenebilir. */
    const val WINDOW_MS = 60L * 60 * 1000

    const val LABEL = "düzenlendi"

    data class Edit(val targetId: String, val text: String)

    /** Sohbetteki bir düzenleme kaydı (çözülmüş). */
    data class Entry(val senderId: String, val createdAt: Long, val edit: Edit)

    /** Ekranda gösterilecek son hal. */
    data class Applied(val text: String, val editedAt: Long)

    fun encode(targetId: String, text: String): String =
        JSONObject().put("t", targetId).put("x", text).toString()

    fun parse(plain: String?): Edit? {
        if (plain.isNullOrBlank()) return null
        return try {
            val o = JSONObject(plain)
            val id = o.optString("t", "")
            val text = o.optString("x", "")
            if (id.isBlank() || text.isBlank()) null else Edit(id, text)
        } catch (_: Exception) {
            null
        }
    }

    /** [sentAt] zamanında gönderilen mesaj şu an düzenlenebilir mi? */
    fun canEdit(sentAt: Long, now: Long): Boolean = sentAt > 0 && now - sentAt in 0..WINDOW_MS

    /**
     * Her mesaj için geçerli son düzenleme. Yalnızca mesajı gönderen kişinin düzenlemeleri sayılır
     * ([senderOf]: mesaj kimliği → gönderen; bilinmeyen mesajların düzenlemeleri yok sayılır).
     */
    fun resolve(entries: List<Entry>, senderOf: (String) -> String?): Map<String, Applied> {
        val out = HashMap<String, Applied>()
        entries.sortedBy { it.createdAt }.forEach { e ->
            val owner = senderOf(e.edit.targetId) ?: return@forEach
            if (owner != e.senderId) return@forEach
            out[e.edit.targetId] = Applied(e.edit.text, e.createdAt)
        }
        return out
    }
}
