package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactionsTest {

    @Test
    fun onlyFourReactionsAreAllowed() {
        assertEquals(listOf("😂", "❤️", "⭐", "👌"), Reactions.ALL)
        assertTrue(Reactions.isAllowed("❤️"))
        assertFalse(Reactions.isAllowed("👍"))
        assertFalse(Reactions.isAllowed(null))
    }

    @Test
    fun choosingSameReactionRemovesIt() {
        assertNull(Reactions.toggle("⭐", "⭐"))
        assertEquals("😂", Reactions.toggle("⭐", "😂"))
        assertEquals("👌", Reactions.toggle(null, "👌"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownReactionIsRejected() {
        Reactions.toggle(null, "🔥")
    }

    @Test
    fun summaryKeepsFixedOrderAndMarksMine() {
        val chips = Reactions.summarize(mapOf("a" to "❤️", "b" to "😂", "c" to "❤️", "d" to "🔥"), myUid = "c")
        assertEquals(
            listOf(Reactions.Chip("😂", 1, false), Reactions.Chip("❤️", 2, true)),
            chips
        )
    }

    @Test
    fun replySnippetIsSingleLineAndShort() {
        assertEquals("selam nasılsın", ReplySnippets.of("  selam\n\nnasılsın ", isImage = false))
        assertEquals("📷 Fotoğraf", ReplySnippets.of(null, isImage = true))
        assertEquals("Mesaj", ReplySnippets.of("   ", isImage = false))
        val long = "a".repeat(300)
        val snippet = ReplySnippets.of(long, isImage = false)
        assertEquals(ReplySnippets.MAX_CHARS + 1, snippet.length)
        assertTrue(snippet.endsWith("…"))
    }

    @Test
    fun screenshotLabelUsesSenderName() {
        assertEquals("Ekran görüntüsü aldın", ScreenshotNotice.label(fromMe = true, senderName = "Can"))
        assertEquals("Can ekran görüntüsü aldı", ScreenshotNotice.label(fromMe = false, senderName = "Can"))
        assertEquals("Karşı taraf ekran görüntüsü aldı", ScreenshotNotice.label(fromMe = false, senderName = ""))
    }
}
