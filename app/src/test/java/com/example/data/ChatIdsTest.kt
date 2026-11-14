package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatIdsTest {

    @Test
    fun directChatIdIsOrderIndependent() {
        assertEquals(ChatIds.of("bbb", "aaa"), ChatIds.of("aaa", "bbb"))
        assertEquals("aaa_bbb", ChatIds.of("bbb", "aaa"))
    }

    @Test
    fun groupIds() {
        val id = ChatIds.newGroupId()
        assertTrue(ChatIds.isGroup(id))
        assertTrue(ChatIds.isValidGroupId(id))
        assertEquals(22, id.length)
        assertNotEquals(id, ChatIds.newGroupId())
        assertFalse(ChatIds.isGroup("AbCdEfGhIjKlMnOpQrStUvWxYz12"))
        assertFalse(ChatIds.isGroup(ChatIds.of("gAbCdEfGhIjKlMnOpQrSt", "Zzz")))
    }

    @Test
    fun randomTokens() {
        val token = ChatIds.randomToken(32)
        assertEquals(32, token.length)
        assertTrue(token.all { it.isLetterOrDigit() })
    }
}
