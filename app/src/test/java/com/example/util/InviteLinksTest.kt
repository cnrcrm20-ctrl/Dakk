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

    @Test
    fun groupInviteRoundTrip() {
        val link = InviteLinks.buildGroup("https://cnrcrm20-ctrl.github.io/Dakk/invite/", "g_AbCdEfGhIjKlMnOpQrSt", "Xy7Kp2Qm9Rt3Lw8N")
        assertEquals("https://cnrcrm20-ctrl.github.io/Dakk/invite/?g=g_AbCdEfGhIjKlMnOpQrSt&c=Xy7Kp2Qm9Rt3Lw8N", link)
        assertEquals(InviteLinks.GroupInvite("g_AbCdEfGhIjKlMnOpQrSt", "Xy7Kp2Qm9Rt3Lw8N"), InviteLinks.parseGroup(link))
        assertEquals(
            InviteLinks.GroupInvite("g_AbCdEfGhIjKlMnOpQrSt", "Xy7Kp2Qm9Rt3Lw8N"),
            InviteLinks.parseGroup("dakk://invite?g=g_AbCdEfGhIjKlMnOpQrSt&c=Xy7Kp2Qm9Rt3Lw8N")
        )
        // Kişi davet bağlantısı grup daveti sayılmaz (ve tersi).
        assertNull(InviteLinks.parseGroup("dakk://add?u=$uid"))
        assertNull(InviteLinks.parseUid(link))
    }

    @Test
    fun rejectsInvalidGroupInvites() {
        assertNull(InviteLinks.parseGroup(null))
        assertNull(InviteLinks.parseGroup("https://example.com/?g=g_AbCdEfGhIjKlMnOpQrSt&c=Xy7Kp2Qm9Rt3Lw8N"))
        assertNull(InviteLinks.parseGroup("dakk://invite?g=AbCdEfGhIjKlMnOpQrSt&c=Xy7Kp2Qm9Rt3Lw8N"))
        assertNull(InviteLinks.parseGroup("dakk://invite?g=g_AbCdEfGhIjKlMnOpQrSt&c=kisa"))
        assertNull(InviteLinks.parseGroup("dakk://invite?g=g_AbCdEfGhIjKlMnOpQrSt"))
    }
}
