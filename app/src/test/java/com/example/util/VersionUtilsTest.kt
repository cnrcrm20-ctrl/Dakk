package com.example.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionUtilsTest {

    @Test
    fun detectsNewerVersions() {
        assertTrue(VersionUtils.isNewer("v2.0.1", "2.0.0"))
        assertTrue(VersionUtils.isNewer("2.1", "2.0.9"))
        assertTrue(VersionUtils.isNewer("3.0.0", "2.9.9"))
        assertTrue(VersionUtils.isNewer("2.0.0.1", "2.0.0"))
    }

    @Test
    fun ignoresSameOrOlderVersions() {
        assertFalse(VersionUtils.isNewer("v2.0.0", "2.0.0"))
        assertFalse(VersionUtils.isNewer("2.0", "2.0.0"))
        assertFalse(VersionUtils.isNewer("1.0.11", "2.0.0"))
        assertFalse(VersionUtils.isNewer("", "2.0.0"))
        assertFalse(VersionUtils.isNewer("2.0.0-beta", "2.0.0"))
    }
}
