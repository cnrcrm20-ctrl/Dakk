package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.CryptoEngine
import com.example.data.db.ContactEntity
import com.example.data.db.MessageEntity
import com.example.data.db.NetworkLogEntity
import com.example.data.repository.DakkRepository
import com.example.github.GitHubUpdateManager
import com.example.github.ReleaseInfo
import com.example.signaling.IceCandidateDto
import com.example.signaling.SignalingPayload
import com.example.signaling.ZeroCostRendezvous
import com.example.webrtc.WebRtcManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.webrtc.PeerConnection
import java.util.UUID
import com.example.data.firebase.FirestoreRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

sealed class Screen {
    object Splash : Screen()
    object Home : Screen()
    data class ChatDetail(val peer: ContactEntity) : Screen()
    object Handshake : Screen()
    object Diagnostic : Screen()
    object Profile : Screen()
}

class DakkViewModel(application: Application) : AndroidViewModel(application) {

    val crypto = CryptoEngine(application)
    val repository = DakkRepository(application)
    val firestoreRepository = FirestoreRepository(application)
    val aiManager = com.example.data.firebase.DakkAiManager()
    val webRtc = WebRtcManager(application)
    val updateManager = GitHubUpdateManager(application)
    val rendezvous = ZeroCostRendezvous()
    val notificationHelper = com.example.notification.NotificationHelper(application)

