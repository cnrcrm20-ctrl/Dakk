package com.example.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceMetaTest {

    @Test
    fun jsonRoundTrip() {
        val meta = VoiceMeta("abcd1234-ef56", 2, 7_400L, 29_000L, listOf(0, 5, 31, 12), remote = true)
        val parsed = VoiceMeta.parse(meta.toJson())
        assertEquals(meta, parsed)
    }

    @Test
    fun rejectsOtherPayloads() {
        assertNull(VoiceMeta.parse(null))
        assertNull(VoiceMeta.parse("merhaba"))
        assertNull(VoiceMeta.parse("""{"type":"image","mediaId":"abcd1234"}"""))
        assertNull(VoiceMeta.parse("""{"type":"voice","mediaId":"../etc"}"""))
    }

    @Test
    fun clampsUntrustedNumbers() {
        val parsed = VoiceMeta.parse("""{"type":"voice","mediaId":"abcd1234","chunks":99,"dur":999999999}""")
        assertNotNull(parsed)
        assertEquals(MediaManager.MAX_CHUNKS, parsed!!.chunks)
        assertTrue(parsed.durationMs <= VoiceMeta.MAX_DURATION_MS + 5_000L)
        assertEquals(emptyList<Int>(), parsed.waveform)
    }

    @Test
    fun formatsDuration() {
        assertEquals("0:00", VoiceMeta.formatDuration(0))
        assertEquals("0:07", VoiceMeta.formatDuration(7_400))
        assertEquals("1:05", VoiceMeta.formatDuration(64_600))
        assertEquals("5:00", VoiceMeta.formatDuration(VoiceMeta.MAX_DURATION_MS))
        assertEquals("🎤 Sesli mesaj (0:12)", VoiceMeta.preview(12_000))
    }

    @Test
    fun waveformEncodingRoundTrips() {
        val bars = (0..31).toList()
        assertEquals(bars, Waveform.decode(Waveform.encode(bars)))
        assertEquals(listOf(31, 0), Waveform.decode(Waveform.encode(listOf(99, -3))))
        assertEquals(emptyList<Int>(), Waveform.decode("!!"))
    }

    @Test
    fun waveformFromAmplitudes() {
        assertEquals(List(Waveform.BARS) { 2 }, Waveform.fromAmplitudes(emptyList()))
        val samples = List(400) { if (it in 200..220) 20_000 else 300 }
        val bars = Waveform.fromAmplitudes(samples)
        assertEquals(Waveform.BARS, bars.size)
        assertTrue(bars.all { it in 2..Waveform.MAX })
        assertEquals(Waveform.MAX, bars.max())
        assertTrue(bars.first() < bars[20])
    }
}
