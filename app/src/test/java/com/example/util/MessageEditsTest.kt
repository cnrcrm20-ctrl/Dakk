package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageEditsTest {

    @Test
    fun encodeParseRoundTrip() {
        val raw = MessageEdits.encode("m1", "düzeltilmiş \"metin\"\nikinci satır")
        assertEquals(MessageEdits.Edit("m1", "düzeltilmiş \"metin\"\nikinci satır"), MessageEdits.parse(raw))
    }

    @Test
    fun parseRejectsGarbage() {
        assertNull(MessageEdits.parse(null))
        assertNull(MessageEdits.parse(""))
        assertNull(MessageEdits.parse("merhaba"))
        assertNull(MessageEdits.parse("""{"t":"m1"}"""))
        assertNull(MessageEdits.parse("""{"x":"metin"}"""))
        assertNull(MessageEdits.parse("""{"t":"","x":"metin"}"""))
    }

    @Test
    fun editWindowIsOneHour() {
        val sent = 1_000_000L
        assertTrue(MessageEdits.canEdit(sent, sent))
        assertTrue(MessageEdits.canEdit(sent, sent + MessageEdits.WINDOW_MS))
        assertFalse(MessageEdits.canEdit(sent, sent + MessageEdits.WINDOW_MS + 1))
        assertFalse(MessageEdits.canEdit(sent, sent - 1))
        assertFalse(MessageEdits.canEdit(0, 10))
    }

    @Test
    fun latestEditBySenderWins() {
        val senders = mapOf("m1" to "ali", "m2" to "veli")
        val entries = listOf(
            MessageEdits.Entry("ali", 30, MessageEdits.Edit("m1", "üçüncü")),
            MessageEdits.Entry("ali", 10, MessageEdits.Edit("m1", "birinci")),
            MessageEdits.Entry("ali", 20, MessageEdits.Edit("m1", "ikinci"))
        )
        val out = MessageEdits.resolve(entries) { senders[it] }
        assertEquals(MessageEdits.Applied("üçüncü", 30), out["m1"])
        assertNull(out["m2"])
    }

    @Test
    fun othersCannotEditYourMessage() {
        val senders = mapOf("m1" to "ali")
        val entries = listOf(
            MessageEdits.Entry("ali", 10, MessageEdits.Edit("m1", "doğru")),
            MessageEdits.Entry("veli", 20, MessageEdits.Edit("m1", "sahte")),
            MessageEdits.Entry("veli", 30, MessageEdits.Edit("yok", "bilinmeyen mesaj"))
        )
        val out = MessageEdits.resolve(entries) { senders[it] }
        assertEquals("doğru", out["m1"]?.text)
        assertEquals(1, out.size)
    }
}
