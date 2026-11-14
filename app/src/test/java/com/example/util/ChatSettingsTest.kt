package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSettingsTest {

    private val now = 1_700_000_000_000L

    @Test
    fun disappearingOptions() {
        assertEquals(listOf(0L, 86_400L, 604_800L), Disappearing.OPTIONS)
        assertEquals("Kapalı", Disappearing.label(0))
        assertEquals("24 saat", Disappearing.label(Disappearing.DAY))
        assertEquals("7 gün", Disappearing.label(Disappearing.WEEK))
        assertFalse(Disappearing.isValid(3_600))
    }

    @Test
    fun disappearingExpiry() {
        assertEquals(0L, Disappearing.expiryFor(0, now))
        assertEquals(0L, Disappearing.expiryFor(3_600, now))
        assertEquals(now + 86_400_000L, Disappearing.expiryFor(Disappearing.DAY, now))
        assertFalse(Disappearing.isExpired(0, now))
        assertFalse(Disappearing.isExpired(now + 1, now))
        assertTrue(Disappearing.isExpired(now, now))
        assertTrue(Disappearing.isExpired(now - 1, now))
    }

    @Test
    fun muteDurations() {
        assertEquals(now + MuteOptions.EIGHT_HOURS, MuteOptions.until(MuteOptions.EIGHT_HOURS, now))
        assertEquals(MuteOptions.FOREVER, MuteOptions.until(MuteOptions.FOREVER, now))
        assertTrue(MuteOptions.isMuted(MuteOptions.FOREVER, now))
        assertFalse(MuteOptions.isMuted(0, now))
        assertFalse(MuteOptions.isMuted(now, now))
    }

    @Test
    fun muteLabels() {
        assertNull(MuteOptions.label(0, now))
        assertEquals("Her zaman sessiz", MuteOptions.label(MuteOptions.FOREVER, now))
        assertEquals("8 saat sessiz", MuteOptions.label(now + MuteOptions.EIGHT_HOURS, now))
        assertEquals("7 gün sessiz", MuteOptions.label(now + MuteOptions.ONE_WEEK, now))
    }

    @Test
    fun typingLabels() {
        assertNull(TypingText.label(emptyList(), isGroup = false))
        assertEquals("yazıyor…", TypingText.label(listOf("Ali"), isGroup = false))
        assertEquals("ronaldoo7 yazıyor…", TypingText.label(listOf("ronaldoo7"), isGroup = true))
        assertEquals("ronaldoo7 ve 2 kişi yazıyor…", TypingText.label(listOf("ronaldoo7", "Ali", "Veli"), isGroup = true))
    }

    @Test
    fun systemNotes() {
        assertEquals("Ali, takma adını ronaldoo7 yaptı", SystemNotes.nickname("Ali", true, "Ali", "ronaldoo7"))
        assertEquals("Ali takma adını kaldırdı", SystemNotes.nickname("Ali", true, "Ali", "  "))
        assertEquals("Ali, Veli için takma adı Kral yaptı", SystemNotes.nickname("Ali", false, "Veli", "Kral"))
        assertEquals("Ali, Veli için takma adı kaldırdı", SystemNotes.nickname("Ali", false, "Veli", null))
        assertEquals("Ali kaybolan mesajları kapattı", SystemNotes.disappearing("Ali", 0))
        assertTrue(SystemNotes.disappearing("Ali", Disappearing.DAY).contains("24 saat"))
        assertEquals("Ali gruba ekledi: Veli, Can", SystemNotes.added("Ali", listOf("Veli", "Can")))
        assertTrue(SystemNotes.groupCreated("Ali", "x".repeat(400)).length <= SystemNotes.MAX_LENGTH)
    }
}
