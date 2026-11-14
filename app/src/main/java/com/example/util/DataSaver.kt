package com.example.util

/**
 * Veri tasarrufu ayarı.
 * - NORMAL: fotoğraflar 1280 px, her şey kendiliğinden indirilir.
 * - SAVER: fotoğraflar 960 px gönderilir, grup fotoğrafları dokununca indirilir.
 * - AUTO: normal çalışır; sunucu aylık kotanın dolmaya yaklaştığını bildirirse kendiliğinden tasarrufa geçer.
 */
enum class DataSaver(val pref: Int, val title: String, val description: String) {
    NORMAL(0, "Normal", "Fotoğraflar 1280 piksel gönderilir ve kendiliğinden indirilir."),
    SAVER(1, "Tasarruf", "Fotoğraflar daha küçük (960 piksel) gönderilir, grup fotoğrafları dokununca indirilir."),
    AUTO(2, "Otomatik", "Normal çalışır; sunucu yoğunlaşınca kendiliğinden tasarrufa geçer. Önerilen.");

    companion object {
        fun fromPref(value: Int): DataSaver = entries.firstOrNull { it.pref == value } ?: AUTO
    }
}