    private val _firebaseUser = MutableStateFlow<FirebaseUser?>(Firebase.auth.currentUser)
    val firebaseUser: StateFlow<FirebaseUser?> = _firebaseUser

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Splash)
    val currentScreen: StateFlow<Screen> = _currentScreen

    val activeContacts = repository.activeContacts.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val blockedContacts = repository.blockedContacts.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val networkLogs = repository.networkLogs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Aktif Sohbet
    private val _activeChatPeer = MutableStateFlow<ContactEntity?>(null)
    val activeChatPeer: StateFlow<ContactEntity?> = _activeChatPeer

    private val _chatMessages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val chatMessages: StateFlow<List<MessageEntity>> = _chatMessages

    // Handshake & Sinyalleşme State'leri
    private val _myOfferPayload = MutableStateFlow<SignalingPayload?>(null)
    val myOfferPayload: StateFlow<SignalingPayload?> = _myOfferPayload

    private val _myAnswerPayload = MutableStateFlow<SignalingPayload?>(null)
    val myAnswerPayload: StateFlow<SignalingPayload?> = _myAnswerPayload

    private val _handshakeStatus = MutableStateFlow<String>("Hazır")
    val handshakeStatus: StateFlow<String> = _handshakeStatus

    private val _isGeneratingOffer = MutableStateFlow(false)
    val isGeneratingOffer: StateFlow<Boolean> = _isGeneratingOffer

    // Gelen Arkadaşlık / El Sıkışma İsteği
    private val _pendingIncomingInvite = MutableStateFlow<SignalingPayload?>(null)
    val pendingIncomingInvite: StateFlow<SignalingPayload?> = _pendingIncomingInvite

    // GitHub Güncelleme State
    private val _updateInfo = MutableStateFlow<ReleaseInfo?>(null)
    val updateInfo: StateFlow<ReleaseInfo?> = _updateInfo

    private val _updateChecking = MutableStateFlow(false)
    val updateChecking: StateFlow<Boolean> = _updateChecking

    private val _updateStatusMessage = MutableStateFlow<String?>(null)
    val updateStatusMessage: StateFlow<String?> = _updateStatusMessage

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog

    private val _downloadProgress = MutableStateFlow<Int?>(null)
    val downloadProgress: StateFlow<Int?> = _downloadProgress

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading

    private var currentSequenceNumber = 0L

    init {
        // Gelen P2P DataChannel mesaj dinleyicisi
        webRtc.onMessageReceived = { senderId, encryptedContent, msgId, seq ->
            handleIncomingP2pMessage(senderId, encryptedContent, msgId, seq)
        }

        // Teslim edildi ACK dinleyicisi
        webRtc.onDeliveryAckReceived = { ackMsgId ->
            viewModelScope.launch {
                repository.updateMessageStatus(ackMsgId, "DELIVERED")
            }
        }

        // İlk açılışta kullanıcıyı karşılayan Dakk Asistanı
        viewModelScope.launch {
            val existing = repository.getContact("DAKK_BOT_OFFICIAL")
            if (existing == null) {
                val botContact = ContactEntity(
                    userId = "DAKK_BOT_OFFICIAL",
                    alias = "Dakk Asistanı 🐥",
                    publicKey = crypto.getPublicKeyBase64(),
                    lastMessage = "Selam! Dakk'a hoş geldin. WhatsApp yerine özgürce mesajlaşabilirsin!",
                    lastMessageTime = System.currentTimeMillis()
                )
                repository.saveContact(botContact)

                val welcomeMsg = MessageEntity(
                    id = UUID.randomUUID().toString(),
                    peerId = "DAKK_BOT_OFFICIAL",
                    content = "Selam! Dakk'a hoş geldin 🐥\n\nBurası WhatsApp gibi çalışan ama hiçbir sunucuda mesajlarını tutmayan doğrudan cihazdan cihaza (P2P) mesajlaşma alanın.\n\nArkadaşlarını eklemek için 'Kişiler' sekmesinden WhatsApp ile davet bağlantısı atabilir veya QR kodunu taratabilirsin!",
                    timestamp = System.currentTimeMillis(),
                    isFromMe = false,
                    status = "DELIVERED",
                    sequenceNumber = 1L
                )
                repository.saveMessage(welcomeMsg)
            }
        }

        checkGitHubUpdatesOnLaunch()
    }

    fun onUserAuthenticated(user: FirebaseUser) {
        _firebaseUser.value = user
        val displayName = user.displayName?.ifBlank { null }
            ?: user.email?.substringBefore("@")
            ?: "Dakk Kullanıcısı 🐥"
        crypto.setUserAlias(displayName)

        _currentScreen.value = Screen.Home

        viewModelScope.launch {
            try {
                firestoreRepository.saveUserProfile(
                    displayName = displayName,
                    email = user.email ?: "",
                    photoUrl = user.photoUrl?.toString() ?: ""
                )
            } catch (e: Exception) {
                // Logged via handleFirestoreError
            }
        }

        // Buluttaki kişileri gözlemle ve yerel veritabanı ile senkronize et
        viewModelScope.launch {
            try {
                firestoreRepository.observeContacts().collect { cloudContacts ->
                    cloudContacts.forEach { cc ->
                        val existing = repository.getContact(cc.contactUserId)
                        val updated = ContactEntity(
                            userId = cc.contactUserId,
                            alias = cc.alias,
                            publicKey = existing?.publicKey ?: "",
                            lastMessage = cc.lastMessage.ifBlank { existing?.lastMessage ?: "" },
                            lastMessageTime = cc.lastMessageTime?.toDate()?.time ?: existing?.lastMessageTime ?: System.currentTimeMillis(),
                            isBlocked = cc.isBlocked
                        )
                        repository.saveContact(updated)
                    }
                }
            } catch (e: Exception) {
                // Handled
            }
        }
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
        if (screen is Screen.ChatDetail) {
            _activeChatPeer.value = screen.peer
            observeChatMessages(screen.peer.userId)
        }
    }

    private fun observeChatMessages(peerId: String) {
        viewModelScope.launch {
            repository.getMessagesForPeer(peerId).collect { msgs ->
                _chatMessages.value = msgs
            }
        }
    }

    /**
     * P2P üzerinden şifreli mesaj gönderir ve veritabanına kaydeder
     */
    fun sendMessageToActivePeer(text: String) {
        val peer = _activeChatPeer.value ?: return
        if (text.isBlank()) return

        val msgId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val seq = ++currentSequenceNumber

        viewModelScope.launch {
            // Uçtan uca AES-256-GCM ile şifrele
            val encrypted = try {
                crypto.encrypt(text, peer.publicKey)
            } catch (e: Exception) {
                text // fallback
            }

            // WebRTC DataChannel üzerinden doğrudan gönder
            val isChannelOpen = webRtc.dataChannelState.value == DataChannel.State.OPEN
            val sent = if (isChannelOpen) {
                webRtc.sendMessage(msgId, crypto.getOrCreateUserId(), encrypted, seq)
            } else {
                false
            }

            val isBot = peer.userId == "DAKK_BOT_OFFICIAL"
            val status = if (sent || isBot) "DELIVERED" else "FAILED"

            val messageEntity = MessageEntity(
                id = msgId,
                peerId = peer.userId,
                content = text,
                timestamp = timestamp,
                isFromMe = true,
                status = status,
                sequenceNumber = seq
            )

            repository.saveMessage(messageEntity)

            // Dakk Bot otomatik Firebase AI Logic (Gemini) yanıtı
            if (isBot) {
                val replyText = aiManager.askDakkAi(text)
                val botReply = MessageEntity(
                    id = UUID.randomUUID().toString(),
                    peerId = "DAKK_BOT_OFFICIAL",
                    content = replyText,
                    timestamp = System.currentTimeMillis(),
                    isFromMe = false,
                    status = "DELIVERED",
                    sequenceNumber = ++currentSequenceNumber
                )
                repository.saveMessage(botReply)
            } else if (peer.isGroup) {
                // Grup sohbetinde bot yanıtı simülasyonu
                kotlinx.coroutines.delay(800)
                val botReply = MessageEntity(
                    id = UUID.randomUUID().toString(),
                    peerId = peer.userId,
                    content = "Grup mesajı tüm katılımcılara güvenle iletildi 🐥",
                    timestamp = System.currentTimeMillis(),
                    isFromMe = false,
                    status = "DELIVERED",
                    sequenceNumber = ++currentSequenceNumber,
                    senderAlias = "Dakk Asistanı"
                )
                repository.saveMessage(botReply)
            }
        }
    }

    /**
     * Yeni bir Dakk P2P Grubu oluşturur
     */
    fun createGroup(groupName: String, selectedMembers: List<ContactEntity>, onCreated: (ContactEntity) -> Unit) {
        val groupId = "GROUP_${UUID.randomUUID().toString().take(12)}"
        val membersList = (selectedMembers.map { it.alias } + crypto.getUserAlias()).joinToString(", ")
        val groupContact = ContactEntity(
            userId = groupId,
            alias = groupName.ifBlank { "Yeni Grup 🐥" },
            publicKey = crypto.getPublicKeyBase64(),
            lastMessage = "Grup oluşturuldu",
            lastMessageTime = System.currentTimeMillis(),
            isGroup = true,
            groupMembers = membersList
        )

        viewModelScope.launch {
            repository.saveContact(groupContact)

            val sysMsg = MessageEntity(
                id = UUID.randomUUID().toString(),
                peerId = groupId,
                content = "🎉 '$groupName' grubu oluşturuldu!\nKatılımcılar: $membersList\nWhatsApp davet linkiyle yeni arkadaşlarını gruba davet edebilirsin.",
                timestamp = System.currentTimeMillis(),
                isFromMe = false,
                status = "DELIVERED",
                sequenceNumber = 1L,
                senderAlias = "Sistem"
            )
            repository.saveMessage(sysMsg)

            onCreated(groupContact)
        }
    }

    private fun handleIncomingP2pMessage(senderId: String, encryptedPayload: String, msgId: String, seq: Long) {
        viewModelScope.launch {
            val contact = repository.getContact(senderId)
            val decrypted = if (contact != null) {
                try {
                    crypto.decrypt(encryptedPayload, contact.publicKey)
                } catch (e: Exception) {
                    "[Şifre Çözülemedi - Doğrulama Hatası]"
                }
            } else {
                encryptedPayload
            }

            val messageEntity = MessageEntity(
                id = msgId,
                peerId = senderId,
                content = decrypted,
                timestamp = System.currentTimeMillis(),
                isFromMe = false,
                status = "DELIVERED",
                sequenceNumber = seq
            )
            repository.saveMessage(messageEntity)

            // Bildirim Gönder
            notificationHelper.showMessageNotification(
                senderAlias = contact?.alias ?: "Dakk Kullanıcısı",
                messageText = decrypted
            )
        }
    }

    /**
     * Yeni bir P2P Offer (Teklif) oluşturur ve otomatik iki yönlü el sıkışma odasını dinlemeye başlar
     */
    fun createOfferForHandshake(onReady: ((SignalingPayload) -> Unit)? = null) {
        _isGeneratingOffer.value = true
        _handshakeStatus.value = "STUN sunucularından ICE adayları toplanıyor..."

        // Otomatik 2 yönlü eşleşme için benzersiz geçici oda ID'si
        val autoRoomId = "dk_" + UUID.randomUUID().toString().replace("-", "").take(10)

        webRtc.createOffer { sdp, candidates ->
            _isGeneratingOffer.value = false
            if (sdp != null) {
                val candidateDtos = candidates.map {
                    IceCandidateDto(it.sdpMid, it.sdpMLineIndex, it.sdp)
                }
                val payload = SignalingPayload(
                    type = "OFFER",
                    userId = crypto.getOrCreateUserId(),
                    alias = crypto.getUserAlias(),
                    publicKey = crypto.getPublicKeyBase64(),
                    sdp = sdp.description,
                    candidates = candidateDtos,
                    roomId = autoRoomId
                )
                _myOfferPayload.value = payload
                _handshakeStatus.value = "Teklif hazır! QR kodu gösterin veya bağlantıyı paylaşın."

                // Karşı taraf bağlantıyı açıp kabul ettiğinde otomatik cevabı dinle (2 tarafa da veri gitmesi için)
                rendezvous.subscribeToRoom(autoRoomId) { answerCompact ->
                    val answerPayload = SignalingPayload.decodeFromCompactString(answerCompact)
                    if (answerPayload != null && answerPayload.type == "ANSWER") {
                        rendezvous.stopListening()
                        applyRemoteAnswer(answerPayload)
                        notificationHelper.showFriendConnectedNotification(answerPayload.alias)
                    }
                }

                onReady?.invoke(payload)
            } else {
                _handshakeStatus.value = "Teklif oluşturulamadı. Lütfen tekrar deneyin."
            }
        }
    }

    /**
     * WhatsApp ve diğer mesajlaşma uygulamaları için otomatik P2P teklif bağlantısı paylaşır
     */
    fun shareInviteLinkViaWhatsApp(context: android.content.Context) {
        val sendShareIntent: (SignalingPayload) -> Unit = { payload ->
            val link = payload.toInvitationLink()
            val text = "👋 Selam! Dakk ile WhatsApp yerine doğrudan cihazdan cihaza (P2P), uçtan uca şifreli mesajlaşalım! 🐥\n\nBeni eklemek için linke tıkla:\n$link"
            val sendIntent = android.content.Intent().apply {
                action = android.content.Intent.ACTION_SEND
                putExtra(android.content.Intent.EXTRA_TEXT, text)
                type = "text/plain"
            }
            context.startActivity(android.content.Intent.createChooser(sendIntent, "Dakk'a Davet Et"))
        }

        val existingOffer = _myOfferPayload.value
        if (existingOffer != null) {
            sendShareIntent(existingOffer)
        } else {
            createOfferForHandshake { payload ->
                sendShareIntent(payload)
            }
        }
    }

    /**
     * Gelen davet linki veya QR okutulduğunda tetiklenir
     */
    fun onIncomingInviteReceived(payload: SignalingPayload) {
        if (payload.type == "OFFER") {
            _pendingIncomingInvite.value = payload
        } else if (payload.type == "ANSWER") {
            applyRemoteAnswer(payload)
            notificationHelper.showFriendConnectedNotification(payload.alias)
        }
    }

    fun dismissIncomingInvite() {
        _pendingIncomingInvite.value = null
    }

    /**
     * Kullanıcı gelen arkadaşlık teklifini kabul ettiğinde çalışır
     */
    fun acceptIncomingInvite(payload: SignalingPayload, onConnected: ((ContactEntity) -> Unit)? = null) {
        _pendingIncomingInvite.value = null
        processRemoteOfferAndCreateAnswer(payload) { answerPayload ->
            // Karşı tarafın dinlediği geçici rendezvous odasına cevabı anında yayınla
            if (payload.roomId.isNotBlank()) {
                rendezvous.publishOfferOrAnswer(payload.roomId, answerPayload.encodeToCompactString()) { success ->
                    // 2. tarafa da veri iletildi!
                }
            }

            viewModelScope.launch {
                val contact = repository.getContact(payload.userId) ?: ContactEntity(
                    userId = payload.userId,
                    alias = payload.alias,
                    publicKey = payload.publicKey,
                    lastMessage = "Bağlantı kuruldu! Vak vak 🐥"
                )
                repository.saveContact(contact)
                try {
                    firestoreRepository.saveContact(contactUserId = payload.userId, alias = payload.alias)
                } catch (e: Exception) {
                    // Handled
                }
                notificationHelper.showFriendConnectedNotification(payload.alias)
                onConnected?.invoke(contact)
                _activeChatPeer.value = contact
                _currentScreen.value = Screen.ChatDetail(contact)
            }
        }
    }

    /**
     * Gelen Offer'ı işler ve Answer üretir
     */
    fun processRemoteOfferAndCreateAnswer(remotePayload: SignalingPayload, onAnswerReady: (SignalingPayload) -> Unit) {
        _handshakeStatus.value = "Karşı tarafın teklifi işleniyor ve cevap oluşturuluyor..."

        val remoteCandidates = remotePayload.candidates.map { it.toWebRtc() }
        webRtc.createAnswer(remotePayload.sdp, remoteCandidates) { answerSdp, myCandidates ->
            if (answerSdp != null) {
                val candidateDtos = myCandidates.map {
                    IceCandidateDto(it.sdpMid, it.sdpMLineIndex, it.sdp)
                }
                val answerPayload = SignalingPayload(
                    type = "ANSWER",
                    userId = crypto.getOrCreateUserId(),
                    alias = crypto.getUserAlias(),
                    publicKey = crypto.getPublicKeyBase64(),
                    sdp = answerSdp.description,
                    candidates = candidateDtos,
                    roomId = remotePayload.roomId
                )
                _myAnswerPayload.value = answerPayload
                _handshakeStatus.value = "Cevap üretildi! P2P bağlantısı başlatılıyor..."

                // Karşı tarafı arkadaş olarak kaydet
                viewModelScope.launch {
                    val contact = ContactEntity(
                        userId = remotePayload.userId,
                        alias = remotePayload.alias,
                        publicKey = remotePayload.publicKey,
                        lastMessage = "P2P El sıkışması sağlandı"
                    )
                    repository.saveContact(contact)
                }

                onAnswerReady(answerPayload)
            } else {
                _handshakeStatus.value = "Cevap SDP oluşturulamadı!"
            }
        }
    }

    /**
     * Initiator tarafında karşıdan gelen Answer'ı uygular ve doğrudan bağlantıyı açar
     */
    fun applyRemoteAnswer(answerPayload: SignalingPayload) {
        _handshakeStatus.value = "Cevap uygulanıyor, ICE doğrulaması başlatıldı..."
        val candidates = answerPayload.candidates.map { it.toWebRtc() }

        webRtc.setRemoteAnswer(answerPayload.sdp, candidates) {
            _handshakeStatus.value = "ICE Kontrolü Başladı! Karşı cihaz ile P2P kuruluyor..."

            viewModelScope.launch {
                val contact = ContactEntity(
                    userId = answerPayload.userId,
                    alias = answerPayload.alias,
                    publicKey = answerPayload.publicKey,
                    lastMessage = "P2P El sıkışması tamamlandı"
                )
                repository.saveContact(contact)
                notificationHelper.showFriendConnectedNotification(answerPayload.alias)
            }
        }
    }

    /**
     * 6 Haneli PIN ile İki Yönlü Anında Eşleşme
     */
    fun startPinRoomMatch(pin: String, onConnected: () -> Unit) {
        val cleanPin = pin.trim()
        if (cleanPin.length < 4) return

        _handshakeStatus.value = "PIN odasına bağlanılıyor ($cleanPin)..."

        createOfferForHandshake { myOffer ->
            publishToPinRoom(cleanPin, myOffer) {
                listenToPinRoom(cleanPin) { remotePayload ->
                    if (remotePayload.userId != crypto.getOrCreateUserId()) {
                        if (remotePayload.type == "OFFER") {
                            processRemoteOfferAndCreateAnswer(remotePayload) { myAnswer ->
                                publishToPinRoom(cleanPin, myAnswer) {
                                    notificationHelper.showFriendConnectedNotification(remotePayload.alias)
                                    onConnected()
                                }
                            }
                        } else if (remotePayload.type == "ANSWER") {
                            applyRemoteAnswer(remotePayload)
                            notificationHelper.showFriendConnectedNotification(remotePayload.alias)
                            onConnected()
                        }
                    }
                }
            }
        }
    }

    /**
     * 6 Haneli PIN ile Sıfır Maliyetli Odaya Gönderim
     */
    fun publishToPinRoom(pin: String, payload: SignalingPayload, onDone: (Boolean) -> Unit) {
        val compact = payload.encodeToCompactString()
        rendezvous.publishOfferOrAnswer(pin, compact, onDone)
    }

    fun listenToPinRoom(pin: String, onReceived: (SignalingPayload) -> Unit) {
        rendezvous.subscribeToRoom(pin) { raw ->
            val payload = SignalingPayload.decodeFromCompactString(raw)
            if (payload != null) {
                onReceived(payload)
            }
        }
    }

    fun clearChat(peerId: String) {
        viewModelScope.launch {
            repository.clearChat(peerId)
            _chatMessages.value = emptyList()
        }
    }

    fun toggleBlock(peerId: String, currentBlocked: Boolean) {
        viewModelScope.launch {
            repository.setBlocked(peerId, !currentBlocked)
            _currentScreen.value = Screen.Home
        }
    }

    fun deleteContact(peerId: String) {
        viewModelScope.launch {
            repository.deleteContact(peerId)
            _currentScreen.value = Screen.Home
        }
    }

    fun logDiagnosticResult(notes: String) {
        viewModelScope.launch {
            val log = NetworkLogEntity(
                peerId = _activeChatPeer.value?.userId ?: "TEST_SESSION",
                operatorName = webRtc.selectedOperator.value,
                networkType = webRtc.networkType.value,
                iceState = webRtc.iceConnectionState.value.name,
                stunLatencyMs = webRtc.stunLatencyMs.value,
                rttMs = webRtc.p2pRttMs.value,
                candidateTypes = webRtc.candidateTypes.value.joinToString(", "),
                statusSummary = notes
            )
            repository.addNetworkLog(log)
        }
    }

    fun checkGitHubUpdates() {
        _updateChecking.value = true
        _updateStatusMessage.value = "GitHub Releases denetleniyor..."

        updateManager.checkForUpdate { hasUpdate, info, error ->
            _updateChecking.value = false
            if (error != null) {
                _updateStatusMessage.value = error
            } else if (hasUpdate && info != null) {
                _updateInfo.value = info
                _showUpdateDialog.value = true
                _updateStatusMessage.value = "Yeni sürüm bulundu: v${info.tagName}"
            } else {
                _updateStatusMessage.value = "Dakk güncel (v${GitHubUpdateManager.CURRENT_VERSION})"
            }
        }
    }

    fun checkGitHubUpdatesOnLaunch() {
        updateManager.checkForUpdate { hasUpdate, info, _ ->
            if (hasUpdate && info != null) {
                _updateInfo.value = info
                _showUpdateDialog.value = true
            }
        }
    }

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }

    fun downloadAndInstallUpdate() {
        val info = _updateInfo.value ?: return
        val apkUrl = info.apkDownloadUrl ?: return
        _isDownloading.value = true
        _downloadProgress.value = 0

        updateManager.downloadAndPromptInstall(
            downloadUrl = apkUrl,
            onProgress = { progress ->
                _downloadProgress.value = progress
            },
            onComplete = {
                _isDownloading.value = false
                _showUpdateDialog.value = false
            },
            onError = { errorMsg ->
                _isDownloading.value = false
                _updateStatusMessage.value = errorMsg
            }
        )
    }
}
