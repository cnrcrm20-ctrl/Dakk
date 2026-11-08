package com.example.util

/**
 * Dakk davet bağlantıları.
 *
 * Desteklenen biçimler:
 *  - https://cnrcrm20-ctrl.github.io/Dakk/invite/?u=<uid>
 *  - dakk://add?u=<uid>   (eski: dakk://invite?u=<uid>)
 *  - Doğrudan kullanıcı kimliği (Firebase UID)
 */
object InviteLinks {

    private val UID_REGEX = Regex("^[A-Za-z0-9]{20,128}$")

    fun build(baseUrl: String, uid: String): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return "${base}?u=$uid"
    }

    fun isValidUid(value: String): Boolean = UID_REGEX.matches(value)

    /** Verilen metinden (bağlantı, QR içeriği veya çıplak kimlik) kullanıcı kimliğini çıkarır. */
    fun parseUid(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (isValidUid(text)) return text

        val lower = text.lowercase()
        val looksLikeDakkLink = lower.startsWith("dakk://") ||
            (lower.startsWith("https://") && lower.contains("/dakk"))
        if (!looksLikeDakkLink) return null

        val query = text.substringAfter('?', missingDelimiterValue = "").substringBefore('#')
        if (query.isEmpty()) return null
        val params = query.split('&').mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null else part.substring(0, idx) to part.substring(idx + 1)
        }.toMap()
        val candidate = params["u"] ?: params["uid"] ?: return null
        return candidate.takeIf { isValidUid(it) }
    }
}
