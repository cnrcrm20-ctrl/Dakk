package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TurkishSuffixTest {

    @Test
    fun dative() {
        assertEquals("Ali'ye", TurkishSuffix.dative("Ali"))
        assertEquals("Ahmet'e", TurkishSuffix.dative("Ahmet"))
        assertEquals("Can'a", TurkishSuffix.dative("Can"))
        assertEquals("Burak'a", TurkishSuffix.dative("Burak"))
        assertEquals("Ayşe'ye", TurkishSuffix.dative("Ayşe"))
        assertEquals("Okul'a", TurkishSuffix.dative("Okul"))
        assertEquals("Gül'e", TurkishSuffix.dative("Gül"))
        assertEquals("IŞIK'a", TurkishSuffix.dative("IŞIK"))
    }

    @Test
    fun genitive() {
        assertEquals("Ali'nin", TurkishSuffix.genitive("Ali"))
        assertEquals("Ahmet'in", TurkishSuffix.genitive("Ahmet"))
        assertEquals("Can'ın", TurkishSuffix.genitive("Can"))
        assertEquals("Burcu'nun", TurkishSuffix.genitive("Burcu"))
        assertEquals("Onur'un", TurkishSuffix.genitive("Onur"))
        assertEquals("Gül'ün", TurkishSuffix.genitive("Gül"))
        assertEquals("Örs'ün", TurkishSuffix.genitive("Örs"))
    }

    @Test
    fun dativeNumbersFollowPronunciation() {
        assertEquals("2026'ya", TurkishSuffix.dativeNumber(2026))
        assertEquals("2027'ye", TurkishSuffix.dativeNumber(2027))
        assertEquals("2028'e", TurkishSuffix.dativeNumber(2028))
        assertEquals("2029'a", TurkishSuffix.dativeNumber(2029))
        assertEquals("2030'a", TurkishSuffix.dativeNumber(2030))
        assertEquals("2000'e", TurkishSuffix.dativeNumber(2000))
        assertEquals("100'e", TurkishSuffix.dativeNumber(100))
    }
}
