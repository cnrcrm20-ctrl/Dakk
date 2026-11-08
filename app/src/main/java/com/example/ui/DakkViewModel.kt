package com.example.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.auth.GoogleAuth
import com.example.crypto.CryptoEngine
import com.example.data.ChatDoc
import com.example.data.ChatIds
import com.example.data.ChatSummary
import com.example.data.Contact
import com.example.data.FirestoreRepository
import com.example.data.MessageDoc
import com.example.data.MessageStatus
import com.example.data.UiMessage
import com.example.data.UserProfile
import com.example.notification.NotificationHelper
import com.example.update.GitHubUpdateManager
import com.example.update.ReleaseInfo
import com.example.update.UpdateCheckResult
import com.example.util.InviteLinks
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class Screen {
    object Home : Screen()
    data class Chat(val peerUid: String) : Screen()
}

enum class HomeTab { CHATS, CONTACTS, SETTINGS }

data class AddContactState(
    val loading: Boolean = false,
    val found: UserProfile? = null,
    val error: String? = null
)

data class UpdateUiState(
    val release: ReleaseInfo? = null,
    val showDialog: Boolean = false,
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val progress: Int = 0,
    val statusMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class DakkViewModel(app: Application) : AndroidViewModel(app) {

    private val auth = Firebase.auth
    val crypto = CryptoEngine(app)
    private val repo = FirestoreRepository(app)
    private val notifications = NotificationHelper(app)
    private val updater = GitHubUpdateManager(app)
    private val prefs = app.getSharedPreferences("dakk_prefs", Context.MODE_PRIVATE)

    val appVersion: String = BuildConfig.VERSION_NAME

    // ------------------------------------------------------------------ Durum

    private val _user = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val user: StateFlow<FirebaseUser?> = _user

    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen

    private val _tab = MutableStateFlow(HomeTab.CHATS)
    val tab: StateFlow<HomeTab> = _tab

    private val _profiles = MutableStateFlow<Map<String, UserProfile>>(emptyMap())
    val profiles: StateFlow<Map<String, UserProfile>> = _profiles

    private val _chatDocs = MutableStateFlow<List<ChatDoc>>(emptyList())
    private val _rawContacts = MutableStateFlow<List<Contact>>(emptyList())
    private val _readMarks = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val _clearMarks = MutableStateFlow<Map<String, Long>>(emptyMap())

    private val _chatsLoaded = MutableStateFlow(false)
    val chatsLoaded: StateFlow<Boolean> = _chatsLoaded

    private val _activeMessages = MutableStateFlow<List<UiMessage>>(emptyList())
    val activeMessages: StateFlow<List<UiMessage>> = _activeMessages

    private val _addContact = MutableStateFlow(AddContactState())
    val addContact: StateFlow<AddContactState> = _addContact

    private val _update = MutableStateFlow(UpdateUiState())
    val update: StateFlow<UpdateUiState> = _update

    private val _autoUpdate = MutableStateFlow(prefs.getBoolean(PREF_AUTO_UPDATE, true))
    val autoUpdate: StateFlow<Boolean> = _autoUpdate

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events

    private val contactsWithProfiles: Flow<List<Contact>> =
        combine(_rawContacts, _profiles) { contacts, profiles ->
            contacts.map { c ->
                val p = profiles[c.uid]
                c.copy(
                    alias = p?.displayName?.ifBlank { null } ?: c.alias.ifBlank { "Dakk Kullanıcısı" },
                    photoUrl = p?.photoUrl?.ifBlank { null } ?: c.photoUrl,
                    email = p?.email?.ifBlank { null } ?: c.email
                )
            }.sortedBy { it.alias.lowercase() }
        }

    val contacts: StateFlow<List<Contact>> = contactsWithProfiles
        .map { list -> list.filterNot { it.isBlocked } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val blockedContacts: StateFlow<List<Contact>> = contactsWithProfiles
        .map { list -> list.filter { it.isBlocked } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val myProfile: StateFlow<UserProfile?> = combine(_user, _profiles) { u, p -> u?.uid?.let { p[it] } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val chats: StateFlow<List<ChatSummary>> =
        combine(_chatDocs, _profiles, _rawContacts, _readMarks, _clearMarks) { docs, profiles, contacts, reads, clears ->
            buildChatSummaries(docs, profiles, contacts, reads, clears)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ------------------------------------------------------------------ Oturum

    private var sessionUid: String? = null
    private var sessionJob: Job? = null
    private val profileJobs = mutableMapOf<String, Job>()
    private var activeChatJob: Job? = null
    private var lastActiveDocs: List<MessageDoc> = emptyList()
    private val readRequested = mutableSetOf<String>()
    private var notificationsPrimed = false
    private var isForeground = false
    private var pendingInvite: String? = null
    private var pendingChatUid: String? = null
    private var pendingApk: File? = null

    private val authListener = FirebaseAuth.AuthStateListener { a -> onAuthChanged(a.currentUser) }

    init {
        auth.addAuthStateListener(authListener)
        if (_autoUpdate.value) checkForUpdates(manual = false)
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authListener)
        super.onCleared()
    }

    private fun onAuthChanged(user: FirebaseUser?) {
        _user.value = user
        if (user == null) {
            stopSession()
        } else if (sessionUid != user.uid) {
            startSession(user)
        }
    }

    private fun <T> Flow<T>.retrying(label: String, uid: String): Flow<T> = retryWhen { cause, attempt ->
        Log.w(TAG, "$label dinleyicisi hata verdi (deneme ${attempt + 1})", cause)
        delay((attempt + 1).coerceAtMost(8) * 1_500L)
        auth.currentUser?.uid == uid
    }

    private fun startSession(user: FirebaseUser) {
        stopSession()
        val uid = user.uid
        sessionUid = uid
        _readMarks.value = loadMarks(PREFIX_READ)
        _clearMarks.value = loadMarks(PREFIX_CLEARED)
        notificationsPrimed = false

        sessionJob = viewModelScope.launch {
            launch { publishMyProfile(user) }
            launch {
                repo.observeContacts(uid).retrying("Kişiler", uid).collect { list ->
                    _rawContacts.value = list
                    list.forEach { ensureProfile(it.uid) }
                }
            }
            launch {
                repo.observeChats(uid).retrying("Sohbetler", uid).collect { docs ->
                    _chatDocs.value = docs
                    _chatsLoaded.value = true
                    docs.forEach { doc -> peerOf(doc, uid)?.let { ensureProfile(it) } }
                    handleNotifications(docs, uid)
                }
            }
        }
        ensureProfile(uid)
        pendingInvite?.let { link ->
            pendingInvite = null
            lookupContact(link, fromLink = true)
        }
        pendingChatUid?.let { peer ->
            pendingChatUid = null
            openChat(peer)
        }
    }

    private fun stopSession() {
        sessionJob?.cancel()
        sessionJob = null
        profileJobs.values.forEach { it.cancel() }
        profileJobs.clear()
        activeChatJob?.cancel()
        activeChatJob = null
        sessionUid = null
        _profiles.value = emptyMap()
        _chatDocs.value = emptyList()
        _rawContacts.value = emptyList()
        _activeMessages.value = emptyList()
        _chatsLoaded.value = false
        _screen.value = Screen.Home
        _tab.value = HomeTab.CHATS
        _addContact.value = AddContactState()
        readRequested.clear()
    }

    private suspend fun publishMyProfile(user: FirebaseUser) {
        try {
            val publicKey = withContext(Dispatchers.Default) { crypto.publicKeyBase64() }
            val name = prefs.getString(PREF_NAME_PREFIX + user.uid, null)
                ?: user.displayName?.ifBlank { null }
                ?: user.email?.substringBefore("@")
                ?: "Dakk Kullanıcısı"
            repo.publishProfile(
                uid = user.uid,
                displayName = name,
                email = user.email.orEmpty(),
                photoUrl = user.photoUrl?.toString().orEmpty(),
                publicKey = publicKey
            )
        } catch (e: Exception) {
            Log.e(TAG, "Profil yayınlanamadı", e)
        }
    }

    private fun ensureProfile(uid: String) {
        if (profileJobs.containsKey(uid)) return
        val myUid = sessionUid ?: return
        profileJobs[uid] = viewModelScope.launch {
            repo.observeProfile(uid).retrying("Profil", myUid).collect { profile ->
                if (profile != null) _profiles.update { it + (uid to profile) }
            }
        }
    }

    // ------------------------------------------------------------------ Gezinme

    fun setTab(tab: HomeTab) {
        _tab.value = tab
    }

    fun openChat(peerUid: String) {
        val myUid = sessionUid ?: return
        ensureProfile(peerUid)
        notifications.cancelFor(peerUid)
        _screen.value = Screen.Chat(peerUid)

        val chatId = ChatIds.of(myUid, peerUid)
        activeChatJob?.cancel()
        _activeMessages.value = emptyList()
        lastActiveDocs = emptyList()
        markChatRead(chatId, 0L)

        activeChatJob = viewModelScope.launch {
            _chatDocs
                .map { docs -> docs.any { it.chatId == chatId } }
                .distinctUntilChanged()
                .flatMapLatest { exists ->
                    if (exists) repo.observeMessages(chatId).retrying("Mesajlar", myUid)
                    else flowOf(emptyList())
                }
                .collect { docs ->
                    lastActiveDocs = docs
                    val cleared = _clearMarks.value[chatId] ?: 0L
                    val ui = withContext(Dispatchers.Default) {
                        docs.filter { it.createdAt > cleared }.map { it.toUi(myUid) }
                    }
                    _activeMessages.value = ui
                    if (isForeground) markIncomingAsRead(chatId, myUid, docs)
                }
        }
    }

    /** Bildirime dokunulduğunda ilgili sohbeti açar (oturum henüz hazır değilse bekletir). */
    fun openChatWhenReady(peerUid: String) {
        if (sessionUid == null) pendingChatUid = peerUid else openChat(peerUid)
    }

    fun closeChat() {
        activeChatJob?.cancel()
        activeChatJob = null
        _activeMessages.value = emptyList()
        lastActiveDocs = emptyList()
        _screen.value = Screen.Home
    }

    fun setForeground(foreground: Boolean) {
        isForeground = foreground
        if (!foreground) return
        val myUid = sessionUid
        val current = _screen.value
        if (myUid != null && current is Screen.Chat) {
            notifications.cancelFor(current.peerUid)
            markIncomingAsRead(ChatIds.of(myUid, current.peerUid), myUid, lastActiveDocs)
        }
        pendingApk?.let { apk ->
            if (updater.canInstallPackages()) {
                pendingApk = null
                runCatching { updater.install(apk) }
            }
        }
    }

    private fun MessageDoc.toUi(myUid: String): UiMessage {
        val fromMe = senderId == myUid
        return UiMessage(
            id = id,
            text = crypto.decryptMessage(content, senderKey, recipientKey),
            isFromMe = fromMe,
            timestamp = createdAt,
            status = when {
                isPending -> MessageStatus.PENDING
                deliveryStatus == "READ" -> MessageStatus.READ
                else -> MessageStatus.SENT
            }
        )
    }

    private fun markIncomingAsRead(chatId: String, myUid: String, docs: List<MessageDoc>) {
        val latest = docs.maxOfOrNull { it.createdAt } ?: 0L
        markChatRead(chatId, latest)
        val ids = docs.filter {
            it.senderId != myUid && it.deliveryStatus != "READ" && !it.isPending && it.id !in readRequested
        }.map { it.id }
        if (ids.isNotEmpty()) {
            readRequested.addAll(ids)
            repo.markRead(chatId, ids)
        }
    }

    private fun markChatRead(chatId: String, latestMessageTime: Long) {
        val docTime = _chatDocs.value.firstOrNull { it.chatId == chatId }?.lastMessageTime ?: 0L
        val mark = maxOf(latestMessageTime, docTime, _readMarks.value[chatId] ?: 0L)
        if (mark <= (_readMarks.value[chatId] ?: 0L)) return
        _readMarks.update { it + (chatId to mark) }
        prefs.edit().putLong(PREFIX_READ + chatId, mark).apply()
    }

    // ------------------------------------------------------------------ Mesajlaşma

    /** Gönderim kuyruğa alındıysa true döner (giriş alanı temizlenebilir). */
    fun sendMessage(peerUid: String, rawText: String): Boolean {
        val text = rawText.trim()
        if (text.isEmpty()) return false
        val myUid = sessionUid ?: return false
        val peer = _profiles.value[peerUid]
        if (peer == null || peer.publicKey.isBlank()) {
            _events.tryEmit("Bu kişi henüz Dakk'ın güncel sürümüne giriş yapmamış. Şifreli mesaj gönderilemiyor.")
            return false
        }
        if (text.toByteArray(Charsets.UTF_8).size > MAX_MESSAGE_BYTES) {
            _events.tryEmit("Mesaj çok uzun. Lütfen daha kısa parçalar halinde gönderin.")
            return false
        }
        val myName = _profiles.value[myUid]?.displayName?.ifBlank { null }
            ?: _user.value?.displayName ?: "Dakk Kullanıcısı"

        viewModelScope.launch {
            try {
                val (content, preview, myKey) = withContext(Dispatchers.Default) {
                    Triple(
                        crypto.encrypt(text, peer.publicKey),
                        crypto.encrypt(previewOf(text), peer.publicKey),
                        crypto.publicKeyBase64()
                    )
                }
                repo.sendMessage(
                    chatId = ChatIds.of(myUid, peerUid),
                    myUid = myUid,
                    peerUid = peerUid,
                    senderName = myName,
                    encryptedContent = content,
                    encryptedPreview = preview,
                    senderKey = myKey,
                    recipientKey = peer.publicKey
                ) { _events.tryEmit("Mesaj gönderilemedi. Lütfen tekrar deneyin.") }
            } catch (e: Exception) {
                Log.e(TAG, "Şifreleme hatası", e)
                _events.tryEmit("Mesaj şifrelenemedi.")
            }
        }
        return true
    }

    fun deleteMessage(peerUid: String, messageId: String) {
        val myUid = sessionUid ?: return
        val chatId = ChatIds.of(myUid, peerUid)
        val wasLast = _activeMessages.value.lastOrNull()?.id == messageId
        viewModelScope.launch {
            try {
                repo.deleteMessage(chatId, messageId)
                if (wasLast) {
                    val peer = _profiles.value[peerUid] ?: return@launch
                    if (peer.publicKey.isBlank()) return@launch
                    val myKey = withContext(Dispatchers.Default) { crypto.publicKeyBase64() }
                    val preview = withContext(Dispatchers.Default) {
                        crypto.encrypt("Bu mesaj silindi", peer.publicKey)
                    }
                    repo.sendPreviewOnly(chatId, myUid, peerUid, preview, myKey, peer.publicKey)
                }
            } catch (e: Exception) {
                _events.tryEmit("Mesaj silinemedi.")
            }
        }
    }

    fun clearChat(peerUid: String) {
        val myUid = sessionUid ?: return
        val chatId = ChatIds.of(myUid, peerUid)
        val latest = maxOf(
            System.currentTimeMillis(),
            _activeMessages.value.maxOfOrNull { it.timestamp } ?: 0L,
            _chatDocs.value.firstOrNull { it.chatId == chatId }?.lastMessageTime ?: 0L
        )
        _clearMarks.update { it + (chatId to latest) }
        prefs.edit().putLong(PREFIX_CLEARED + chatId, latest).apply()
        _activeMessages.value = emptyList()
        _events.tryEmit("Sohbet bu cihazdan temizlendi")
    }

    fun setBlocked(peerUid: String, blocked: Boolean) {
        val myUid = sessionUid ?: return
        val peer = _profiles.value[peerUid]
            ?: UserProfile(peerUid, _rawContacts.value.firstOrNull { it.uid == peerUid }?.alias.orEmpty(), "", "", "")
        viewModelScope.launch {
            try {
                repo.setBlocked(myUid, peer, blocked)
                _events.tryEmit(if (blocked) "${peer.displayName.ifBlank { "Kişi" }} engellendi" else "Engel kaldırıldı")
                if (blocked && (_screen.value as? Screen.Chat)?.peerUid == peerUid) closeChat()
            } catch (e: Exception) {
                _events.tryEmit("İşlem tamamlanamadı. İnternet bağlantınızı kontrol edin.")
            }
        }
    }

    fun removeContact(peerUid: String) {
        val myUid = sessionUid ?: return
        viewModelScope.launch {
            try {
                repo.deleteContact(myUid, peerUid)
                _events.tryEmit("Kişi silindi")
            } catch (e: Exception) {
                _events.tryEmit("Kişi silinemedi.")
            }
        }
    }

    fun isContact(peerUid: String): Boolean = _rawContacts.value.any { it.uid == peerUid && !it.isBlocked }

    fun safetyNumberFor(peerUid: String): String? {
        val key = _profiles.value[peerUid]?.publicKey?.ifBlank { null } ?: return null
        return runCatching { crypto.safetyNumber(key) }.getOrNull()
    }

    // ------------------------------------------------------------------ Kişi ekleme

    fun myInviteLink(): String? = sessionUid?.let { InviteLinks.build(BuildConfig.INVITE_BASE_URL, it) }

    fun handleIncomingLink(link: String) {
        if (InviteLinks.parseUid(link) == null) return
        if (sessionUid == null) pendingInvite = link else lookupContact(link, fromLink = true)
    }

    fun lookupContact(input: String, fromLink: Boolean = false) {
        val text = input.trim()
        val myUid = sessionUid ?: return
        if (text.isEmpty()) return
        viewModelScope.launch {
            _addContact.value = AddContactState(loading = true)
            try {
                val profile = if (text.contains("@") && !text.contains("://")) {
                    repo.findProfileByEmail(text)
                } else {
                    InviteLinks.parseUid(text)?.let { repo.getProfile(it) }
                }
                val error = when {
                    profile == null -> "Bu bilgilerle kayıtlı bir Dakk kullanıcısı bulunamadı."
                    profile.uid == myUid -> "Bu sizin kendi hesabınız."
                    else -> null
                }
                if (error == null && profile != null) {
                    _addContact.value = AddContactState(found = profile)
                } else {
                    reportLookupError(error ?: "", fromLink)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Kişi araması başarısız", e)
                reportLookupError("Arama yapılamadı. İnternet bağlantınızı kontrol edin.", fromLink)
            }
        }
    }

    private fun reportLookupError(message: String, fromLink: Boolean) {
        if (fromLink) {
            _addContact.value = AddContactState()
            _events.tryEmit(message)
        } else {
            _addContact.value = AddContactState(error = message)
        }
    }

    fun confirmAddContact(profile: UserProfile) {
        val myUid = sessionUid ?: return
        viewModelScope.launch {
            try {
                _addContact.value = AddContactState(loading = true, found = profile)
                repo.saveContact(myUid, profile)
                _profiles.update { it + (profile.uid to profile) }
                _addContact.value = AddContactState()
                _events.tryEmit("${profile.displayName} kişilerinize eklendi")
                openChat(profile.uid)
            } catch (e: Exception) {
                _addContact.value = AddContactState(found = profile, error = "Kişi eklenemedi. Lütfen tekrar deneyin.")
            }
        }
    }

    fun resetAddContact() {
        _addContact.value = AddContactState()
    }

    // ------------------------------------------------------------------ Hesap

    fun updateDisplayName(name: String) {
        val user = _user.value ?: return
        val clean = name.trim().take(60)
        if (clean.isEmpty()) return
        prefs.edit().putString(PREF_NAME_PREFIX + user.uid, clean).apply()
        viewModelScope.launch {
            publishMyProfile(user)
            _events.tryEmit("Adınız güncellendi")
        }
    }

    fun signOut() {
        viewModelScope.launch {
            GoogleAuth.signOut(getApplication())
        }
    }

    // ------------------------------------------------------------------ Güncelleme

    fun setAutoUpdate(enabled: Boolean) {
        _autoUpdate.value = enabled
        prefs.edit().putBoolean(PREF_AUTO_UPDATE, enabled).apply()
    }

    fun checkForUpdates(manual: Boolean) {
        if (_update.value.checking || _update.value.downloading) return
        _update.update { it.copy(checking = true, statusMessage = if (manual) "Denetleniyor…" else null) }
        viewModelScope.launch {
            when (val result = updater.check()) {
                is UpdateCheckResult.Available -> _update.update {
                    it.copy(checking = false, release = result.release, showDialog = true,
                        statusMessage = "Yeni sürüm mevcut: v${result.release.version}")
                }
                UpdateCheckResult.UpToDate -> _update.update {
                    it.copy(checking = false, statusMessage = if (manual) "Dakk güncel (v$appVersion)" else null)
                }
                is UpdateCheckResult.Failed -> _update.update {
                    it.copy(checking = false, statusMessage = if (manual) result.message else null)
                }
            }
        }
    }

    fun showUpdateDialog() {
        if (_update.value.release != null) _update.update { it.copy(showDialog = true) }
    }

    fun dismissUpdateDialog() {
        if (!_update.value.downloading) _update.update { it.copy(showDialog = false) }
    }

    fun startUpdate() {
        val release = _update.value.release ?: return
        if (_update.value.downloading) return
        _update.update { it.copy(downloading = true, progress = 0) }
        viewModelScope.launch {
            try {
                val apk = updater.download(release) { p -> _update.update { it.copy(progress = p) } }
                _update.update { it.copy(downloading = false, progress = 100, showDialog = false) }
                if (updater.canInstallPackages()) {
                    updater.install(apk)
                } else {
                    pendingApk = apk
                    _events.tryEmit("Güncellemeyi yüklemek için Dakk'a izin verin, ardından geri dönün.")
                    updater.openInstallPermissionSettings()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Güncelleme indirilemedi", e)
                _update.update { it.copy(downloading = false, statusMessage = "İndirme başarısız oldu. Tekrar deneyin.") }
                _events.tryEmit("Güncelleme indirilemedi. İnternet bağlantınızı kontrol edin.")
            }
        }
    }

    // ------------------------------------------------------------------ Yardımcılar

    private fun peerOf(doc: ChatDoc, myUid: String): String? =
        doc.memberUids.firstOrNull { it != myUid } ?: doc.memberUids.firstOrNull()

    private fun buildChatSummaries(
        docs: List<ChatDoc>,
        profiles: Map<String, UserProfile>,
        contacts: List<Contact>,
        reads: Map<String, Long>,
        clears: Map<String, Long>
    ): List<ChatSummary> {
        val myUid = sessionUid ?: return emptyList()
        val blocked = contacts.filter { it.isBlocked }.map { it.uid }.toSet()
        return docs.mapNotNull { doc ->
            val peerUid = peerOf(doc, myUid) ?: return@mapNotNull null
            if (peerUid in blocked) return@mapNotNull null
            if (doc.lastMessage.isBlank() && doc.lastMessageTime == 0L) return@mapNotNull null
            if (doc.lastMessageTime <= (clears[doc.chatId] ?: 0L)) return@mapNotNull null
            val profile = profiles[peerUid]
            val contact = contacts.firstOrNull { it.uid == peerUid }
            val fromMe = doc.lastMessageSenderId == myUid
            ChatSummary(
                chatId = doc.chatId,
                peerUid = peerUid,
                peerName = profile?.displayName?.ifBlank { null } ?: contact?.alias?.ifBlank { null } ?: "Dakk Kullanıcısı",
                peerPhotoUrl = profile?.photoUrl?.ifBlank { null } ?: contact?.photoUrl.orEmpty(),
                preview = crypto.decryptMessage(doc.lastMessage, doc.lastMessageSenderKey, doc.lastMessageRecipientKey)
                    ?: "🔒 Şifreli mesaj",
                lastMessageTime = doc.lastMessageTime,
                lastFromMe = fromMe,
                isUnread = !fromMe && doc.lastMessageTime > (reads[doc.chatId] ?: 0L)
            )
        }.sortedByDescending { it.lastMessageTime }
    }

    private fun handleNotifications(docs: List<ChatDoc>, myUid: String) {
        val editor = prefs.edit()
        val blocked = _rawContacts.value.filter { it.isBlocked }.map { it.uid }.toSet()
        docs.forEach { doc ->
            if (doc.lastMessageSenderId.isBlank() || doc.lastMessageSenderId == myUid) return@forEach
            val key = PREFIX_NOTIFIED + doc.chatId
            val last = prefs.getLong(key, -1L)
            if (doc.lastMessageTime <= last) return@forEach
            editor.putLong(key, doc.lastMessageTime)
            if (!notificationsPrimed) return@forEach

            val peerUid = peerOf(doc, myUid) ?: return@forEach
            if (peerUid in blocked) return@forEach
            val viewing = isForeground && (_screen.value as? Screen.Chat)?.peerUid == peerUid
            if (viewing) return@forEach

            viewModelScope.launch {
                val text = withContext(Dispatchers.Default) {
                    crypto.decryptMessage(doc.lastMessage, doc.lastMessageSenderKey, doc.lastMessageRecipientKey)
                } ?: "Yeni şifreli mesaj"
                val name = _profiles.value[peerUid]?.displayName?.ifBlank { null } ?: "Dakk"
                notifications.showMessage(peerUid, name, text)
            }
        }
        editor.apply()
        notificationsPrimed = true
    }

    private fun loadMarks(prefix: String): Map<String, Long> =
        prefs.all.entries
            .filter { it.key.startsWith(prefix) }
            .mapNotNull { e -> (e.value as? Long)?.let { e.key.removePrefix(prefix) to it } }
            .toMap()

    private fun previewOf(text: String): String {
        val single = text.replace('\n', ' ')
        return if (single.length > 100) single.take(100) + "…" else single
    }

    companion object {
        private const val TAG = "DakkViewModel"
        private const val PREF_AUTO_UPDATE = "auto_update"
        private const val PREF_NAME_PREFIX = "display_name_"
        private const val PREFIX_READ = "read_"
        private const val PREFIX_CLEARED = "cleared_"
        private const val PREFIX_NOTIFIED = "notified_"
        const val MAX_MESSAGE_BYTES = 3_500
        const val MAX_MESSAGE_CHARS = 2_000
    }
}
