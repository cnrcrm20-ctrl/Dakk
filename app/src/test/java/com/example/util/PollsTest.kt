package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class PollsTest {

    @Test
    fun buildCleansQuestionAndOptions() {
        val spec = Polls.build("  Akşam ne yiyelim?  ", listOf(" Pizza ", "", "pizza", "Döner", "  "), multi = true, isMatch = false)!!
        assertEquals("Akşam ne yiyelim?", spec.question)
        assertEquals(listOf("Pizza", "Döner"), spec.options)
        assertTrue(spec.multi)
    }

    @Test
    fun buildRejectsInvalidPolls() {
        assertNull(Polls.build("", listOf("A", "B"), multi = false, isMatch = false))
        assertNull(Polls.build("Soru", listOf("Tek"), multi = false, isMatch = false))
        assertNull(Polls.build("Soru", listOf("Aynı", "aynı"), multi = false, isMatch = false))
    }

    @Test
    fun optionsAreLimited() {
        val many = (1..15).map { "Seçenek $it" }
        assertEquals(Polls.MAX_OPTIONS, Polls.cleanOptions(many).size)
        assertEquals(Polls.MAX_OPTION, Polls.cleanOptions(listOf("x".repeat(200))).first().length)
    }

    @Test
    fun matchPollIsAlwaysSingleChoice() {
        val spec = Polls.build(Polls.MATCH_QUESTION, Polls.MATCH_OPTIONS, multi = true, isMatch = true)!!
        assertTrue(spec.isMatch)
        assertEquals(false, spec.multi)
        assertEquals("Geliyorum", spec.options.first())
    }

    @Test
    fun voteEncodingRoundTrip() {
        assertEquals("0,2", Polls.encodeVote(setOf(2, 0)))
        assertEquals(setOf(0, 2), Polls.decodeVote("0,2", 3))
        assertEquals(setOf(1), Polls.decodeVote("1, 7, x, -1", 3))
        assertEquals(emptySet<Int>(), Polls.decodeVote(null, 3))
        assertEquals(emptySet<Int>(), Polls.decodeVote("", 3))
    }

    @Test
    fun toggleRespectsChoiceMode() {
        assertEquals(setOf(1), Polls.toggle(setOf(0), 1, multi = false))
        assertEquals(emptySet<Int>(), Polls.toggle(setOf(1), 1, multi = false))
        assertEquals(setOf(0, 1), Polls.toggle(setOf(0), 1, multi = true))
        assertEquals(setOf(0), Polls.toggle(setOf(0, 1), 1, multi = true))
    }

    @Test
    fun teamsAreBalancedAndComplete() {
        val players = listOf("Ali", "Veli", "Ayşe", "Fatma", "Can", "Ece", "Mert")
        val (a, b) = Polls.splitTeams(players, Random(42))
        assertEquals(4, a.size)
        assertEquals(3, b.size)
        assertEquals(players.toSet(), (a + b).toSet())
        assertTrue(a.intersect(b.toSet()).isEmpty())
    }

    @Test
    fun teamsIgnoreDuplicates() {
        val (a, b) = Polls.splitTeams(listOf("Ali", "Ali", "Veli"), Random(1))
        assertEquals(2, a.size + b.size)
    }

    @Test
    fun teamsMessageListsEveryone() {
        val text = Polls.teamsMessage(listOf("Ali", "Can"), listOf("Veli"))
        assertTrue(text.startsWith("⚽ Takımlar hazır!"))
        assertTrue(text.contains("Takım 1 (2)"))
        assertTrue(text.contains("Takım 2 (1)"))
        assertTrue(text.contains("• Ali") && text.contains("• Can") && text.contains("• Veli"))
    }
}
