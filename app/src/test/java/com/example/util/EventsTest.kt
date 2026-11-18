package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class EventsTest {

    private val tz: TimeZone = TimeZone.getTimeZone("Europe/Istanbul")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        Calendar.getInstance(tz).apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis

    private val bayram = """
        {"events": [{"id": "bayram-2027", "start": "2027-03-19", "end": "2027-03-22",
          "countdown_to": "2027-03-20T00:00", "countdown_title": "Bayrama kalan",
          "done_title": "İyi Bayramlar!", "emoji": "🌙", "greetings": ["İyi Bayramlar", "bayramın mübarek"],
          "cards": ["https://example.com/kart1.webp", "yerlesik:2", "http://kotu.com/x.png"],
          "colors": ["#123456", "#80FF0000"]}]}
    """.trimIndent()

    @Test
    fun parsesAServerEvent() {
        val spec = Events.parse(bayram, tz)!!
        assertTrue(spec.newYear)
        val e = spec.events.single()
        assertEquals("bayram-2027", e.id)
        assertEquals(at(2027, Calendar.MARCH, 19), e.start)
        // Yalnızca tarih verilen bitiş o günün sonuna kadar sürer.
        assertEquals(at(2027, Calendar.MARCH, 23), e.end)
        assertEquals(at(2027, Calendar.MARCH, 20), e.countdownTo)
        assertEquals(listOf("iyi bayramlar", "bayramin mubarek"), e.greetings)
        assertEquals(listOf(EventCard.Remote("https://example.com/kart1.webp"), EventCard.Builtin(1)), e.cards)
        assertEquals(listOf(0xFF123456L, 0x80FF0000L), e.colors)
        assertTrue(e.celebrate)
        assertEquals("İyi Bayramlar!", e.celebrateTitle)
    }

    @Test
    fun serverEventIsActiveOnlyInItsWindow() {
        val spec = Events.parse(bayram, tz)
        assertNull(Events.active(spec, at(2027, Calendar.MARCH, 18, 23, 59), true, tz))
        assertEquals("bayram-2027", Events.active(spec, at(2027, Calendar.MARCH, 19), true, tz)?.id)
        assertEquals("bayram-2027", Events.active(spec, at(2027, Calendar.MARCH, 22, 23, 59), true, tz)?.id)
        assertNull(Events.active(spec, at(2027, Calendar.MARCH, 23), true, tz))
    }

    @Test
    fun countdownAndCelebration() {
        val e = Events.parse(bayram, tz)!!.events.single()
        val before = at(2027, Calendar.MARCH, 19, 23, 0)
        assertEquals(Festive.Countdown(0, 1, 0, 0), e.countdown(before))
        assertFalse(e.celebrating(before))
        val after = at(2027, Calendar.MARCH, 20, 0, 1)
        assertNull(e.countdown(after))
        assertTrue(e.celebrating(after))
        assertTrue(e.isGreeting("İYİ BAYRAMLAR kanka"))
        assertTrue(e.isGreeting("Bayramın mübarek olsun"))
        assertFalse(e.isGreeting("mutlu yıllar"))
    }

    @Test
    fun builtInNewYearStillWorks() {
        val dec20 = at(2026, Calendar.DECEMBER, 20)
        val e = Events.active(null, dec20, true, tz)!!
        assertEquals("yilbasi-2027", e.id)
        assertEquals("2027'ye kalan", e.countdownTitle)
        assertTrue(e.hat && e.snow)
        assertEquals(4, e.cards.size)
        assertTrue(e.isGreeting("Mutlu yıllar!"))
        assertNull(Events.active(null, at(2027, Calendar.JANUARY, 2), true, tz))
        // Sunucuda feature_yilbasi kapalı ya da event_json'da "yilbasi": false.
        assertNull(Events.active(null, dec20, false, tz))
        assertNull(Events.active(Events.parse("""{"yilbasi": false}""", tz), dec20, true, tz))
    }

    @Test
    fun serverEventWinsOverNewYear() {
        val json = """{"events": [{"id": "ozel", "start": "2026-12-20", "end": "2026-12-21", "emoji": "🎁"}]}"""
        val spec = Events.parse(json, tz)
        assertEquals("ozel", Events.active(spec, at(2026, Calendar.DECEMBER, 20, 12), true, tz)?.id)
        assertEquals("yilbasi-2027", Events.active(spec, at(2026, Calendar.DECEMBER, 22), true, tz)?.id)
    }

    @Test
    fun badInputIsIgnored() {
        assertNull(Events.parse("", tz))
        assertNull(Events.parse("{bozuk", tz))
        val json = """{"events": [
            {"id": "BÜYÜK", "start": "2027-01-01", "end": "2027-01-02"},
            {"id": "ters", "start": "2027-01-05", "end": "2027-01-01"},
            {"id": "cok-uzun", "start": "2027-01-01", "end": "2027-06-01"},
            {"id": "tarihsiz"},
            {"id": "iyi", "start": "2027-01-10", "end": "2027-01-10"}
        ]}"""
        val spec = Events.parse(json, tz)!!
        assertEquals(listOf("iyi"), spec.events.map { it.id })
        assertNull(spec.events.single().countdownTo)
        assertFalse(spec.events.single().celebrate)
    }

    @Test
    fun timeFormats() {
        assertEquals(at(2027, Calendar.MARCH, 8, 21, 30), Events.time("2027-03-08T21:30", tz))
        assertEquals(at(2027, Calendar.MARCH, 8, 21, 30), Events.time("2027-03-08 21:30", tz))
        assertEquals(at(2027, Calendar.MARCH, 9), Events.time("2027-03-08", tz, endOfDay = true))
        assertEquals(1_800_000_000_000L, Events.time(1_800_000_000_000L, tz))
        assertNull(Events.time("2027-02-30", tz))
        assertNull(Events.time("yarın", tz))
        assertNotNull(Events.color("#ABCDEF"))
        assertNull(Events.color("kırmızı"))
        assertNull(Events.card("yerlesik:9"))
    }
}
