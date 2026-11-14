package com.example.push

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * "Yazıyor…" sinyali.
 *
 * Sinyaller Firestore'a yazılmaz (kota harcamaz); Dakk sunucusunun anlık yayın kanalı (Supabase Realtime)
 * üzerinden yalnızca o an uygulaması açık olan sohbet üyelerine iletilir ve hiçbir yerde saklanmaz.
 * Kanal adları sohbetin gizli anahtarından türetildiği için sohbet dışındaki biri kanalı bulamaz.
 * Sinyal yalnızca "yazıyor / bıraktı" bilgisini ve gönderenin kullanıcı kimliğini taşır; mesaj içeriği taşımaz.
 */
class TypingClient(private val scope: CoroutineScope) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val lock = Any()
    private var socket: WebSocket? = null
    private var open = false
    private var active = false
    private var info: PushClient.RealtimeInfo? = null
    private var myUid: String = ""
    private var ref = 0
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var pruneJob: Job? = null
    private var failures = 0

    /** Sohbet kimliği → kanal adı (katılmak istenen). */
    private var wanted: Map<String, String> = emptyMap()
    /** Kanal adı → katılım referansı (katılınan). */
    private val joined = HashMap<String, String>()
    /** Sohbet kimliği → (son durum, gönderim zamanı). */
    private val lastSent = HashMap<String, Pair<Boolean, Long>>()

    private val _typing = MutableStateFlow<Map<String, Map<String, Long>>>(emptyMap())
    /** Sohbet kimliği → (yazan kişi kimliği → sinyalin geçerlilik sonu). */
    val typing: StateFlow<Map<String, Map<String, Long>>> = _typing

    fun configure(info: PushClient.RealtimeInfo?, uid: String?) {
        synchronized(lock) {
            val changed = info != this.info || uid.orEmpty() != myUid
            this.info = info
            myUid = uid.orEmpty()
            if (changed) {
                closeLocked()
                _typing.value = emptyMap()
            }
            ensureConnectionLocked()
        }
    }

    /** Uygulama ön plandayken bağlı kalır; arka plana geçince bağlantı kapanır. */
    fun setActive(value: Boolean) {
        synchronized(lock) {
            active = value
            if (!value) {
                closeLocked()
                _typing.value = emptyMap()
            } else {
                ensureConnectionLocked()
            }
        }
    }

    /** Dinlenecek sohbetleri günceller: sohbet kimliği → kanal adı. */
    fun setTopics(topics: Map<String, String>) {
        synchronized(lock) {
            wanted = topics
            if (!open) return
            val wantedTopics = topics.values.toSet()
            joined.keys.filter { it !in wantedTopics }.forEach { leaveLocked(it) }
            wantedTopics.filter { it !in joined }.forEach { joinLocked(it) }
        }
    }

    /**
     * Bu kişinin yazma durumunu sohbete bildirir. Yazarken en fazla 3 saniyede bir sinyal gönderilir;
     * yazma bitince tek bir "bıraktı" sinyali gider.
     */
    fun sendTyping(chatId: String, typing: Boolean) {
        synchronized(lock) {
            val topic = wanted[chatId] ?: return
            if (!open || topic !in joined) return
            val now = System.currentTimeMillis()
            val last = lastSent[chatId]
            if (typing) {
                if (last != null && last.first && now - last.second < RESEND_MS) return
            } else {
                if (last == null || !last.first) return
            }
            lastSent[chatId] = typing to now
            val payload = JSONObject()
                .put("type", "broadcast")
                .put("event", EVENT)
                .put("payload", JSONObject().put("u", myUid).put("s", if (typing) 1 else 0))
            sendLocked(topic, "broadcast", payload, joined[topic])
        }
    }

    // ------------------------------------------------------------------ Bağlantı

    private fun ensureConnectionLocked() {
        if (!active || socket != null || myUid.isBlank()) return
        val i = info ?: return
        val wsUrl = i.url.trimEnd('/').replaceFirst("https://", "wss://") +
            "/realtime/v1/websocket?apikey=" + i.publicKey + "&vsn=1.0.0"
        val request = Request.Builder().url(wsUrl).build()
        socket = http.newWebSocket(request, Listener())
    }

    private fun closeLocked() {
        heartbeatJob?.cancel(); heartbeatJob = null
        reconnectJob?.cancel(); reconnectJob = null
        socket?.close(1000, null)
        socket = null
        open = false
        joined.clear()
        lastSent.clear()
    }

    private fun nextRef(): String = (++ref).toString()

    private fun sendLocked(topic: String, event: String, payload: JSONObject, joinRef: String?) {
        val msg = JSONObject()
            .put("topic", if (topic == "phoenix") topic else "realtime:$topic")
            .put("event", event)
            .put("payload", payload)
            .put("ref", nextRef())
        if (joinRef != null) msg.put("join_ref", joinRef)
        socket?.send(msg.toString())
    }

    private fun joinLocked(topic: String) {
        val joinRef = nextRef()
        joined[topic] = joinRef
        val config = JSONObject()
            .put("broadcast", JSONObject().put("self", false).put("ack", false))
            .put("presence", JSONObject().put("key", ""))
            .put("private", false)
        val payload = JSONObject().put("config", config)
        info?.publicKey?.let { payload.put("access_token", it) }
        sendLocked(topic, "phx_join", payload, joinRef)
    }

    private fun leaveLocked(topic: String) {
        val joinRef = joined.remove(topic) ?: return
        sendLocked(topic, "phx_leave", JSONObject(), joinRef)
    }

    private fun scheduleReconnectLocked() {
        socket = null
        open = false
        joined.clear()
        heartbeatJob?.cancel(); heartbeatJob = null
        if (!active || reconnectJob?.isActive == true) return
        val wait = (2_000L shl failures.coerceAtMost(5)).coerceAtMost(60_000L)
        failures++
        reconnectJob = scope.launch {
            delay(wait)
            synchronized(lock) {
                reconnectJob = null
                ensureConnectionLocked()
            }
        }
    }

    private fun onBroadcast(topic: String, payload: JSONObject) {
        if (payload.optString("event") != EVENT) return
        val inner = payload.optJSONObject("payload") ?: return
        val uid = inner.optString("u")
        if (uid.isBlank() || uid == myUid || uid.length > 128) return
        val chatId = synchronized(lock) { wanted.entries.firstOrNull { it.value == topic }?.key } ?: return
        val typingNow = inner.optInt("s") == 1
        val now = System.currentTimeMillis()
        val current = _typing.value
        val forChat = current[chatId].orEmpty()
        val updated = if (typingNow) forChat + (uid to now + TTL_MS) else forChat - uid
        _typing.value = if (updated.isEmpty()) current - chatId else current + (chatId to updated)
        if (typingNow) ensurePruning()
    }

    /** Süresi dolan sinyalleri temizler (karşı taraf "bıraktı" gönderemeden çıkarsa). */
    private fun ensurePruning() {
        if (pruneJob?.isActive == true) return
        pruneJob = scope.launch {
            while (isActive) {
                delay(1_000)
                val now = System.currentTimeMillis()
                val current = _typing.value
                if (current.isEmpty()) break
                val pruned = current.mapValues { (_, m) -> m.filterValues { it > now } }.filterValues { it.isNotEmpty() }
                if (pruned != current) _typing.value = pruned
            }
        }
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            synchronized(lock) {
                if (webSocket !== socket) return
                open = true
                failures = 0
                wanted.values.toSet().forEach { joinLocked(it) }
                heartbeatJob?.cancel()
                heartbeatJob = scope.launch {
                    while (isActive) {
                        delay(HEARTBEAT_MS)
                        synchronized(lock) {
                            if (webSocket === socket && open) sendLocked("phoenix", "heartbeat", JSONObject(), null)
                        }
                    }
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (webSocket !== socket) return
            val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
            val topic = msg.optString("topic").removePrefix("realtime:")
            when (msg.optString("event")) {
                "broadcast" -> msg.optJSONObject("payload")?.let { onBroadcast(topic, it) }
                "phx_error" -> synchronized(lock) {
                    // Kanal sunucu tarafında hata verdiyse biraz bekleyip yeniden katıl.
                    if (joined.remove(topic) != null) {
                        scope.launch {
                            delay(5_000)
                            synchronized(lock) {
                                if (webSocket === socket && open && topic in wanted.values && topic !in joined) joinLocked(topic)
                            }
                        }
                    }
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            synchronized(lock) { if (webSocket === socket) scheduleReconnectLocked() }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "Anlık kanal bağlantısı koptu: ${t.message}")
            synchronized(lock) { if (webSocket === socket) scheduleReconnectLocked() }
        }
    }

    companion object {
        private const val TAG = "DakkTyping"
        private const val EVENT = "typing"
        private const val HEARTBEAT_MS = 25_000L
        private const val RESEND_MS = 3_000L
        /** Yeni sinyal gelmezse "yazıyor…" bu süre sonra kendiliğinden kaybolur. */
        const val TTL_MS = 6_000L
    }
}
