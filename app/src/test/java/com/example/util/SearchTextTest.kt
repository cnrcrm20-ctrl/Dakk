package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextTest {

    @Test
    fun ignoresCaseAndTurkishCharacters() {
        assertTrue(SearchText.matches("Çağrı Yılmaz", "cagri"))
        assertTrue(SearchText.matches("Çağrı Yılmaz", "YILMAZ"))
        assertTrue(SearchText.matches("ışık", "ISIK"))
        assertTrue(SearchText.matches("İstanbul", "istanbul"))
        assertTrue(SearchText.matches("Şükrü", "sukru"))
        assertFalse(SearchText.matches("Ahmet", "mehmet"))
    }

    @Test
    fun blankQueryMatchesEverythingButHighlightsNothing() {
        assertTrue(SearchText.matches("Ahmet", "   "))
        assertNull(SearchText.matchRange("Ahmet", ""))
    }

    @Test
    fun rangePointsIntoOriginalText() {
        val text = "Merhaba Gülşen!"
        val range = SearchText.matchRange(text, " gulsen")
        assertEquals("Gülşen", text.substring(range!!))
    }

    @Test
    fun foldKeepsLength() {
        val text = "İĞÜŞÖÇ ığüşöç"
        assertEquals(text.length, SearchText.fold(text).length)
        assertEquals("igusoc igusoc", SearchText.fold(text))
    }

    @Test
    fun matchesAnyChecksEveryField() {
        assertTrue(SearchText.matchesAny("yarın", "Can", "Yarın görüşürüz"))
        assertFalse(SearchText.matchesAny("film", "Can", "Yarın görüşürüz"))
    }
}
