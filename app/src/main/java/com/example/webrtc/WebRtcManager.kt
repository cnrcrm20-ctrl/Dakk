package com.example.webrtc

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Collections

/**
 * Dakk WebRTC P2P Doğrudan İletişim Motoru.
 * - Sadece STUN havuzu (Google ve Mozilla STUN) - TURN VE BLUETOOTH KESİNLİKLE YOKTUR.
 * - Doğrudan WebRTC DataChannel üzerinden şifreli P2P mesajlaşma
 * - ICE durum takibi: NEW, CHECKING, CONNECTED, COMPLETED, FAILED, DISCONNECTED
 * - STUN erişim testi ve gecikme (ms) ölçümü
 * - Türkiye Operatörleri tespiti ve manuel seçim (Turkcell, Vodafone, Türk Telekom, BİMcell, Netgsm)
 * - RTT (Round Trip Time) P2P paket gecikmesi takibi
 */
class WebRtcManager(private val context: Context) {

    companion object {
        private const val TAG = "DakkWebRtc"
        val STUN_SERVERS = listOf(
            "stun:stun.l.google.com:19302",
            "stun:stun1.l.google.com:19302",
            "stun:stun2.l.google.com:19302",
            "stun:stun3.l.google.com:19302",
            "stun:stun4.l.google.com:19302",
            "stun:stun.services.mozilla.com:3478"
        )
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null

    // Sinyalleşme ve bağlantı durumları
    private val _iceConnectionState = MutableStateFlow(PeerConnection.IceConnectionState.NEW)
    val iceConnectionState: StateFlow<PeerConnection.IceConnectionState> = _iceConnectionState

    private val _dataChannelState = MutableStateFlow(DataChannel.State.CLOSED)
    val dataChannelState: StateFlow<DataChannel.State> = _dataChannelState

    private val _gatheredCandidates = Collections.synchronizedList(mutableListOf<IceCandidate>())
    private val _candidateTypes = MutableStateFlow<Set<String>>(emptySet())
    val candidateTypes: StateFlow<Set<String>> = _candidateTypes

    private val _stunLatencyMs = MutableStateFlow(-1L)
    val stunLatencyMs: StateFlow<Long> = _stunLatencyMs

    private val _p2pRttMs = MutableStateFlow(-1L)
    val p2pRttMs: StateFlow<Long> = _p2pRttMs

    private val _selectedOperator = MutableStateFlow(detectOperator())
    val selectedOperator: StateFlow<String> = _selectedOperator

    private val _networkType = MutableStateFlow(detectNetworkType())
    val networkType: StateFlow<String> = _networkType

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    // Mesaj alıcı dinleyicisi
    var onMessageReceived: ((peerId: String, content: String, msgId: String, seq: Long) -> Unit)? = null
    var onDeliveryAckReceived: ((msgId: String) -> Unit)? = null

    init {
        initializeFactory()
        measureStunLatency()
    }

    private fun initializeFactory() {
        try {
            val options = PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(options)

            val factoryOptions = PeerConnectionFactory.Options()
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(factoryOptions)
                .createPeerConnectionFactory()
            Log.d(TAG, "PeerConnectionFactory initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "WebRTC init failed: ${e.message}")
            _errorMessage.value = "WebRTC motoru başlatılamadı: ${e.localizedMessage}"
        }
    }

    fun setSelectedOperator(op: String) {
        _selectedOperator.value = op
    }

    fun detectOperator(): String {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val simOperatorName = telephonyManager?.simOperatorName?.trim() ?: ""
            val networkOperatorName = telephonyManager?.networkOperatorName?.trim() ?: ""
            val name = if (simOperatorName.isNotEmpty()) simOperatorName else networkOperatorName

            when {
                name.contains("Turkcell", ignoreCase = true) -> "Turkcell"
                name.contains("Vodafone", ignoreCase = true) -> "Vodafone"
                name.contains("Telekom", ignoreCase = true) || name.contains("Avea", ignoreCase = true) -> "Türk Telekom"
                name.contains("BIMcell", ignoreCase = true) -> "BİMcell"
                name.contains("Netgsm", ignoreCase = true) -> "Netgsm"
                name.isNotEmpty() -> name
                else -> "Otomatik (Tespit Edilemedi)"
            }
        } catch (e: Exception) {
            "Otomatik / Genel"
        }
    }

    fun detectNetworkType(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork
            val capabilities = cm?.getNetworkCapabilities(network)
            when {
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobil Veri"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                else -> "Bilinmeyen Ağ"
            }
        } catch (e: Exception) {
            "Bilinmeyen Ağ"
        }
    }

