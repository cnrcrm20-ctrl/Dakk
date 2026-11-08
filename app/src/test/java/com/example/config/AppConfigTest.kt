package com.example.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConfigTest {

    @Test
    fun defaultsWhenDocumentMissing() {
        val c = PublicConfig.fromMap(null)
        assertTrue(c.assistantEnabled)
        assertEquals(PublicConfig.PROVIDER_GEMINI, c.assistantProvider)
        assertEquals(PublicConfig.DEFAULT_MODEL, c.assistantModel)
        assertTrue(c.imagesEnabled)
        assertFalse(c.maintenanceEnabled)
        assertFalse(c.hasServer)
        assertNull(c.announcement)
    }

    @Test
    fun parsesFirestoreValues() {
        val c = PublicConfig.fromMap(
            mapOf(
                "assistant_enabled" to false,
                "assistant_model" to "",
                "assistant_daily_limit" to 12L,
                "server_url" to " https://dakk.example.workers.dev/ ",
                "min_supported_version" to "2.1.0",
                "announcement" to mapOf("id" to "a1", "active" to true, "title" to "Merhaba", "end_at" to 5L)
            )
        )
        assertFalse(c.assistantEnabled)
        assertEquals(PublicConfig.DEFAULT_MODEL, c.assistantModel)
        assertEquals(12, c.assistantDailyLimit)
        assertEquals("https://dakk.example.workers.dev", c.serverUrl)
        assertTrue(c.hasServer)
        assertEquals("a1", c.announcement?.id)
        assertEquals(5L, c.announcement?.endAt)
    }

    @Test
    fun forceUpdateComparesVersions() {
        val c = PublicConfig(minSupportedVersion = "2.1.0")
        assertTrue(c.requiresUpdate("2.0.1"))
        assertFalse(c.requiresUpdate("2.1.0"))
        assertFalse(c.requiresUpdate("2.2.0"))
        assertFalse(PublicConfig().requiresUpdate("1.0.0"))
    }

    @Test
    fun announcementVisibilityRules() {
        val a = Announcement(id = "a1", active = true, title = "Duyuru", startAt = 100, endAt = 200, minVersion = "2.1.0")
        assertTrue(a.isLive(150, "2.1.0"))
        assertFalse(a.isLive(50, "2.1.0"))
        assertFalse(a.isLive(250, "2.1.0"))
        assertFalse(a.isLive(150, "2.0.9"))
        assertFalse(a.copy(active = false).isLive(150, "2.1.0"))
        assertFalse(a.copy(title = "").isLive(150, "2.1.0"))
        assertTrue(Announcement(id = "x", active = true, body = "b").isLive(System.currentTimeMillis(), "1.0"))
    }

    @Test
    fun announcementRoundTrip() {
        val a = Announcement(
            id = "a9", active = true, title = "T", body = "B", imageUrl = "https://i", buttonText = "Git",
            buttonUrl = "https://x", startAt = 1, endAt = 2, minVersion = "2.1", dismissible = false
        )
        assertEquals(a, Announcement.fromMap(a.toMap()))
    }

    @Test
    fun privateConfigNeedsHttpsKeyAndModel() {
        assertTrue(PrivateConfig("https://api.x/v1", "k", "m").isUsable)
        assertFalse(PrivateConfig("http://api.x/v1", "k", "m").isUsable)
        assertFalse(PrivateConfig("https://api.x/v1", "", "m").isUsable)
    }
}
