package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreBundleTest {

    private fun entry(i: Int, kind: String = "T") =
        RestoreBundle.Entry("m$i", if (i % 2 == 0) "ali" else "veli", "Ali", 1_700_000_000_000L + i, kind, "Mesaj numarası $i 👋")

    @Test
    fun roundTrip() {
        val entries = (1..50).map { entry(it) } + entry(51, "S")
        val decoded = RestoreBundle.decode(RestoreBundle.encode("ali_veli", entries))
        assertNotNull(decoded)
        assertEquals("ali_veli", decoded!!.first)
        assertEquals(entries, decoded.second)
    }

    @Test
    fun withinLimitKeepsNewest() {
        val entries = (1..3_000).map { entry(it) }
        val (packed, count) = RestoreBundle.encodeWithinLimit("c", entries, limit = 4_000)!!
        assertTrue(packed.size <= 4_000)
        assertTrue(count in 1 until 3_000)
        val decoded = RestoreBundle.decode(packed)!!.second
        assertEquals(entries.takeLast(count), decoded)
    }

    @Test
    fun garbageIsRejected() {
        assertNull(RestoreBundle.decode(byteArrayOf(1, 2, 3, 4)))
        assertNull(RestoreBundle.decodePointer("not json"))
        assertNull(RestoreBundle.decodePointer("{\"r\":1,\"m\":\"../../etc\"}"))
    }

    @Test
    fun pointerRoundTrip() {
        val p = RestoreBundle.Pointer("0f8e2b1c-aaaa-bbbb-cccc-1234567890ab", 120)
        assertEquals(p, RestoreBundle.decodePointer(RestoreBundle.encodePointer(p)))
    }

    @Test
    fun shouldServe() {
        val now = 2_000_000_000_000L
        val p = UserProfile("u", "Ali", "", "", "KEY", caps = 1, backup = true, restoreAt = now - 1_000)
        assertTrue(RestoreBundle.shouldServe(p, 0L, true, now))
        // Bende yedek kapalı, karşıda kapalı, eski sürüm veya zaten gönderildi.
        assertFalse(RestoreBundle.shouldServe(p, 0L, false, now))
        assertFalse(RestoreBundle.shouldServe(p.copy(backup = false), 0L, true, now))
        assertFalse(RestoreBundle.shouldServe(p.copy(caps = 0), 0L, true, now))
        assertFalse(RestoreBundle.shouldServe(p, p.restoreAt, true, now))
        // İstek yok veya 14 günden eski.
        assertFalse(RestoreBundle.shouldServe(p.copy(restoreAt = 0L), 0L, true, now))
        assertFalse(RestoreBundle.shouldServe(p.copy(restoreAt = now - RestoreBundle.REQUEST_TTL_MS - 1), 0L, true, now))
    }
}
