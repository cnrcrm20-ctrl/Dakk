package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WhatsNewTest {

    @Test
    fun parsesVersionHeaderAndNotes() {
        val notes = WhatsNew.parse("\n# 2.6.1\n\n### ✏️ Düzenleme\n- Mesajı düzelt\n")!!
        assertEquals("2.6.1", notes.version)
        assertEquals(
            listOf(ReleaseNotes.Block.Heading("✏️ Düzenleme"), ReleaseNotes.Block.Bullet("Mesajı düzelt")),
            notes.blocks
        )
    }

    @Test
    fun rejectsMissingHeaderOrEmptyNotes() {
        assertNull(WhatsNew.parse(null))
        assertNull(WhatsNew.parse("### Başlık\n- madde"))
        assertNull(WhatsNew.parse("# 2.6.1\n\n"))
        assertEquals("2.6.1", WhatsNew.parse("# v2.6.1\n- x")?.version)
    }

    @Test
    fun showOnlyAfterUpdateAndOncePerVersion() {
        assertTrue(WhatsNew.shouldShow(seenCode = -1, currentCode = 29, freshInstall = false))
        assertTrue(WhatsNew.shouldShow(seenCode = 28, currentCode = 29, freshInstall = false))
        assertFalse(WhatsNew.shouldShow(seenCode = 29, currentCode = 29, freshInstall = false))
        assertFalse(WhatsNew.shouldShow(seenCode = -1, currentCode = 29, freshInstall = true))
    }

    /** APK'daki notlar uygulamanın sürümüyle aynı olmalı; aksi halde ekran hiç görünmez. */
    @Test
    fun bundledNotesMatchAppVersion() {
        val asset = listOf("src/main/assets/whats_new.md", "app/src/main/assets/whats_new.md").map(::File).first { it.isFile }
        val gradle = listOf("build.gradle.kts", "app/build.gradle.kts").map(::File).first { it.isFile && it.readText().contains("dakkVersionName") }
        val version = Regex("""dakkVersionName\s*=\s*"([^"]+)"""").find(gradle.readText())!!.groupValues[1]
        assertEquals(version, WhatsNew.parse(asset.readText())?.version)
    }
}
