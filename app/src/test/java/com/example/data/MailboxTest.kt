package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MailboxTest {

    private val now = 2_000_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun doc(
        id: String,
        sender: String = "me",
        ageMs: Long,
        status: String = "SENT",
        type: String = "TEXT",
        deleted: Boolean = false,
        pending: Boolean = false
    ) = MessageDoc(id, sender, "x", "k1", "k2", status, now - ageMs, pending, messageType = type, deleted = deleted)

    @Test
    fun directChatRemovesReadAfterADay() {
        val docs = listOf(
            doc("read_old", ageMs = 2 * day, status = "READ"),
            doc("read_new", ageMs = day / 2, status = "READ"),
            doc("unread", ageMs = 10 * day),
            doc("theirs", sender = "peer", ageMs = 10 * day, status = "READ"),
            doc("pending", ageMs = 10 * day, status = "READ", pending = true),
            doc("poll", ageMs = 60 * day, status = "READ", type = "POLL")
        )
        assertEquals(listOf("read_old"), Mailbox.sweepable(docs, "me", isGroup = false, now = now))
    }

    @Test
    fun groupsNotesAndTombstones() {
        val docs = listOf(
            doc("g_old", ageMs = 31 * day),
            doc("g_new", ageMs = 5 * day, status = "READ"),
            doc("note_old", ageMs = 8 * day, type = "SYSTEM"),
            doc("note_new", ageMs = 2 * day, type = "SYSTEM"),
            doc("tomb_old", ageMs = 31 * day, deleted = true),
            doc("tomb_new", ageMs = 3 * day, deleted = true)
        )
        assertEquals(listOf("g_old", "note_old", "tomb_old"), Mailbox.sweepable(docs, "me", isGroup = true, now = now))
    }

    @Test
    fun receivedMessagesAfterThreeDays() {
        val docs = listOf(
            doc("r_old", sender = "peer", ageMs = 4 * day, status = "READ"),
            doc("r_new", sender = "peer", ageMs = 2 * day, status = "READ"),
            doc("r_unread", sender = "peer", ageMs = 9 * day),
            doc("r_note", sender = "peer", ageMs = 8 * day, type = "SYSTEM"),
            doc("r_poll", sender = "peer", ageMs = 30 * day, status = "READ", type = "POLL"),
            doc("r_tomb", sender = "peer", ageMs = 30 * day, status = "READ", deleted = true),
            doc("mine", ageMs = 30 * day, status = "READ")
        )
        assertEquals(listOf("r_old", "r_note"), Mailbox.receivedSweepable(docs, "me", now))
    }
}
