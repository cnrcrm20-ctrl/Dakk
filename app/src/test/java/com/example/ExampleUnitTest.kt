package com.example

import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom

class ExampleUnitTest {

    @Test
    fun test512CharacterIdGeneration() {
        val pool = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val random = SecureRandom()
        val sb = StringBuilder(512)
        for (i in 0 until 512) {
            sb.append(pool[random.nextInt(pool.length)])
        }
        val id = sb.toString()

        assertEquals("Kullanıcı ID tam 512 karakter olmalıdır", 512, id.length)
        assertTrue("Kullanıcı ID sadece a-z, A-Z, 0-9 içermelidir", id.matches(Regex("^[a-zA-Z0-9]{512}$")))
    }

    @Test
    fun testVersionComparison() {
        fun isVersionNewer(remote: String, local: String): Boolean {
            val rParts = remote.split(".").mapNotNull { it.toIntOrNull() }
            val lParts = local.split(".").mapNotNull { it.toIntOrNull() }
            for (i in 0 until minOf(rParts.size, lParts.size)) {
                if (rParts[i] > lParts[i]) return true
                if (rParts[i] < lParts[i]) return false
            }
            return rParts.size > lParts.size
        }

        assertTrue(isVersionNewer("1.0.1", "1.0.0"))
        assertTrue(isVersionNewer("2.0.0", "1.9.9"))
        assertFalse(isVersionNewer("1.0.0", "1.0.0"))
        assertFalse(isVersionNewer("0.9.5", "1.0.0"))
    }
}
