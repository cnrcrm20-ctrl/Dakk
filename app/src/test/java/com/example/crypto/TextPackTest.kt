package com.example.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextPackTest {

    private val samples = listOf(
        "",
        "ok",
        "Selam, akşam halı sahaya geliyor musun? Saat 21:00'de kadro tamam mı?",
        "Teşekkür ederim kanka, yarın sabah ararım 🙏😂",
        "{\"v\":1,\"type\":\"image\",\"mediaId\":\"3f1c2a\",\"chunks\":1,\"w\":1280,\"h\":960}",
        "ğüşıöç ĞÜŞİÖÇ ".repeat(40),
        "a".repeat(10_000)
    )

    @Test
    fun roundTrip() {
        samples.forEach { text -> assertEquals(text, TextPack.decode(TextPack.encode(text))) }
    }

    @Test
    fun turkishChatTextShrinks() {
        val text = "Selam kanka, bugün akşam maç var mı? Saat kaçta buluşuyoruz, ben biraz geç kalabilirim."
        val raw = text.toByteArray(Charsets.UTF_8)
        val packed = TextPack.encode(text)
        assertTrue(TextPack.isPacked(packed))
        assertTrue("${packed.size} >= ${raw.size}", packed.size < raw.size)
    }

    @Test
    fun packedLengthIsPadded() {
        val packed = TextPack.encode("Akşam görüşürüz, yarın da maç var unutma lütfen. Herkese selam söyle!")
        if (TextPack.isPacked(packed)) assertEquals(0, packed.size % TextPack.PAD_BLOCK)
    }

    @Test
    fun shortTextStaysRaw() {
        val packed = TextPack.encode("naber")
        assertFalse(TextPack.isPacked(packed))
        assertArrayEquals("naber".toByteArray(Charsets.UTF_8), packed)
    }

    @Test
    fun legacyUtf8IsDecodedAsIs() {
        val legacy = "Eski sürümden gelen mesaj 👋".toByteArray(Charsets.UTF_8)
        assertEquals("Eski sürümden gelen mesaj 👋", TextPack.decode(legacy))
    }

    @Test
    fun bytesRoundTrip() {
        val data = ("geçmiş paketi ".repeat(2_000)).toByteArray(Charsets.UTF_8)
        assertArrayEquals(data, TextPack.inflateBytes(TextPack.deflateBytes(data)))
    }
}
