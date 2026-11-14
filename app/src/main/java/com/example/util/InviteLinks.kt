package com.example.util

/**
 * Dakk davet bağlantıları.
 *
 * Desteklenen biçimler:
 *  - https://cnrcrm20-ctrl.github.io/Dakk/invite/?u=<uid>
 *  - dakk://add?u=<uid>   (eski: dakk://invite?u=<uid>)
 *  - Doğrudan kullanıcı kimliği (Firebase UID)
 *
 * Grup davetleri:
 *  - https://cnrcrm20-ctrl.github.io/Dakk/invite/?g=<grupKimliği>&c=<davetKodu>
 *  - dakk://invite?g=<grupKimliği>&c=<davetKodu>
 */
object InviteLinks {

    private val UID_REGEX = Regex("^[A-Za-z0-9]{20,128}$")

    fun build(baseUrl: String, uid: String): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return "${base}?u=$uid"
    }

    fun isValidUid(value: String): Boolean = UID_REGEX.matches(value)

    private val GROUP_REGEX = Regex("^g_[A-Za-z0-9]{16,40}$")
    private val CODE_REGEX = Regex("^[A-Za-z0-9]{8,40}$")

    data class GroupInvite(val groupId: String, val code: String)

    fun buildGroup(baseUrl: String, groupId: String, code: String): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return "${base}?g=$groupId&c=$code"
    }

    /** Bağlantı bir grup davetiyse grup kimliğini ve davet kodunu döner. */
    fun parseGroup(raw: String?): GroupInvite? {
        val params = dakkParams(raw) ?: return null
        val group = params["g"] ?: return null
        val code = params["c"] ?: return null
        if (!GROUP_REGEX.matches(group) || !CODE_REGEX.matches(code)) return null
        return GroupInvite(group, code)
    }

    private fun dakkParams(raw: String?): Map<String, String>? {
        val text = raw?.trim().orEmpty()
        val lower = text.lowercase()
        val looksLikeDakkLink = lower.startsWith("dakk://") ||
            (lower.startsWith("https://") && lower.contains("/dakk"))
        if (!looksLikeDakkLink) return null
        val query = text.substringAfter('?', missingDelimiterValue = "").substringBefore('#')
        if (query.isEmpty()) return null
        return query.split('&').mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null else part.substring(0, idx) to part.substring(idx + 1)
        }.toMap()
    }

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
