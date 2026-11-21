package com.example.util

import android.content.Context

/**
 * "Yenilikler" ekranı (2.6.1+): güncellemeden sonraki ilk açılışta bir kez tam ekran gösterilir.
 *
 * Notlar APK içindeki `assets/whats_new.md` dosyasından gelir. İlk satır `# <sürüm>` olmalıdır; dosya başka bir
 * sürüme aitse (ör. unutulup güncellenmediyse) ekran gösterilmez. Kalan kısım [ReleaseNotes] biçimindedir.
 */
object WhatsNew {
    const val ASSET = "whats_new.md"

    data class Notes(val version: String, val blocks: List<ReleaseNotes.Block>)

    private val HEADER = Regex("""^\s*#\s*v?(\d+(?:\.\d+){1,3})\s*$""")

    fun parse(raw: String?): Notes? {
        val lines = raw.orEmpty().replace("\r\n", "\n").lines().dropWhile { it.isBlank() }
        val version = lines.firstOrNull()?.let { HEADER.matchEntire(it) }?.groupValues?.get(1) ?: return null
        val blocks = ReleaseNotes.parse(lines.drop(1).joinToString("\n"))
        return if (blocks.isEmpty()) null else Notes(version, blocks)
    }

    /**
     * Ekran gösterilmeli mi? İlk kurulumda (tanıtım zaten gösterilir) ve bu sürüm daha önce görüldüyse hayır.
     * [seenCode]: en son kapatılan yenilikler ekranının sürüm kodu (-1 = hiç).
     */
    fun shouldShow(seenCode: Long, currentCode: Long, freshInstall: Boolean): Boolean =
        !freshInstall && seenCode < currentCode

    fun load(context: Context): Notes? = runCatching {
        context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { parse(it.readText()) }
    }.getOrNull()
}
