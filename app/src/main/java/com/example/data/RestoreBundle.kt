package com.example.data

import com.example.crypto.TextPack
import org.json.JSONArray
import org.json.JSONObject

/**
 * Arkadaştan geri yükleme paketi (2.5.0+).
 *
 * Bir kişi Dakk'ı yeniden kurunca eski anahtarı kaybolur ve geçmiş mesajları açamaz. Sohbet yedeği açıksa
 * arkadaşının telefonu, kendi arşivindeki ortak sohbeti çözüp bu pakete koyar; paket sıkıştırılır, kişinin
 * YENİ anahtarıyla şifrelenir ve fotoğraf deposuna geçici olarak yüklenir. Sunucu içeriği göremez.
 */
object RestoreBundle {

    const val VERSION = 1
    /** Fotoğraf deposunun tek dosya sınırı 2 MB; şifreleme payı bırakılır. */
    const val MAX_PACKED_BYTES = 1_900_000
    const val MAX_ENTRIES = 5_000
    /** Geri yükleme isteği bu süreden eskiyse yanıtlanmaz. */
    const val REQUEST_TTL_MS = 14L * 24 * 60 * 60 * 1000

    data class Entry(
        val id: String,
        val senderId: String,
        val senderName: String,
        val createdAt: Long,
        /** "T" metin, "S" sistem notu. */
        val kind: String,
        val text: String
    )

    data class Pointer(val mediaId: String, val count: Int)

    fun encode(chatId: String, entries: List<Entry>): ByteArray {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject()
                    .put("i", e.id)
                    .put("s", e.senderId)
                    .put("n", e.senderName)
                    .put("t", e.createdAt)
                    .put("y", e.kind)
                    .put("x", e.text)
            )
        }
        val json = JSONObject().put("v", VERSION).put("c", chatId).put("m", arr).toString()
        return TextPack.deflateBytes(json.toByteArray(Charsets.UTF_8))
    }

    /** En yeni mesajlardan başlayarak sınıra sığan en büyük paketi üretir. */
    fun encodeWithinLimit(chatId: String, entries: List<Entry>, limit: Int = MAX_PACKED_BYTES): Pair<ByteArray, Int>? {
        var take = minOf(entries.size, MAX_ENTRIES)
        while (take > 0) {
            val slice = entries.takeLast(take)
            val packed = encode(chatId, slice)
            if (packed.size <= limit) return packed to slice.size
            take = take * 2 / 3
        }
        return null
    }

    fun decode(bytes: ByteArray): Pair<String, List<Entry>>? = try {
        val o = JSONObject(String(TextPack.inflateBytes(bytes), Charsets.UTF_8))
        if (o.optInt("v") != VERSION) null else {
            val arr = o.optJSONArray("m") ?: JSONArray()
            val out = ArrayList<Entry>(arr.length())
            for (i in 0 until minOf(arr.length(), MAX_ENTRIES)) {
                val e = arr.optJSONObject(i) ?: continue
                val id = e.optString("i")
                val kind = e.optString("y")
                if (id.isBlank() || id.length > 64 || (kind != "T" && kind != "S")) continue
                out += Entry(
                    id = id,
                    senderId = e.optString("s").take(128),
                    senderName = e.optString("n").take(60),
                    createdAt = e.optLong("t"),
                    kind = kind,
                    text = e.optString("x").take(5_000)
                )
            }
            o.optString("c") to out
        }
    } catch (_: Exception) {
        null
    }

    fun encodePointer(p: Pointer): String = JSONObject().put("r", VERSION).put("m", p.mediaId).put("c", p.count).toString()

    fun decodePointer(json: String?): Pointer? = try {
        val o = JSONObject(json ?: "")
        val id = o.optString("m")
        if (o.optInt("r") != VERSION || !id.matches(Regex("[A-Za-z0-9_-]{8,80}"))) null else Pointer(id, o.optInt("c"))
    } catch (_: Exception) {
        null
    }

    /** Bu profil için geçmiş gönderilmeli mi? */
    fun shouldServe(profile: UserProfile, lastServedAt: Long, myBackupEnabled: Boolean, now: Long): Boolean =
        myBackupEnabled && profile.caps >= 1 && profile.backup && profile.publicKey.isNotBlank() &&
            profile.restoreAt > lastServedAt && profile.restoreAt <= now + 60_000 &&
            now - profile.restoreAt < REQUEST_TTL_MS
}

/**
 * Sunucu yalnızca posta kutusudur (2.5.0+): teslim edilen mesajlar telefonlarda saklanır, gönderen
 * tarafından sunucudan kaldırılır.
 */
object Mailbox {

    const val READ_GRACE_MS = 24L * 60 * 60 * 1000
    const val GROUP_TTL_MS = 30L * 24 * 60 * 60 * 1000
    const val NOTE_TTL_MS = 7L * 24 * 60 * 60 * 1000
    const val TOMBSTONE_TTL_MS = 30L * 24 * 60 * 60 * 1000

    /**
     * Sunucudan kaldırılabilecek kendi mesajlarım. Anketler oyları üzerinde taşıdığı için kalır; birebir
     * sohbette mesaj okunduktan 24 saat sonra (tepki bırakılabilsin diye), gruplarda 30 gün sonra kaldırılır.
     */
    fun sweepable(docs: List<MessageDoc>, myUid: String, isGroup: Boolean, now: Long): List<String> =
        docs.filter { d ->
            if (d.senderId != myUid || d.isPending) return@filter false
            val age = now - d.createdAt
            when {
                d.deleted -> age >= TOMBSTONE_TTL_MS
                d.messageType == "POLL" -> false
                d.messageType == "SYSTEM" -> age >= NOTE_TTL_MS
                isGroup -> age >= GROUP_TTL_MS
                else -> d.deliveryStatus == "READ" && age >= READ_GRACE_MS
            }
        }.map { it.id }

    /**
     * Birebir sohbette bana gelen ve telefonuma kaydedilmiş mesajlar: gönderen uzun süre çevrimdışı kalsa da
     * sunucuda birikmesinler diye okunduktan 3 gün sonra (gönderen önce kendisi kaldırabilsin) silinebilir.
     * Anketler ve silinmiş mesaj kayıtları gönderene bırakılır.
     */
    fun receivedSweepable(docs: List<MessageDoc>, myUid: String, now: Long): List<String> =
        docs.filter { d ->
            if (d.senderId == myUid || d.isPending || d.deleted) return@filter false
            val age = now - d.createdAt
            when (d.messageType) {
                "POLL" -> false
                "SYSTEM", "SCREENSHOT" -> age >= NOTE_TTL_MS
                else -> d.deliveryStatus == "READ" && age >= RECEIVED_GRACE_MS
            }
        }.map { it.id }

    const val RECEIVED_GRACE_MS = 3L * 24 * 60 * 60 * 1000
}
