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
}
