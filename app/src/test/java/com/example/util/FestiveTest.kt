package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class FestiveTest {

    private val tz: TimeZone = TimeZone.getTimeZone("Europe/Istanbul")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0, second: Int = 0, ms: Int = 0): Long =
        Calendar.getInstance(tz).apply {
            clear()
            set(year, month, day, hour, minute, second)
            set(Calendar.MILLISECOND, ms)
        }.timeInMillis

    @Test
    fun seasonStartsOnDecember15() {
        assertFalse(Festive.isSeason(at(2026, Calendar.DECEMBER, 14, 23, 59, 59), tz))
        assertTrue(Festive.isSeason(at(2026, Calendar.DECEMBER, 15), tz))
        assertTrue(Festive.isSeason(at(2026, Calendar.DECEMBER, 31, 23, 59), tz))
    }

    @Test
    fun seasonEndsOnJanuary2() {
        assertTrue(Festive.isSeason(at(2027, Calendar.JANUARY, 1, 23, 59, 59), tz))
        assertFalse(Festive.isSeason(at(2027, Calendar.JANUARY, 2), tz))
        assertFalse(Festive.isSeason(at(2027, Calendar.JANUARY, 20), tz))
        assertFalse(Festive.isSeason(at(2026, Calendar.NOVEMBER, 8), tz))
        assertFalse(Festive.isSeason(at(2026, Calendar.JULY, 1), tz))
    }

    @Test
    fun newYearIsNextYearInDecember() {
        assertEquals(2027, Festive.newYear(at(2026, Calendar.DECEMBER, 20), tz))
        assertEquals(2027, Festive.newYear(at(2027, Calendar.JANUARY, 1, 0, 0, 5), tz))
        assertEquals(at(2027, Calendar.JANUARY, 1), Festive.newYearAt(at(2026, Calendar.DECEMBER, 31, 22), tz))
        assertTrue(Festive.isNewYearsDay(at(2027, Calendar.JANUARY, 1, 12), tz))
        assertFalse(Festive.isNewYearsDay(at(2026, Calendar.DECEMBER, 31, 12), tz))
    }

    @Test
    fun countdownRoundsUpToWholeSeconds() {
        val c = Festive.countdown(at(2026, Calendar.DECEMBER, 31, 23, 59, 59, 600), tz)
        assertNotNull(c)
        assertEquals(1L, c!!.totalSeconds)
        assertEquals("1 saniye", Festive.countdownLabel(c))
        assertNull(Festive.countdown(at(2027, Calendar.JANUARY, 1), tz))
        assertNull(Festive.countdown(at(2027, Calendar.JANUARY, 1, 0, 0, 1), tz))
    }

    @Test
    fun countdownLabels() {
        val days = Festive.countdown(at(2026, Calendar.DECEMBER, 8, 20), tz)!!
        assertEquals(23L, days.days)
        assertEquals(4L, days.hours)
        assertEquals("23 gün 4 saat", Festive.countdownLabel(days))
        assertEquals("5 saat 12 dakika", Festive.countdownLabel(Festive.countdown(at(2026, Calendar.DECEMBER, 31, 18, 48), tz)!!))
        assertEquals("3 dakika 20 saniye", Festive.countdownLabel(Festive.countdown(at(2026, Calendar.DECEMBER, 31, 23, 56, 40), tz)!!))
        assertEquals("2 gün", Festive.countdownLabel(Festive.countdown(at(2026, Calendar.DECEMBER, 30), tz)!!))
        assertEquals("9 saniye", Festive.countdownLabel(Festive.countdown(at(2026, Calendar.DECEMBER, 31, 23, 59, 51), tz)!!))
    }

    @Test
    fun tickIsFastOnlyInTheLastHour() {
        assertEquals(30_000L, Festive.tickMillis(at(2026, Calendar.DECEMBER, 31, 22, 0), tz))
        val fast = Festive.tickMillis(at(2026, Calendar.DECEMBER, 31, 23, 30, 0, 250), tz)
        assertTrue(fast in 1L..1_000L)
    }

    @Test
    fun recognisesGreetings() {
        listOf(
            "Mutlu yıllar!",
            "Mutluuu yıllaaar!!",
            "MUTLU YILLAR 🎉",
            "iyi seneler kanka",
            "Yeni yılın kutlu olsun",
            "yeni yiliniz mutlu olsun",
            "Nice yıllara 🥂",
            "Happy New Year!",
            "herkese mutlu yeni yıllar",
            "Mutlu yıllar canım ailem"
        ).forEach { assertTrue(it, Festive.isGreeting(it)) }
    }

    @Test
    fun ignoresOtherMessages() {
        listOf(
            null,
            "",
            "   ",
            "Yıllar geçti",
            "mutlu musun",
            "iyi akşamlar",
            "sınav yılları zor",
            "mutluyum yıllardır"
        ).forEach { assertFalse(it.toString(), Festive.isGreeting(it)) }
    }

    @Test
    fun normalizeFoldsAndCollapses() {
        assertEquals("mutlu yilar", Festive.normalize("Mutluuu   YILLAAAR!!!"))
        assertEquals("iyi seneler", Festive.normalize("İyi    seneler."))
    }
}
