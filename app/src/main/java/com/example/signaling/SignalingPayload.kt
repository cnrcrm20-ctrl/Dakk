package com.example.signaling

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.IceCandidate
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

data class IceCandidateDto(
    val sdpMid: String,
    val sdpMLineIndex: Int,
    val sdp: String
) {
    fun toWebRtc(): IceCandidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
}

data class SignalingPayload(
    val type: String, // "OFFER" veya "ANSWER"
    val userId: String, // 512 karakterlik kimlik
    val alias: String,
    val publicKey: String,
    val sdp: String,
    val candidates: List<IceCandidateDto>,
    val timestamp: Long = System.currentTimeMillis(),
    val signature: String = ""
) {

    /**
     * JSON olarak paketler, Zlib ile sıkıştırır ve URL-Safe Base64 string üretir.
     * Bu sayede büyük SDP verisi küçülür ve QR koduna ya da davet linkine tek parça sığar.
     */
    fun encodeToCompactString(): String {
        val root = JSONObject().apply {
            put("t", type)
            put("u", userId)
            put("a", alias)
            put("k", publicKey)
            put("s", sdp)
            put("ts", timestamp)
            put("sig", signature)

            val candArray = JSONArray()
            for (c in candidates) {
                val cObj = JSONObject().apply {
                    put("m", c.sdpMid)
                    put("i", c.sdpMLineIndex)
                    put("c", c.sdp)
                }
                candArray.put(cObj)
            }
            put("cands", candArray)
        }

        val jsonBytes = root.toString().toByteArray(StandardCharsets.UTF_8)
        val baos = ByteArrayOutputStream()
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        val deflaterStream = DeflaterOutputStream(baos, deflater)
        deflaterStream.write(jsonBytes)
        deflaterStream.finish()
        deflaterStream.close()

        val compressedBytes = baos.toByteArray()
        return Base64.encodeToString(compressedBytes, Base64.URL_SAFE or Base64.NO_WRAP)
    }

    fun toInvitationLink(): String {
        return "dakk://invite?data=${encodeToCompactString()}"
    }

    companion object {
        fun decodeFromCompactString(compactString: String): SignalingPayload? {
            return try {
                val rawString = compactString.removePrefix("dakk://invite?data=").trim()
                val compressedBytes = Base64.decode(rawString, Base64.URL_SAFE or Base64.NO_WRAP)

                val bais = ByteArrayInputStream(compressedBytes)
                val inflaterStream = InflaterInputStream(bais)
                val decompressedBytes = inflaterStream.readBytes()
                inflaterStream.close()

                val jsonString = String(decompressedBytes, StandardCharsets.UTF_8)
                val root = JSONObject(jsonString)

                val cands = mutableListOf<IceCandidateDto>()
                val candArray = root.optJSONArray("cands")
                if (candArray != null) {
                    for (i in 0 until candArray.length()) {
                        val cObj = candArray.getJSONObject(i)
                        cands.add(
                            IceCandidateDto(
                                sdpMid = cObj.getString("m"),
                                sdpMLineIndex = cObj.getInt("i"),
                                sdp = cObj.getString("c")
                            )
                        )
                    }
                }

                SignalingPayload(
                    type = root.getString("t"),
                    userId = root.getString("u"),
                    alias = root.optString("a", "Bilinmeyen"),
                    publicKey = root.getString("k"),
                    sdp = root.getString("s"),
                    candidates = cands,
                    timestamp = root.optLong("ts", System.currentTimeMillis()),
                    signature = root.optString("sig", "")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
