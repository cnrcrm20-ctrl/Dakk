package com.example.util

import com.example.ui.components.ChatWallpapers
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatWallpapersTest {

    @Test
    fun perChatOverridesDefault() {
        val w = ChatWallpapers().with(null, "ocean").with("c1", "forest")
        assertEquals("ocean", w.forChat("c2"))
        assertEquals("forest", w.forChat("c1"))
        assertEquals("ocean", w.forChat(null))
    }

    @Test
    fun applyingToAllClearsPerChat() {
        val w = ChatWallpapers().with("c1", "forest").with(null, "night")
        assertEquals("night", w.forChat("c1"))
        assertEquals(emptyMap<String, String>(), w.perChat)
    }

    @Test
    fun unknownIdsFallBackToNone() {
        assertEquals(ChatWallpapers.NONE, ChatWallpapers().with("c1", "lava").forChat("c1"))
        assertEquals(ChatWallpapers.NONE, ChatWallpapers.load("""{"d":"lava","c":{"c1":"???"}}""").forChat("c1"))
        assertEquals(ChatWallpapers(), ChatWallpapers.load("bozuk"))
    }

    @Test
    fun jsonRoundTrip() {
        val w = ChatWallpapers().with(null, "dots").with("c1", "candy").with("c2", "sunrise")
        assertEquals(w, ChatWallpapers.load(w.toJson()))
    }
}
