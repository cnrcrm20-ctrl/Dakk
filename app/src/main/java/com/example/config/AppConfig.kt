package com.example.config

import com.example.util.VersionUtils

/**
 * Herkese açık uygulama ayarları — Firestore: app_config/public
 *
 * Bu belge yalnızca kurucular tarafından (Kurucu paneli üzerinden) değiştirilebilir;
 * oturum açmış her kullanıcı okuyabilir. Alan adları cloud/admin_panel.json ile aynıdır.
 */
data class PublicConfig(
    val assistantEnabled: Boolean = true,
    val assistantProvider: String = PROVIDER_GEMINI,
    val assistantModel: String = DEFAULT_MODEL,
    val assistantSystemPrompt: String = "",
    val assistantDailyLimit: Int = 40,
    val imagesEnabled: Boolean = true,
    val minSupportedVersion: String = "",
    val forceUpdateMessage: String = "",
    val maintenanceEnabled: Boolean = false,
    val maintenanceMessage: String = "",
    val serverUrl: String = DEFAULT_SERVER_URL,
    val announcement: Announcement? = null
) {
    /** Bu sürüm artık desteklenmiyorsa true (zorunlu güncelleme). */
    fun requiresUpdate(currentVersion: String): Boolean =
        minSupportedVersion.isNotBlank() && VersionUtils.isNewer(minSupportedVersion, currentVersion)

    val hasServer: Boolean get() = serverUrl.startsWith("https://")

    companion object {
        const val PROVIDER_GEMINI = "gemini"
        const val PROVIDER_CUSTOM = "custom"
        const val DEFAULT_MODEL = "gemini-2.5-flash"

        /** Varsayılan Dakk sunucusu. Kurucu panelindeki adres boşsa bu kullanılır. */
        const val DEFAULT_SERVER_URL = "https://hkpmrbahktbujdjpnwwb.supabase.co/functions/v1/dakk"

        fun fromMap(data: Map<String, Any?>?): PublicConfig {
            if (data == null) return PublicConfig()
            val d = PublicConfig()
            return PublicConfig(
                assistantEnabled = data.bool("assistant_enabled") ?: d.assistantEnabled,
                assistantProvider = data.str("assistant_provider")?.ifBlank { null } ?: d.assistantProvider,
                assistantModel = data.str("assistant_model")?.ifBlank { null } ?: d.assistantModel,
                assistantSystemPrompt = data.str("assistant_system_prompt") ?: "",
                assistantDailyLimit = data.long("assistant_daily_limit")?.toInt() ?: d.assistantDailyLimit,
                imagesEnabled = data.bool("images_enabled") ?: d.imagesEnabled,
                minSupportedVersion = data.str("min_supported_version")?.trim() ?: "",
                forceUpdateMessage = data.str("force_update_message") ?: "",
                maintenanceEnabled = data.bool("maintenance_enabled") ?: false,
                maintenanceMessage = data.str("maintenance_message") ?: "",
                serverUrl = data.str("server_url")?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: DEFAULT_SERVER_URL,
                announcement = (data["announcement"] as? Map<*, *>)?.let { Announcement.fromMap(it) }
            )
        }
    }
}

/**
 * Tam ekran duyuru. Her duyuru kimliği kullanıcıya bir kez gösterilir.
 * Tarihler epoch milisaniye olarak saklanır; 0 = sınır yok.
 */
data class Announcement(
    val id: String = "",
    val active: Boolean = false,
    val title: String = "",
    val body: String = "",
    val imageUrl: String = "",
    val buttonText: String = "",
    val buttonUrl: String = "",
    val startAt: Long = 0L,
    val endAt: Long = 0L,
    val minVersion: String = "",
    val dismissible: Boolean = true
) {
    /** Bu cihazda ve bu anda gösterilmeli mi? (Görülmüş olma durumu ayrıca kontrol edilir.) */
    fun isLive(now: Long, appVersion: String): Boolean {
        if (!active || id.isBlank() || (title.isBlank() && body.isBlank() && imageUrl.isBlank())) return false
        if (startAt > 0 && now < startAt) return false
        if (endAt > 0 && now > endAt) return false
        if (minVersion.isNotBlank() && VersionUtils.isNewer(minVersion, appVersion)) return false
        return true
    }

    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "active" to active,
        "title" to title,
        "body" to body,
        "image_url" to imageUrl,
        "button_text" to buttonText,
        "button_url" to buttonUrl,
        "start_at" to startAt,
        "end_at" to endAt,
        "min_version" to minVersion,
        "dismissible" to dismissible
    )

    companion object {
        fun fromMap(m: Map<*, *>): Announcement = Announcement(
            id = m.str("id") ?: "",
            active = m.bool("active") ?: false,
            title = m.str("title") ?: "",
            body = m.str("body") ?: "",
            imageUrl = m.str("image_url") ?: "",
            buttonText = m.str("button_text") ?: "",
            buttonUrl = m.str("button_url") ?: "",
            startAt = m.long("start_at") ?: 0L,
            endAt = m.long("end_at") ?: 0L,
            minVersion = m.str("min_version") ?: "",
            dismissible = m.bool("dismissible") ?: true
        )
    }
}

/** Yalnızca kuruculara açık ayarlar — Firestore: app_config/private */
data class PrivateConfig(
    val customApiBaseUrl: String = "",
    val customApiKey: String = "",
    val customApiModel: String = ""
) {
    val isUsable: Boolean
        get() = customApiBaseUrl.startsWith("https://") && customApiKey.isNotBlank() && customApiModel.isNotBlank()

    companion object {
        fun fromMap(data: Map<String, Any?>?): PrivateConfig = PrivateConfig(
            customApiBaseUrl = data?.str("custom_api_base_url")?.trim()?.trimEnd('/') ?: "",
            customApiKey = data?.str("custom_api_key")?.trim() ?: "",
            customApiModel = data?.str("custom_api_model")?.trim() ?: ""
        )
    }
}

internal fun Map<*, *>.str(key: String): String? = this[key] as? String
internal fun Map<*, *>.bool(key: String): Boolean? = this[key] as? Boolean
internal fun Map<*, *>.long(key: String): Long? = when (val v = this[key]) {
    is Number -> v.toLong()
    is String -> v.trim().toLongOrNull()
    is com.google.firebase.Timestamp -> v.toDate().time
    else -> null
}
