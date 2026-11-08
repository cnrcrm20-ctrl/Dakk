package com.example.util

/**
 * GitHub Release notlarını uygulama içi güncelleme penceresinde gösterilecek sade bloklara çevirir.
 *
 * - İlk `---` ayracından sonrası (kurulum talimatı, APK SHA-256 vb.) kullanıcıya gösterilmez.
 * - `### Başlık` → [Block.Heading], `- madde` → [Block.Bullet]
 * - Bir maddenin girintili devam satırları aynı maddeye eklenir.
 * - `**kalın**`, `` `kod` `` ve `[metin](bağlantı)` işaretleri sadeleştirilir.
 */
object ReleaseNotes {

    sealed class Block {
        data class Heading(val text: String) : Block()
        data class Bullet(val text: String) : Block()
        data class Paragraph(val text: String) : Block()
    }

    private val HEADING = Regex("""^\s{0,3}#{1,6}\s+(.*)$""")
    private val BULLET = Regex("""^\s{0,3}[-*+]\s+(.*)$""")
    private val LINK = Regex("""\[([^\]]+)]\([^)]*\)""")

    fun parse(raw: String?): List<Block> {
        val blocks = mutableListOf<Block>()
        for (line in raw.orEmpty().replace("\r\n", "\n").lines()) {
            val trimmed = line.trim()
            if (trimmed == "---" || trimmed == "***" || trimmed == "___") break
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("<!--") || trimmed.startsWith("**Full Changelog**")) continue

            val heading = HEADING.matchEntire(line)
            val bullet = BULLET.matchEntire(line)
            when {
                heading != null -> blocks += Block.Heading(clean(heading.groupValues[1]))
                bullet != null -> blocks += Block.Bullet(clean(bullet.groupValues[1]))
                else -> {
                    val text = clean(trimmed)
                    val last = blocks.lastOrNull()
                    val isContinuation = line.startsWith(" ") || line.startsWith("\t")
                    when {
                        last is Block.Bullet && isContinuation ->
                            blocks[blocks.lastIndex] = Block.Bullet("${last.text} $text")
                        last is Block.Paragraph ->
                            blocks[blocks.lastIndex] = Block.Paragraph("${last.text} $text")
                        else -> blocks += Block.Paragraph(text)
                    }
                }
            }
        }
        return blocks.filterNot { block ->
            when (block) {
                is Block.Heading -> block.text.isBlank()
                is Block.Bullet -> block.text.isBlank()
                is Block.Paragraph -> block.text.isBlank()
            }
        }
    }

    private fun clean(text: String): String =
        text.replace(LINK, "$1")
            .replace("**", "")
            .replace("__", "")
            .replace("`", "")
            .replace(Regex("""\s+"""), " ")
            .trim()
}
