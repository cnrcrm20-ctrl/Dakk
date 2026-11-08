package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMentionTest {

    @Test
    fun recognizesMention() {
        assertTrue(AssistantMention.isMention("@dakk bu akşam ne yesek?"))
        assertTrue(AssistantMention.isMention("  @Dakk, özetle"))
        assertTrue(AssistantMention.isMention("@DAKK: selam"))
    }

    @Test
    fun ignoresNonMentions() {
        assertFalse(AssistantMention.isMention("@dakk"))
        assertFalse(AssistantMention.isMention("@dakk   "))
        assertFalse(AssistantMention.isMention("@dakkus selam"))
        assertFalse(AssistantMention.isMention("selam @dakk"))
        assertFalse(AssistantMention.isMention(""))
    }

    @Test
    fun extractsQuestion() {
        assertEquals("bu akşam ne yesek?", AssistantMention.question("@dakk bu akşam ne yesek?"))
        assertEquals("özetle", AssistantMention.question("@Dakk, özetle"))
        assertEquals("selam", AssistantMention.question("@DAKK: selam"))
    }
}