    /**
     * Google STUN sunucusuna UDP STUN Binding Request göndererek yanıt süresini (gecikmeyi) ölçer
     */
    fun measureStunLatency() {
        Thread {
            try {
                val start = System.currentTimeMillis()
                val socket = DatagramSocket()
                socket.soTimeout = 2500

                // Basit RFC 5389 STUN Binding Request paketi (20 bytes header)
                // Type: 0x0001 (Binding Request), Length: 0x0000, Magic Cookie: 0x2112A442, Transaction ID: 12 bytes
                val stunPacket = ByteArray(20)
                stunPacket[0] = 0x00
                stunPacket[1] = 0x01 // Binding Request
                stunPacket[2] = 0x00
                stunPacket[3] = 0x00 // Length 0
                stunPacket[4] = 0x21
                stunPacket[5] = 0x12
                stunPacket[6] = 0xA4.toByte()
                stunPacket[7] = 0x42 // Magic Cookie

                for (i in 8 until 20) {
                    stunPacket[i] = (i * 7).toByte()
                }

                val address = InetAddress.getByName("stun.l.google.com")
                val sendPacket = DatagramPacket(stunPacket, stunPacket.size, address, 19302)
                socket.send(sendPacket)

                val receiveBuf = ByteArray(256)
                val receivePacket = DatagramPacket(receiveBuf, receiveBuf.size)
                socket.receive(receivePacket)
                val duration = System.currentTimeMillis() - start
                socket.close()

                mainHandler.post {
                    _stunLatencyMs.value = duration
                    Log.d(TAG, "STUN Latency measured: ${duration}ms")
                }
            } catch (e: Exception) {
                Log.w(TAG, "STUN latency test: ${e.message}")
                mainHandler.post {
                    _stunLatencyMs.value = -1L
                }
            }
        }.start()
    }

    /**
     * Yeni bir doğrudan PeerConnection oluşturur (Kesinlikle STUN bazlı)
     */
    fun createPeerConnection(isInitiator: Boolean): PeerConnection? {
        closePeerConnection()

        val iceServers = STUN_SERVERS.map { url ->
            PeerConnection.IceServer.builder(url).createIceServer()
        }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED // Sadece UDP P2P
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
            iceTransportsType = PeerConnection.IceTransportsType.ALL
        }

        _gatheredCandidates.clear()
        _candidateTypes.value = emptySet()
        _iceConnectionState.value = PeerConnection.IceConnectionState.NEW
        _dataChannelState.value = DataChannel.State.CLOSED
        _errorMessage.value = null

