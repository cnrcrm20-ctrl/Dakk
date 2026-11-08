package com.example.util

import com.example.util.ReleaseNotes.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesTest {

    private val sample = """
        ### Düzeltildi
        - Google hesabı seçildikten sonra giriş ekranına sessizce geri dönme sorunu. Google'ın yapılandırma
          hatalarını "iptal" olarak bildirdiği durumlar (`[16] Account reauth failed`) artık yakalanıyor.
        - **Kalın** ve [bağlantı](https://example.com) temizlenir.

        ---
        ### Kurulum
        1. Aşağıdaki **Dakk-v2.0.1.apk** dosyasını indirin.

        APK SHA-256: `abc`
    """.trimIndent()

    @Test
    fun cutsEverythingAfterSeparator() {
        val blocks = ReleaseNotes.parse(sample)
        assertTrue(blocks.none { it.toString().contains("Kurulum") || it.toString().contains("SHA-256") })
    }

    @Test
    fun mergesContinuationLinesAndCleansMarkdown() {
        val blocks = ReleaseNotes.parse(sample)
        assertEquals(Block.Heading("Düzeltildi"), blocks[0])
        assertEquals(
            Block.Bullet(
                "Google hesabı seçildikten sonra giriş ekranına sessizce geri dönme sorunu. Google'ın yapılandırma " +
                    "hatalarını \"iptal\" olarak bildirdiği durumlar ([16] Account reauth failed) artık yakalanıyor."
            ),
            blocks[1]
        )
        assertEquals(Block.Bullet("Kalın ve bağlantı temizlenir."), blocks[2])
        assertEquals(3, blocks.size)
    }

    @Test
    fun handlesEmptyAndPlainText() {
        assertTrue(ReleaseNotes.parse(null).isEmpty())
        assertEquals(listOf(Block.Paragraph("Dakk v2.1.0")), ReleaseNotes.parse("Dakk v2.1.0"))
    }
}
