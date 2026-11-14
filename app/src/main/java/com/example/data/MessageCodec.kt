package com.example.data

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Telefondaki mesaj arşivinin satır biçimi: mesaj belgesi (içerik hâlâ uçtan uca şifreli) kısa anahtarlı
 * JSON'a çevrilip sıkıştırılır. Şifreli metin sıkışmadığı için kazanç çoğunlukla anahtar ve alan adlarındandır.
 */
object MessageCodec {

    fun encode(doc: MessageDoc): ByteArray {
        val o = JSONObject()
            .put("s", doc.senderId)
            .put("c", doc.content)
            .put("sk", doc.senderKey)
            .put("rk", doc.recipientKey)
            .put("d", doc.deliveryStatus)
            .put("t", doc.createdAt)
            .put("y", doc.messageType)
        if (doc.isAssistant) o.put("a", true)
        if (doc.reactions.isNotEmpty()) o.put("re", JSONObject(doc.reactions))
        doc.replyTo?.let { o.put("rt", it) }
        doc.replyToSender?.let { o.put("rs", it) }
        doc.replyPreview?.let { o.put("rp", it) }
        if (doc.keys.isNotEmpty()) o.put("k", JSONObject(doc.keys))
        if (doc.expiresAt > 0) o.put("x", doc.expiresAt)
        doc.systemText?.let { o.put("st", it) }
        if (doc.votes.isNotEmpty()) o.put("v", JSONObject(doc.votes))
        if (doc.senderName.isNotEmpty()) o.put("n", doc.senderName)
        doc.restoreTo?.let { o.put("ro", it) }
        return deflate(o.toString().toByteArray(Charsets.UTF_8))
    }

    fun decode(id: String, bytes: ByteArray): MessageDoc? = try {
        val o = JSONObject(String(inflate(bytes), Charsets.UTF_8))
        MessageDoc(
            id = id,
            senderId = o.optString("s"),
            content = o.optString("c"),
            senderKey = o.optString("sk"),
            recipientKey = o.optString("rk"),
            deliveryStatus = o.optString("d", "SENT"),
            createdAt = o.optLong("t"),
            isPending = false,
            messageType = o.optString("y", "TEXT"),
            isAssistant = o.optBoolean("a"),
            reactions = o.optJSONObject("re").toStringMap(),
            replyTo = o.optString("rt").ifBlank { null },
            replyToSender = o.optString("rs").ifBlank { null },
            replyPreview = o.optString("rp").ifBlank { null },
            keys = o.optJSONObject("k").toStringMap(),
            expiresAt = o.optLong("x"),
            systemText = o.optString("st").ifBlank { null },
            votes = o.optJSONObject("v").toStringMap(),
            senderName = o.optString("n"),
            restoreTo = o.optString("ro").ifBlank { null }
        )
    } catch (_: Exception) {
        null
    }

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        val out = LinkedHashMap<String, String>()
        keys().forEach { k -> optString(k).takeIf { it.isNotEmpty() }?.let { out[k] = it } }
        return out
    }

    private fun deflate(input: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        try {
            deflater.setInput(input)
            deflater.finish()
            val out = ByteArrayOutputStream(input.size / 2 + 16)
            val buf = ByteArray(4096)
            while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun inflate(input: ByteArray): ByteArray {
        val inflater = Inflater(true)
        try {
            inflater.setInput(input)
            val out = ByteArrayOutputStream(input.size * 3)
            val buf = ByteArray(4096)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        } finally {
            inflater.end()
        }
    }
}