        val observer = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                _gatheredCandidates.add(candidate)
                val type = when {
                    candidate.sdp.contains("typ host") -> "host"
                    candidate.sdp.contains("typ srflx") -> "srflx"
                    candidate.sdp.contains("typ prflx") -> "prflx"
                    candidate.sdp.contains("typ relay") -> "relay"
                    else -> "unknown"
                }
                mainHandler.post {
                    _candidateTypes.value = _candidateTypes.value + type
                }
                Log.d(TAG, "Discovered ICE candidate: $type -> ${candidate.sdpMid}:${candidate.sdpMLineIndex}")
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                Log.d(TAG, "ICE Connection State: $newState")
                mainHandler.post {
                    _iceConnectionState.value = newState
                    if (newState == PeerConnection.IceConnectionState.CONNECTED || newState == PeerConnection.IceConnectionState.COMPLETED) {
                        startRttPingLoop()
                    }
                }
            }

            override fun onDataChannel(dc: DataChannel) {
                Log.d(TAG, "DataChannel received from remote peer")
                setupDataChannel(dc)
            }

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                Log.d(TAG, "ICE Gathering State: $state")
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)

        if (isInitiator) {
            val init = DataChannel.Init().apply {
                ordered = true
                maxRetransmits = -1
                negotiated = false
            }
            val dc = peerConnection?.createDataChannel("dakk-p2p-channel", init)
            if (dc != null) {
                setupDataChannel(dc)
            }
        }

        return peerConnection
    }

    private fun setupDataChannel(dc: DataChannel) {
        dataChannel = dc
        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(amount: Long) {}

            override fun onStateChange() {
                val state = dataChannel?.state() ?: DataChannel.State.CLOSED
                Log.d(TAG, "DataChannel state changed: $state")
                mainHandler.post {
                    _dataChannelState.value = state
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                val data = ByteArray(buffer.data.remaining())
                buffer.data.get(data)
                val jsonString = String(data, StandardCharsets.UTF_8)
                handleIncomingPacket(jsonString)
            }
        })
    }

    private fun handleIncomingPacket(rawJson: String) {
        try {
            val obj = JSONObject(rawJson)
            when (obj.optString("type")) {
                "MSG" -> {
                    val msgId = obj.getString("id")
                    val senderId = obj.getString("senderId")
                    val content = obj.getString("payload")
                    val seq = obj.optLong("seq", 0L)

                    // Otomatik teslim alındı (ACK) paketi dön
                    sendAck(msgId)

                    mainHandler.post {
                        onMessageReceived?.invoke(senderId, content, msgId, seq)
                    }
                }
                "ACK" -> {
                    val ackMsgId = obj.getString("msgId")
                    mainHandler.post {
                        onDeliveryAckReceived?.invoke(ackMsgId)
                    }
                }
                "PING" -> {
                    val ts = obj.optLong("ts")
                    val pong = JSONObject().apply {
                        put("type", "PONG")
                        put("ts", ts)
                    }
                    sendRawPacket(pong.toString())
                }
                "PONG" -> {
                    val ts = obj.optLong("ts")
                    val rtt = System.currentTimeMillis() - ts
                    mainHandler.post {
                        _p2pRttMs.value = rtt
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing incoming packet: ${e.message}")
        }
    }

    private fun sendAck(msgId: String) {
        val ackObj = JSONObject().apply {
            put("type", "ACK")
            put("msgId", msgId)
        }
        sendRawPacket(ackObj.toString())
    }

    private fun startRttPingLoop() {
        val pingObj = JSONObject().apply {
            put("type", "PING")
            put("ts", System.currentTimeMillis())
        }
        sendRawPacket(pingObj.toString())
    }

    /**
     * P2P DataChannel üzerinden mesaj gönderir
     */
    fun sendMessage(msgId: String, senderId: String, encryptedPayload: String, seq: Long): Boolean {
        if (_dataChannelState.value != DataChannel.State.OPEN || dataChannel == null) {
            _errorMessage.value = "DataChannel açık değil! Mesaj gönderilemedi."
            return false
        }

        val packet = JSONObject().apply {
            put("type", "MSG")
            put("id", msgId)
            put("senderId", senderId)
            put("payload", encryptedPayload)
            put("seq", seq)
            put("timestamp", System.currentTimeMillis())
        }
        return sendRawPacket(packet.toString())
    }

    private fun sendRawPacket(data: String): Boolean {
        val dc = dataChannel ?: return false
        if (dc.state() != DataChannel.State.OPEN) return false

        val bytes = data.toByteArray(StandardCharsets.UTF_8)
        val buffer = DataChannel.Buffer(ByteBuffer.wrap(bytes), false)
        return dc.send(buffer)
    }

    /**
     * SDP Offer Oluşturur
     */
    fun createOffer(callback: (sdp: SessionDescription?, candidates: List<IceCandidate>) -> Unit) {
        val pc = createPeerConnection(isInitiator = true) ?: return
        val constraints = MediaConstraints()

        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                pc.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        // Candidate toplama için kısa bir bekleme
                        mainHandler.postDelayed({
                            callback(desc, _gatheredCandidates.toList())
                        }, 1200)
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(error: String?) {
                        Log.e(TAG, "setLocalDescription failure: $error")
                    }
                }, desc)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "createOffer failure: $error")
                _errorMessage.value = "Offer oluşturulamadı: $error"
            }
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    /**
     * Gelen Offer'ı işleyip SDP Answer Oluşturur
     */
    fun createAnswer(
        remoteOfferSdp: String,
        remoteCandidates: List<IceCandidate>,
        callback: (sdp: SessionDescription?, candidates: List<IceCandidate>) -> Unit
    ) {
        val pc = createPeerConnection(isInitiator = false) ?: return
        val offerDesc = SessionDescription(SessionDescription.Type.OFFER, remoteOfferSdp)

        pc.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                // Uzak adayları ekle
                for (cand in remoteCandidates) {
                    pc.addIceCandidate(cand)
                }

                val constraints = MediaConstraints()
                pc.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(answerDesc: SessionDescription) {
                        pc.setLocalDescription(object : SdpObserver {
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onSetSuccess() {
                                mainHandler.postDelayed({
                                    callback(answerDesc, _gatheredCandidates.toList())
                                }, 1200)
                            }
                            override fun onCreateFailure(p0: String?) {}
                            override fun onSetFailure(error: String?) {
                                Log.e(TAG, "setLocalDescription (answer) failure: $error")
                            }
                        }, answerDesc)
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(error: String?) {
                        Log.e(TAG, "createAnswer failure: $error")
                        _errorMessage.value = "Answer oluşturulamadı: $error"
                    }
                    override fun onSetFailure(p0: String?) {}
                }, constraints)
            }
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "setRemoteDescription (offer) failure: $error")
                _errorMessage.value = "Offer SDP atanamadı: $error"
            }
        }, offerDesc)
    }

    /**
     * Answer SDP'sini Initiator tarafına set eder ve doğrudan P2P bağlantısını başlatır
     */
    fun setRemoteAnswer(
        answerSdp: String,
        candidates: List<IceCandidate>,
        onSuccess: () -> Unit
    ) {
        val pc = peerConnection ?: return
        val answerDesc = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)

        pc.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                for (cand in candidates) {
                    pc.addIceCandidate(cand)
                }
                mainHandler.post {
                    onSuccess()
                }
            }
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "setRemoteDescription (answer) failure: $error")
                _errorMessage.value = "Answer uygulanamadı: $error"
            }
        }, answerDesc)
    }

    fun closePeerConnection() {
        try {
            dataChannel?.close()
            dataChannel?.dispose()
            dataChannel = null

            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing connection: ${e.message}")
        }
    }
}
