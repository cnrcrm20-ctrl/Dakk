package com.example.auth

/**
 * Credential Manager / Google Play Hizmetleri hata mesajlarını sınıflandırır.
 *
 * Önemli: Google, uygulamanın imza parmak izi (SHA-1) veya OAuth yapılandırması hatalı olduğunda
 * hesap seçildikten SONRA çoğu zaman `GetCredentialCancellationException` fırlatır
 * (ör. "[16] Account reauth failed."). Bu bir kullanıcı iptali değildir ve sessizce yutulmamalıdır.
 */
object AuthErrorClassifier {

    enum class Kind {
        /** Kullanıcı hesap seçiciyi kendisi kapattı — mesaj gösterme. */
        USER_CANCELLED,
        /** SHA-1 / paket adı / OAuth istemcisi / izin ekranı yapılandırma sorunu. */
        CONFIGURATION,
        /** Başka bir hata. */
        OTHER
    }

    private val CONFIG_MARKERS = listOf(
        "28444",
        "developer console",
        "reauth",
        "unregistered_on_api_console",
        "invalid_audience",
        "not configured",
        "12500"
    )

    fun classify(message: String?, isCancellation: Boolean): Kind {
        val raw = message.orEmpty()
        val lower = raw.lowercase()
        if (CONFIG_MARKERS.any { lower.contains(it) } || Regex("""(^|\D)10:""").containsMatchIn(raw)) {
            return Kind.CONFIGURATION
        }
        if (isCancellation) {
            // "[16] Cancelled by user." / "activity is cancelled by the user." / boş mesaj
            return if (lower.isBlank() || lower.contains("user")) Kind.USER_CANCELLED else Kind.CONFIGURATION
        }
        return Kind.OTHER
    }
}
