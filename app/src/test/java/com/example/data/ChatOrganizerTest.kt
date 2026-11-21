package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatOrganizerTest {

    private fun chat(id: String, time: Long) = ChatSummary(
        chatId = id, peerUid = id, peerName = id, peerPhotoUrl = "", preview = "",
        lastMessageTime = time, lastFromMe = false, isUnread = false
    )

    // Sohbetler son mesaja göre sıralı gelir (en yeni başta).
    private val chats = listOf(chat("a", 50), chat("b", 40), chat("c", 30), chat("d", 20), chat("e", 10))

    @Test
    fun pinnedGoOnTopInPinOrder() {
        val org = ChatOrganizer().pin("d")!!.pin("b")!!
        val lists = org.organize(chats)
        assertEquals(listOf("d", "b", "a", "c", "e"), lists.main.map { it.chatId })
        assertEquals(listOf(true, true, false, false, false), lists.main.map { it.isPinned })
    }

    @Test
    fun pinLimitIsThree() {
        val org = ChatOrganizer().pin("a")!!.pin("b")!!.pin("c")!!
        assertNull(org.pin("d"))
        assertEquals(org, org.pin("a"))
        assertEquals(listOf("a", "c"), org.unpin("b").pinned)
    }

    @Test
    fun archivedAndHiddenLeaveMainList() {
        val org = ChatOrganizer().archive("b").hide("c")
        val lists = org.organize(chats)
        assertEquals(listOf("a", "d", "e"), lists.main.map { it.chatId })
        assertEquals(listOf("b"), lists.archived.map { it.chatId })
        assertEquals(listOf("c"), lists.hidden.map { it.chatId })
    }

    @Test
    fun hidingRemovesPinAndArchive() {
        val org = ChatOrganizer().pin("a")!!.archive("b").hide("a").hide("b")
        assertFalse(org.isPinned("a"))
        assertFalse(org.isArchived("b"))
        assertTrue(org.isHidden("a") && org.isHidden("b"))
        val back = org.unhide("a")
        assertEquals(listOf("a", "d", "e", "c").sorted(), back.organize(chats).main.map { it.chatId }.sorted())
    }

    @Test
    fun archivingUnpinsAndPinningUnarchives() {
        val org = ChatOrganizer().pin("a")!!.archive("a")
        assertFalse(org.isPinned("a"))
        assertTrue(org.isArchived("a"))
        val pinned = org.pin("a")!!
        assertTrue(pinned.isPinned("a"))
        assertFalse(pinned.isArchived("a"))
    }

    @Test
    fun retainOnlyDropsVanishedChats() {
        val org = ChatOrganizer(pinned = listOf("a", "x"), archived = setOf("b", "y"), hidden = setOf("c", "z"))
        val kept = org.retainOnly(setOf("a", "b", "c"))
        assertEquals(ChatOrganizer(listOf("a"), setOf("b"), setOf("c")), kept)
        assertTrue(kept === kept.retainOnly(setOf("a", "b", "c")))
    }
}
