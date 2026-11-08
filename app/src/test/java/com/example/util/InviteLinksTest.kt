package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InviteLinksTest {

    private val uid = "AbCdEfGhIjKlMnOpQrStUvWxYz12"

    @Test
    fun buildAndParseRoundTrip() {
        val link = InviteLinks.build("https://cnrcrm20-ctrl.github.io/Dakk/invite/", uid)
        assertEquals("https://cnrcrm20-ctrl.github.io/Dakk/invite/?u=$uid", link)
        assertEquals(uid, InviteLinks.parseUid(link))
    }

    @Test
    fun parsesCustomSchemeAndRawUid() {
        assertEquals(uid, InviteLinks.parseUid("dakk://add?u=$uid"))
        assertEquals(uid, InviteLinks.parseUid("dakk://invite?uid=$uid&x=1"))
        assertEquals(uid, InviteLinks.parseUid("  $uid  "))
    }

    @Test
    fun rejectsForeignOrInvalidInput() {
        assertNull(InviteLinks.parseUid(null))
        assertNull(InviteLinks.parseUid(""))
        assertNull(InviteLinks.parseUid("https://example.com/?u=$uid"))
        assertNull(InviteLinks.parseUid("dakk://add?u=kisa"))
        assertNull(InviteLinks.parseUid("dakk://add?u=bad/../id1234567890123456"))
    }
}
