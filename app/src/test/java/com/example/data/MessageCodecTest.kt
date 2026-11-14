package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageCodecTest {

    private fun doc(id: String, t: Long) = MessageDoc(
        id = id, senderId = "u1", content = "ŞİFRELİ==", senderKey = "SK", recipientKey = "RK",
        deliveryStatus = "READ", createdAt = t, isPending = false, messageType = "POLL",
        reactions = mapOf("u2" to "R"), keys = mapOf("u1" to "K1", "u2" to "K2"),
        votes = mapOf("u2" to "V"), senderName = "Ali", replyTo = "m0", expiresAt = 99L
    )

    @Test
    fun roundTripKeepsAllFields() {
        val d = doc("m1", 1234L)
        assertEquals(d, MessageCodec.decode("m1", MessageCodec.encode(d)))
    }

    @Test
    fun mergeKeepsOlderLocalMessages() {
        val local = listOf(doc("a", 1), doc("b", 2), doc("c", 3))
        val window = listOf(doc("c", 3), doc("d", 4))
        assertEquals(listOf("a", "b", "c", "d"), LocalMessageStore.merge(local, window, windowIsComplete = false).map { it.id })
    }

    @Test
    fun completeWindowReplacesLocal() {
        val local = listOf(doc("a", 1), doc("b", 2))
        val window = listOf(doc("b", 2))
        assertEquals(listOf("b"), LocalMessageStore.merge(local, window, windowIsComplete = true).map { it.id })
    }
}
