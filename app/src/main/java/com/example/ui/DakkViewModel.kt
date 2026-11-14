package com.example.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.example.BuildConfig
import com.example.assistant.AssistantEngine
import com.example.assistant.AssistantException
import com.example.assistant.AssistantTurn
import com.example.config.Announcement
import com.example.config.ConfigRepository
import com.example.config.PrivateConfig
import com.example.config.PublicConfig
import com.example.data.ImageAttachment
import com.example.data.ImageState
import com.example.media.DownloadResult
import com.example.media.ImageMeta
import com.example.media.MediaManager
import com.example.push.AppState
import com.example.push.PushClient
import com.example.auth.GoogleAuth
import com.example.crypto.CryptoEngine
import com.example.data.ChatDoc
import com.example.data.ChatIds
import com.example.data.ChatPrefs
import com.example.data.LocalPhoto
import com.example.data.PollView
import com.example.push.TypingClient
import com.example.util.Disappearing
import com.example.util.MuteOptions
import com.example.util.Polls
import com.example.util.SystemNotes
import com.example.util.TypingText
import com.example.data.ChatSummary
import com.example.data.Contact
import com.example.data.FirestoreRepository
import com.example.data.MessageDoc
import com.example.data.MessageStatus
import com.example.data.UiMessage
import com.example.data.ReplyQuote
import com.example.data.UserProfile
import com.example.notification.NotificationHelper
import com.example.update.GitHubUpdateManager
import com.example.update.ReleaseInfo
import com.example.update.UpdateCheckResult
import com.example.util.AssistantMention
import com.example.util.InviteLinks
import com.example.util.Reactions
import com.example.util.ReplySnippets
import com.example.util.ScreenshotNotice
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
    /** Sohbet. peerUid: birebirde karşı tarafın kimliği, grupta grubun kimliği ("g_…"). */
    data class Chat(val peerUid: String) : Screen()
    /** Sohbet/grup bilgileri (Instagram tarzı ayrıntılar ekranı). */
    data class ChatInfo(val key: String) : Screen()
    /** Yeni grup oluşturma veya var olan gruba kişi ekleme. */
    data class NewGroup(val preselected: List<String> = emptyList(), val addToGroup: String? = null) : Screen()
    object Assistant : Screen()
    object FounderPanel : Screen()

    /** Geçiş animasyonu için ekran derinliği. */
    val depth: Int
        get() = when (this) {
            Home -> 0
            is Chat, Assistant, FounderPanel -> 1
            is ChatInfo, is NewGroup -> 2
        }
}

/** Sohbetteki bir kişi. name: takma ad varsa takma ad. */
data class ChatMember(
    val uid: String,
    val name: String,
    val realName: String,
    val nickname: String?,
    val photoUrl: String,
    val email: String,
    val isAdmin: Boolean,
    val isMe: Boolean,
    val hasKey: Boolean
)

/** Açık sohbetin başlık ve ayrıntı bilgileri. */
data class ActiveChat(
    val key: String,
    val chatId: String,
    val isGroup: Boolean,
    val title: String,
    val photoModel: Any?,
    val subtitle: String,
    val members: List<ChatMember>,
    val amAdmin: Boolean,
    val isMember: Boolean,
    val exists: Boolean,
    val disappearSeconds: Long,
    val muteUntil: Long,
    val inviteEnabled: Boolean,
    val inviteLink: String?,
    val canSend: Boolean,
    val hasCustomPhoto: Boolean,
    val peer: UserProfile?
) {
    fun member(uid: String): ChatMember? = members.firstOrNull { it.uid == uid }
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
    val statusMessage: String? = null,
    /** İndirilen sürüm farklı bir anahtarla imzalanmış; yeniden kurulum gerekiyor. */
    val signatureMismatch: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class DakkViewModel(app: Application) : AndroidViewModel(app) {

    private val auth = Firebase.auth
    val crypto = CryptoEngine(app)
    private val repo = FirestoreRepository(app)
    private val notifications = NotificationHelper(app)
    private val updater = GitHubUpdateManager(app)
    private val prefs = app.getSharedPreferences("dakk_prefs", Context.MODE_PRIVATE)
    val configRepo = ConfigRepository(app)
    private val media = MediaManager(app, repo, crypto)
    private val assistant = AssistantEngine(app)
    private val push = PushClient(repo)
    private val typingClient = TypingClient(viewModelScope)

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
    /** Sohbet kimliği → sessizlik bitiş zamanı (yalnızca bu cihaz). */
    private val _mutes = MutableStateFlow<Map<String, Long>>(emptyMap())
    /** Kişi kimliği → bu cihazda verilen özel fotoğrafın sürümü. */
    private val _customPhotos = MutableStateFlow<Map<String, Long>>(emptyMap())
    /** Açık sohbetin anahtarı (birebirde kişi kimliği, grupta grup kimliği). */
    private val _activeKey = MutableStateFlow<String?>(null)

    private val _groupInvite = MutableStateFlow<InviteLinks.GroupInvite?>(null)
    /** Davet bağlantısıyla açılan ve katılım onayı bekleyen grup. */
    val groupInvite: StateFlow<InviteLinks.GroupInvite?> = _groupInvite
    private val _joiningGroup = MutableStateFlow(false)
    val joiningGroup: StateFlow<Boolean> = _joiningGroup

    private val _chatSearchRequest = MutableStateFlow<String?>(null)
    /** Bilgi ekranındaki "Ara" düğmesi: sohbet açılınca arama çubuğu gösterilir. */
    val chatSearchRequest: StateFlow<String?> = _chatSearchRequest

    private val _busy = MutableStateFlow(false)
    /** Grup oluşturma gibi kısa süren işlemler sürerken true. */
    val busy: StateFlow<Boolean> = _busy

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

    // Uzak ayarlar (Kurucu paneli) ---------------------------------------
    private val _config = MutableStateFlow(PublicConfig())
    val config: StateFlow<PublicConfig> = _config

    private val _isFounder = MutableStateFlow(false)
    val isFounder: StateFlow<Boolean> = _isFounder
    private var privateConfig: PrivateConfig? = null

    // Duyurular -----------------------------------------------------------
    private val _seenAnnouncements = MutableStateFlow(loadSeenAnnouncements())
    private val _previewAnnouncement = MutableStateFlow<Announcement?>(null)
    val previewAnnouncement: StateFlow<Announcement?> = _previewAnnouncement

    /** Gösterilmeyi bekleyen duyuru (önizleme öncelikli). */
    val announcement: StateFlow<Announcement?> =
        combine(_config, _seenAnnouncements, _previewAnnouncement) { cfg, seen, preview ->
            preview ?: cfg.announcement?.takeIf { it.isLive(System.currentTimeMillis(), appVersion) && it.id !in seen }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Fotoğraflar ---------------------------------------------------------
    private val _imageStates = MutableStateFlow<Map<String, ImageState>>(emptyMap())
    private val downloadsInFlight = mutableSetOf<String>()
    private val _uploads = MutableStateFlow<Map<String, List<UiMessage>>>(emptyMap())
    val uploads: StateFlow<Map<String, List<UiMessage>>> = _uploads

    // Dakk Asistan --------------------------------------------------------
    private val _assistantTurns = MutableStateFlow<List<AssistantTurn>>(emptyList())
    val assistantTurns: StateFlow<List<AssistantTurn>> = _assistantTurns
    private val _assistantBusy = MutableStateFlow(false)
    val assistantBusy: StateFlow<Boolean> = _assistantBusy
    private val _chatAssistantBusy = MutableStateFlow<String?>(null)
    /** @dakk ile çağrılan asistanın yanıt hazırladığı sohbetin kişi kimliği. */
    val chatAssistantBusy: StateFlow<String?> = _chatAssistantBusy
    private var assistantHistoryLoaded = false

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

    private data class ListMarks(
        val reads: Map<String, Long>,
        val clears: Map<String, Long>,
        val mutes: Map<String, Long>,
        val photos: Map<String, Long>
    )

    private val listMarks: Flow<ListMarks> =
        combine(_readMarks, _clearMarks, _mutes, _customPhotos) { r, c, m, p -> ListMarks(r, c, m, p) }

    val chats: StateFlow<List<ChatSummary>> =
        combine(_chatDocs, _profiles, _rawContacts, listMarks) { docs, profiles, contacts, marks ->
            buildChatSummaries(docs, profiles, contacts, marks)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Açık sohbetin başlık, üye ve ayar bilgileri. */
    val activeChat: StateFlow<ActiveChat?> =
        combine(_activeKey, _chatDocs, _profiles, _rawContacts, combine(_mutes, _customPhotos) { m, p -> m to p }) {
            key, docs, profiles, contacts, mp ->
            key?.let { buildActiveChat(it, docs, profiles, contacts, mp.first, mp.second) }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Sohbet anahtarı → şu an yazan kişilerin kimlikleri. */
    private val typingByKey: Flow<Map<String, List<String>>> =
        combine(typingClient.typing, _chatDocs) { typing, docs ->
            val myUid = sessionUid
            typing.mapNotNull { (chatId, users) ->
                val doc = docs.firstOrNull { it.chatId == chatId } ?: return@mapNotNull null
                val key = if (isGroupDoc(doc)) doc.chatId else myUid?.let { peerOf(doc, it) } ?: return@mapNotNull null
                val uids = users.keys.filter { it in doc.memberUids && it != myUid }.sorted()
                if (uids.isEmpty()) null else key to uids
            }.toMap()
        }

    /** Sohbet listesi için: sohbet anahtarı → "yazıyor…" metni. */
    val typingLabels: StateFlow<Map<String, String>> =
        combine(typingByKey, _chatDocs, _profiles, _rawContacts) { typing, docs, profiles, contacts ->
            typing.mapNotNull { (key, uids) ->
                val isGroup = ChatIds.isGroup(key)
                val doc = if (isGroup) docs.firstOrNull { it.chatId == key } else null
                val names = uids.map { resolveName(it, doc?.nicknames.orEmpty(), profiles, contacts) }
                TypingText.label(names, isGroup)?.let { key to it }
            }.toMap()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Açık sohbette şu an yazan kişiler. */
    val activeTyping: StateFlow<List<ChatMember>> =
        combine(typingByKey, activeChat) { typing, chat ->
            if (chat == null) emptyList() else typing[chat.key].orEmpty().mapNotNull { chat.member(it) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
    private var pendingGroupInvite: InviteLinks.GroupInvite? = null
    private val groupKeyCache = HashMap<String, ByteArray?>()
    private val expiredRequested = mutableSetOf<String>()
    private var expiryTicker: Job? = null
    private var pendingChatUid: String? = null
    private var pendingApk: File? = null
    private var pushTokenRegistered = false
    private var serverReady = false
    private var checkedServerUrl: String? = null
    private val decryptCache = HashMap<String, String?>()

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
        _mutes.value = loadMarks(ChatPrefs.PREFIX_MUTE)
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
                    docs.forEach { doc ->
                        if (isGroupDoc(doc)) doc.memberUids.forEach { ensureProfile(it) }
                        else peerOf(doc, uid)?.let { ensureProfile(it) }
                    }
                    handleNotifications(docs, uid)
                }
            }
            launch(Dispatchers.Default) {
                // "Yazıyor…" kanalları: en son konuşulan sohbetler.
                combine(_chatDocs, _profiles) { docs, profiles -> typingTopics(docs, profiles, uid) }
                    .distinctUntilChanged()
                    .collect { typingClient.setTopics(it) }
            }
            launch(Dispatchers.IO) { loadCustomPhotos() }
            launch {
                configRepo.observePublic().retrying("Ayarlar", uid).collect { cfg ->
                    _config.value = cfg
                    updatePushActive()
                    if (cfg.serverUrl != checkedServerUrl) {
                        checkedServerUrl = cfg.serverUrl
                        launch {
                            serverReady = cfg.hasServer && push.isServerReady(cfg.serverUrl)
                            updatePushActive()
                            typingClient.configure(if (serverReady) push.realtime else null, uid)
                            typingClient.setActive(isForeground)
                        }
                    }
                    if (cfg.requiresUpdate(appVersion) && _update.value.release == null) checkForUpdates(manual = false)
                }
            }
            launch { refreshFounderStatus() }
            launch {
                pushTokenRegistered = push.registerToken(uid)
                updatePushActive()
            }
            launch { runCatching { media.cleanupExpiredUploads() } }
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
        pendingGroupInvite?.let { invite ->
            pendingGroupInvite = null
            offerGroupInvite(invite)
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
        _isFounder.value = false
        privateConfig = null
        pushTokenRegistered = false
        serverReady = false
        checkedServerUrl = null
        updatePushActive()
        _uploads.value = emptyMap()
        _imageStates.value = emptyMap()
        synchronized(decryptCache) { decryptCache.clear() }
        _assistantTurns.value = emptyList()
        assistantHistoryLoaded = false
        _activeKey.value = null
        _groupInvite.value = null
        synchronized(groupKeyCache) { groupKeyCache.clear() }
        typingClient.configure(null, null)
    }

    private fun updatePushActive() {
        AppState.pushActive = pushTokenRegistered && _config.value.hasServer && serverReady
        // Bildirimden yanıt verilirken (uygulama kapalıyken) kullanılacak sunucu adresi.
        val server = if (_config.value.hasServer && serverReady) _config.value.serverUrl else ""
        AppState.rememberServer(getApplication<Application>(), server)
    }

    /** Kurucu yetkisini Firestore kurallarına sorar (yalnızca kurucular app_config/private okuyabilir). */
    suspend fun refreshFounderStatus() {
        try {
            privateConfig = configRepo.loadPrivateIfFounder()
            _isFounder.value = privateConfig != null
        } catch (e: Exception) {
            Log.w(TAG, "Kurucu durumu alınamadı", e)
        }
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
        val key = peerUid
        val isGroup = ChatIds.isGroup(key)
        if (!isGroup) ensureProfile(key)
        notifications.cancelFor(key)
        _screen.value = Screen.Chat(key)
        val chatId = chatIdFor(key, myUid)
        if (_activeKey.value == key && activeChatJob?.isActive == true) return

        sendTypingStop()
        _activeKey.value = key
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
                    rebuildActiveMessages(chatId, myUid)
                    if (isForeground) markIncomingAsRead(chatId, myUid, docs)
                }
        }
        expiryTicker?.cancel()
        expiryTicker = viewModelScope.launch {
            // Kaybolan mesajlar süresi dolunca ekrandan kalksın.
            while (true) {
                delay(15_000)
                val now = System.currentTimeMillis()
                if (lastActiveDocs.any { Disappearing.isExpired(it.expiresAt, now) }) rebuildActiveMessages(chatId, myUid)
            }
        }
    }

    /** Bilgi ekranından sohbete dönerken veya bilgi ekranını açarken sohbet dinlemesi sürer. */
    fun openChatInfo(key: String) {
        if (_activeKey.value != key) openChat(key)
        _screen.value = Screen.ChatInfo(key)
    }

    fun closeChatInfo() {
        val key = _activeKey.value
        if (key != null) _screen.value = Screen.Chat(key) else closeChat()
    }

    /** Bilgi ekranındaki "Ara": sohbete dönüp arama çubuğunu açar. */
    fun openChatSearch(key: String) {
        _chatSearchRequest.value = key
        _screen.value = Screen.Chat(key)
    }

    fun consumeChatSearchRequest() {
        _chatSearchRequest.value = null
    }

    private fun activeChatId(myUid: String): String? = _activeKey.value?.let { chatIdFor(it, myUid) }

    private suspend fun rebuildActiveMessages(chatId: String, myUid: String) {
        val docs = lastActiveDocs
        val cleared = _clearMarks.value[chatId] ?: 0L
        val now = System.currentTimeMillis()
        val expired = docs.filter { Disappearing.isExpired(it.expiresAt, now) && !it.isPending }
        val ui = withContext(Dispatchers.Default) {
            docs.filter { it.createdAt > cleared && !Disappearing.isExpired(it.expiresAt, now) }.map { it.toUi(myUid) }
        }
        if (activeChatId(myUid) != chatId) return
        _activeMessages.value = ui
        startImageDownloads(chatId, docs, ui)
        if (expired.isNotEmpty()) deleteExpired(chatId, myUid, expired)
    }

    /** Süresi dolan kaybolan mesajları ve fotoğraflarını sunucudan siler. */
    private fun deleteExpired(chatId: String, myUid: String, expired: List<MessageDoc>) {
        val fresh = expired.filter { it.id !in expiredRequested }
        if (fresh.isEmpty()) return
        expiredRequested += fresh.map { it.id }
        viewModelScope.launch {
            fresh.filter { it.messageType == "IMAGE" }.forEach { doc ->
                val meta = withContext(Dispatchers.Default) { ImageMeta.parse(decryptContent(doc, myUid)) } ?: return@forEach
                runCatching { media.deleteMedia(chatId, meta) }
            }
            runCatching { repo.deleteMessages(chatId, fresh.map { it.id }) }
                .onFailure { Log.w(TAG, "Kaybolan mesajlar silinemedi", it) }
        }
    }

    /** Görünen sohbette henüz cihazda olmayan fotoğrafları indirir. */
    private fun startImageDownloads(chatId: String, docs: List<MessageDoc>, ui: List<UiMessage>) {
        val myUid = sessionUid ?: return
        val byId = docs.associateBy { it.id }
        ui.forEach { m ->
            val image = m.image ?: return@forEach
            if (image.state != ImageState.DOWNLOADING || image.mediaId in downloadsInFlight) return@forEach
            val doc = byId[m.id] ?: return@forEach
            if (doc.isPending) return@forEach
            downloadsInFlight += image.mediaId
            viewModelScope.launch {
                val meta = ImageMeta(image.mediaId, image.chunks, image.width, image.height, image.size, null)
                val result = if (doc.keys.isNotEmpty()) {
                    // Grup fotoğrafını birden çok kişi indirir; parçalar gönderici tarafından 7 gün sonra silinir.
                    val key = messageKey(doc, myUid)
                    if (key == null) DownloadResult.Failed
                    else media.download(chatId, meta, deleteAfter = false) { crypto.decryptBytesWithKey(it, key) }
                } else {
                    media.download(chatId, meta, doc.senderKey, doc.recipientKey, deleteAfter = doc.senderId != myUid)
                }
                downloadsInFlight -= image.mediaId
                val state = when (result) {
                    is DownloadResult.Ok -> ImageState.READY
                    DownloadResult.Expired -> ImageState.EXPIRED
                    DownloadResult.Failed -> ImageState.FAILED
                }
                _imageStates.update { it + (image.mediaId to state) }
                rebuildActiveMessages(chatId, myUid)
            }
        }
    }

    /** İndirilemeyen bir fotoğrafı yeniden dener. */
    fun retryImage(peerUid: String, mediaId: String) {
        val myUid = sessionUid ?: return
        _imageStates.update { it - mediaId }
        viewModelScope.launch { rebuildActiveMessages(chatIdFor(peerUid, myUid), myUid) }
    }

    fun imageFile(mediaId: String): File = media.localFile(mediaId)

    fun saveImageToGallery(mediaId: String) {
        viewModelScope.launch {
            _events.tryEmit(if (media.saveToGallery(mediaId)) "Fotoğraf galeriye kaydedildi" else "Fotoğraf kaydedilemedi")
        }
    }

    /** Bildirime dokunulduğunda ilgili sohbeti açar (oturum henüz hazır değilse bekletir). */
    fun openChatWhenReady(peerUid: String) {
        if (sessionUid == null) pendingChatUid = peerUid else openChat(peerUid)
    }

    fun closeChat() {
        sendTypingStop()
        activeChatJob?.cancel()
        activeChatJob = null
        expiryTicker?.cancel()
        expiryTicker = null
        _activeKey.value = null
        _activeMessages.value = emptyList()
        lastActiveDocs = emptyList()
        _screen.value = Screen.Home
    }

    fun setForeground(foreground: Boolean) {
        isForeground = foreground
        AppState.foreground = foreground
        typingClient.setActive(foreground && sessionUid != null)
        if (!foreground) {
            sendTypingStop()
            return
        }
        val myUid = sessionUid
        if (myUid != null) {
            // Bildirimdeki "Görüldü" / "Cevapla" düğmeleri okundu işaretini doğrudan kaydedebilir.
            val stored = loadMarks(PREFIX_READ)
            _readMarks.update { current ->
                (current.keys + stored.keys).associateWith { maxOf(current[it] ?: 0L, stored[it] ?: 0L) }
            }
            _mutes.value = loadMarks(ChatPrefs.PREFIX_MUTE)
        }
        val key = _activeKey.value
        if (myUid != null && key != null && _screen.value.let { it is Screen.Chat || it is Screen.ChatInfo }) {
            notifications.cancelFor(key)
            markIncomingAsRead(chatIdFor(key, myUid), myUid, lastActiveDocs)
        }
        pendingApk?.let { apk ->
            if (updater.canInstallPackages()) {
                pendingApk = null
                runCatching { updater.install(apk) }
            }
        }
    }

    // ------------------------------------------------------------------ Şifre çözme

    /** Grup mesajının bu cihaz için sarılmış anahtarını açar (önbellekli). */
    private fun messageKey(doc: MessageDoc, myUid: String): ByteArray? {
        val wrapped = doc.keys[myUid] ?: return null
        val cacheKey = "${doc.id}:${wrapped.hashCode()}"
        return synchronized(groupKeyCache) {
            if (groupKeyCache.containsKey(cacheKey)) groupKeyCache[cacheKey]
            else crypto.unwrapKey(wrapped, doc.senderKey).also { groupKeyCache[cacheKey] = it }
        }
    }

    /** Mesaja bağlı bir şifreli alanı (içerik, alıntı, tepki, oy) çözer. */
    private fun decryptField(doc: MessageDoc, myUid: String, cipher: String, cacheKey: String): String? =
        synchronized(decryptCache) {
            if (decryptCache.containsKey(cacheKey)) return@synchronized decryptCache[cacheKey]
            val plain = if (doc.keys.isNotEmpty()) {
                messageKey(doc, myUid)?.let { crypto.decryptWithKey(cipher, it) }
            } else {
                crypto.decryptMessage(cipher, doc.senderKey, doc.recipientKey)
            }
            decryptCache[cacheKey] = plain
            plain
        }

    private fun decryptContent(doc: MessageDoc, myUid: String): String? =
        if (doc.content.isBlank()) null else decryptField(doc, myUid, doc.content, "${doc.id}:${doc.content.hashCode()}")

    /** Mesaja bağlı küçük bir veriyi (tepki, oy) mesajla aynı anahtarla şifreler. */
    private fun encryptForDoc(doc: MessageDoc, myUid: String, plain: String): String? =
        if (doc.keys.isNotEmpty()) {
            messageKey(doc, myUid)?.let { crypto.encryptWithKey(plain, it) }
        } else {
            crypto.encryptForMessage(plain, doc.senderKey, doc.recipientKey)
        }

    private fun MessageDoc.toUi(myUid: String): UiMessage {
        val fromMe = senderId == myUid
        val pendingStatus = if (isPending) MessageStatus.PENDING else MessageStatus.SENT
        if (messageType == SystemNotes.MESSAGE_TYPE) {
            return UiMessage(
                id = id, text = null, isFromMe = fromMe, timestamp = createdAt, status = pendingStatus,
                senderUid = senderId, systemText = systemText ?: ""
            )
        }
        if (messageType == ScreenshotNotice.MESSAGE_TYPE) {
            return UiMessage(
                id = id,
                text = null,
                isFromMe = fromMe,
                timestamp = createdAt,
                status = pendingStatus,
                isScreenshotNotice = true,
                senderUid = senderId
            )
        }
        val plain = decryptContent(this, myUid)
        val image = if (messageType == "IMAGE") {
            ImageMeta.parse(plain)?.let { meta ->
                val local = media.hasLocal(meta.mediaId)
                ImageAttachment(
                    mediaId = meta.mediaId,
                    chunks = meta.chunks,
                    width = meta.width,
                    height = meta.height,
                    size = meta.size,
                    thumbBase64 = meta.thumbBase64,
                    localPath = if (local) media.localFile(meta.mediaId).absolutePath else null,
                    state = if (local) ImageState.READY else _imageStates.value[meta.mediaId] ?: ImageState.DOWNLOADING
                )
            }
        } else null
        val poll = if (messageType == Polls.MESSAGE_TYPE) pollView(this, myUid, plain) else null
        val isGroupMessage = keys.isNotEmpty()
        return UiMessage(
            id = id,
            text = when {
                messageType == "IMAGE" -> null
                poll != null -> poll.question
                else -> plain
            },
            isFromMe = fromMe,
            timestamp = createdAt,
            status = when {
                isPending -> MessageStatus.PENDING
                !isGroupMessage && deliveryStatus == "READ" -> MessageStatus.READ
                else -> MessageStatus.SENT
            },
            image = image,
            isAssistant = isAssistant,
            reactions = decryptReactions(this, myUid),
            reply = replyQuote(this, myUid),
            senderUid = senderId,
            poll = poll,
            expiresAt = expiresAt
        )
    }

    private fun pollView(doc: MessageDoc, myUid: String, plain: String?): PollView? {
        val spec = Polls.parse(plain) ?: return null
        val byOption = HashMap<Int, MutableList<String>>()
        var mine = emptySet<Int>()
        doc.votes.forEach { (uid, cipher) ->
            val choices = Polls.decodeVote(
                decryptField(doc, myUid, cipher, "v:${doc.id}:$uid:${cipher.hashCode()}"),
                spec.options.size
            ).let { if (spec.multi) it else it.take(1).toSet() }
            if (uid == myUid) mine = choices
            choices.forEach { byOption.getOrPut(it) { mutableListOf() }.add(uid) }
        }
        return PollView(spec.question, spec.options, spec.multi, spec.isMatch, byOption, mine)
    }

    private fun decryptReactions(doc: MessageDoc, myUid: String): Map<String, String> =
        doc.reactions.mapNotNull { (uid, cipher) ->
            decryptField(doc, myUid, cipher, "r:${doc.id}:$uid:${cipher.hashCode()}")
                ?.takeIf { Reactions.isAllowed(it) }
                ?.let { uid to it }
        }.toMap()

    private fun replyQuote(doc: MessageDoc, myUid: String): ReplyQuote? {
        val target = doc.replyTo ?: return null
        val text = doc.replyPreview?.let {
            decryptField(doc, myUid, it, "q:${doc.id}:${it.hashCode()}")
        } ?: "🔒 Alıntı"
        return ReplyQuote(messageId = target, fromMe = doc.replyToSender == myUid, text = text, senderUid = doc.replyToSender.orEmpty())
    }

    private fun markIncomingAsRead(chatId: String, myUid: String, docs: List<MessageDoc>) {
        val latest = docs.maxOfOrNull { it.createdAt } ?: 0L
        markChatRead(chatId, latest)
        // Gruplarda okundu bilgisi her mesaja yazılmaz (kota); okunma yalnızca bu cihazda tutulur.
        if (ChatIds.isGroup(chatId)) return
        val ids = docs.filter {
            it.senderId != myUid && it.deliveryStatus != "READ" && !it.isPending && it.id !in readRequested &&
                it.messageType != SystemNotes.MESSAGE_TYPE
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
    fun sendMessage(peerUid: String, rawText: String, replyTo: UiMessage? = null): Boolean {
        val text = rawText.trim()
        if (text.isEmpty()) return false
        if (text.toByteArray(Charsets.UTF_8).size > MAX_MESSAGE_BYTES) {
            _events.tryEmit("Mesaj çok uzun. Lütfen daha kısa parçalar halinde gönderin.")
            return false
        }
        val myUid = sessionUid
        val reply = replyTo?.takeIf {
            myUid != null && !it.isScreenshotNotice && it.systemText == null && !it.id.startsWith("upload_")
        }?.let {
            val sender = if (it.isFromMe) myUid!! else it.senderUid.ifBlank { peerUid }
            ReplyDraft(it.id, sender, ReplySnippets.of(it.poll?.let { p -> "📊 ${p.question}" } ?: it.text, it.image != null))
        }
        val sent = sendEncrypted(peerUid, text, previewOf(text), "TEXT", isAssistant = false, reply = reply)
        if (sent) sendTypingStop()
        if (sent && isAssistantMention(text) && _config.value.assistantEnabled) {
            askAssistantInChat(peerUid, AssistantMention.question(text))
        }
        return sent
    }

    /** Mesaj "@dakk" ile başlıyorsa asistan sohbete katılır. */
    fun isAssistantMention(text: String): Boolean = AssistantMention.isMention(text)

    /**
     * Ortak gönderim yolu: içeriği şifreler, Firestore'a yazar ve sunucu varsa
     * karşı tarafa anlık bildirim gönderilmesini ister. [peerUid] grup kimliği de olabilir.
     */
    private fun sendEncrypted(
        peerUid: String,
        plain: String,
        preview: String,
        messageType: String,
        isAssistant: Boolean,
        reply: ReplyDraft? = null,
        updateChat: Boolean = true,
        notifyPeer: Boolean = true,
        presetKey: ByteArray? = null
    ): Boolean {
        if (ChatIds.isGroup(peerUid)) {
            return sendGroupEncrypted(peerUid, plain, preview, messageType, isAssistant, reply, updateChat, notifyPeer, presetKey)
        }
        val myUid = sessionUid ?: return false
        val peer = _profiles.value[peerUid]
        if (peer == null || peer.publicKey.isBlank()) {
            _events.tryEmit("Bu kişi henüz Dakk'ın güncel sürümüne giriş yapmamış. Şifreli mesaj gönderilemiyor.")
            return false
        }
        val myName = myDisplayName(myUid)
        val chatId = ChatIds.of(myUid, peerUid)
        val expiresAt = if (messageType == ScreenshotNotice.MESSAGE_TYPE) 0L
        else Disappearing.expiryFor(chatDoc(chatId)?.disappearSeconds ?: 0L, System.currentTimeMillis())

        viewModelScope.launch {
            try {
                val (content, encPreview, myKey) = withContext(Dispatchers.Default) {
                    Triple(
                        crypto.encrypt(plain, peer.publicKey),
                        crypto.encrypt(preview, peer.publicKey),
                        crypto.publicKeyBase64()
                    )
                }
                val replyFields = reply?.let {
                    val encQuote = withContext(Dispatchers.Default) { crypto.encrypt(it.snippet, peer.publicKey) }
                    FirestoreRepository.ReplyFields(it.messageId, it.senderUid, encQuote)
                }
                repo.sendMessage(
                    chatId = chatId,
                    myUid = myUid,
                    peerUid = peerUid,
                    senderName = myName,
                    encryptedContent = content,
                    encryptedPreview = encPreview,
                    senderKey = myKey,
                    recipientKey = peer.publicKey,
                    messageType = messageType,
                    isAssistant = isAssistant,
                    reply = replyFields,
                    updateChat = updateChat,
                    expiresAt = expiresAt,
                    onSent = { messageId ->
                        val server = _config.value.serverUrl
                        if (notifyPeer && server.isNotBlank()) {
                            viewModelScope.launch { push.notifyMessage(server, chatId, messageId) }
                        }
                    }
                ) { _events.tryEmit("Mesaj gönderilemedi. Lütfen tekrar deneyin.") }
            } catch (e: Exception) {
                Log.e(TAG, "Şifreleme hatası", e)
                _events.tryEmit("Mesaj şifrelenemedi.")
            }
        }
        return true
    }

    /**
     * Grup mesajı: rastgele bir mesaj anahtarı üretilir, içerik bu anahtarla şifrelenir ve anahtar
     * her üye için ayrı ayrı sarılır.
     */
    private fun sendGroupEncrypted(
        chatId: String,
        plain: String,
        preview: String,
        messageType: String,
        isAssistant: Boolean,
        reply: ReplyDraft?,
        updateChat: Boolean,
        notifyPeer: Boolean,
        presetKey: ByteArray?
    ): Boolean {
        val myUid = sessionUid ?: return false
        val doc = chatDoc(chatId)
        if (doc == null || myUid !in doc.memberUids) {
            _events.tryEmit("Bu grubun üyesi değilsiniz.")
            return false
        }
        val myName = myDisplayName(myUid)
        val expiresAt = if (messageType == ScreenshotNotice.MESSAGE_TYPE) 0L
        else Disappearing.expiryFor(doc.disappearSeconds, System.currentTimeMillis())
        viewModelScope.launch {
            try {
                val members = doc.memberUids
                val publicKeys = memberPublicKeys(members, myUid)
                val messageKey = presetKey ?: crypto.newMessageKey()
                val encrypted = withContext(Dispatchers.Default) {
                    val keys = publicKeys.mapValues { (_, pub) -> crypto.wrapKey(messageKey, pub) }
                    Triple(
                        crypto.encryptWithKey(plain, messageKey),
                        crypto.encryptWithKey(preview, messageKey),
                        keys
                    )
                }
                val replyFields = reply?.let {
                    FirestoreRepository.ReplyFields(it.messageId, it.senderUid, crypto.encryptWithKey(it.snippet, messageKey))
                }
                repo.sendGroupMessage(
                    chatId = chatId,
                    myUid = myUid,
                    senderName = myName,
                    encryptedContent = encrypted.first,
                    encryptedPreview = encrypted.second,
                    senderKey = crypto.publicKeyBase64(),
                    keys = encrypted.third,
                    messageType = messageType,
                    isAssistant = isAssistant,
                    reply = replyFields,
                    updateChat = updateChat,
                    expiresAt = expiresAt,
                    onSent = { messageId ->
                        val server = _config.value.serverUrl
                        if (notifyPeer && server.isNotBlank()) {
                            viewModelScope.launch { push.notifyMessage(server, chatId, messageId) }
                        }
                    }
                ) { _events.tryEmit("Mesaj gönderilemedi. Lütfen tekrar deneyin.") }
            } catch (e: Exception) {
                Log.e(TAG, "Grup şifreleme hatası", e)
                _events.tryEmit("Mesaj şifrelenemedi.")
            }
        }
        return true
    }

    /** Üyelerin açık anahtarları (henüz yüklenmemiş profiller sunucudan okunur). Benim anahtarım cihazdan alınır. */
    private suspend fun memberPublicKeys(members: List<String>, myUid: String): Map<String, String> {
        val result = HashMap<String, String>()
        result[myUid] = withContext(Dispatchers.Default) { crypto.publicKeyBase64() }
        members.filter { it != myUid }.forEach { uid ->
            val known = _profiles.value[uid]?.publicKey?.ifBlank { null }
            val key = known ?: runCatching { repo.getProfile(uid) }.getOrNull()?.also { p ->
                _profiles.update { it + (uid to p) }
            }?.publicKey?.ifBlank { null }
            if (key != null) result[uid] = key
        }
        return result
    }

    /** Yanıtlanan mesajın gönderim sırasında gereken bilgisi. */
    data class ReplyDraft(val messageId: String, val senderUid: String, val snippet: String)

    /** Oturumdaki kullanıcının kimliği (tepkilerde "benim tepkim" için). */
    val currentUid: String? get() = sessionUid

    /**
     * Bir mesaja tepki bırakır. Aynı emojiye yeniden dokunulursa tepki kaldırılır.
     * Tepki, mesajla aynı anahtarlarla şifrelenir; sunucu ve Dakk hangi emojinin seçildiğini göremez.
     */
    fun react(peerUid: String, messageId: String, emoji: String) {
        val myUid = sessionUid ?: return
        if (!Reactions.isAllowed(emoji)) return
        val chatId = chatIdFor(peerUid, myUid)
        val doc = lastActiveDocs.firstOrNull { it.id == messageId } ?: return
        if (doc.isPending || doc.messageType == ScreenshotNotice.MESSAGE_TYPE || doc.messageType == SystemNotes.MESSAGE_TYPE) return
        val current = decryptReactions(doc, myUid)[myUid]
        val next = Reactions.toggle(current, emoji)
        viewModelScope.launch {
            try {
                val cipher = next?.let {
                    withContext(Dispatchers.Default) { encryptForDoc(doc, myUid, it) }
                        ?: run {
                            _events.tryEmit("Bu mesaja tepki verilemiyor (farklı bir cihazda şifrelenmiş).")
                            return@launch
                        }
                }
                repo.setReaction(chatId, messageId, myUid, cipher)
            } catch (e: Exception) {
                Log.w(TAG, "Tepki kaydedilemedi", e)
                _events.tryEmit("Tepki gönderilemedi.")
            }
        }
    }

    private var lastScreenshotAt = 0L

    /**
     * Sohbet açıkken ekran görüntüsü alındığında karşı tarafa sohbet içinde küçük bir not bırakır.
     * Ekran görüntüsü engellenmez. Sohbet listesi önizlemesi ve bildirim etkilenmez.
     */
    fun onScreenCaptured() {
        val myUid = sessionUid ?: return
        val key = (_screen.value as? Screen.Chat)?.peerUid ?: return
        val chatId = chatIdFor(key, myUid)
        if (_chatDocs.value.none { it.chatId == chatId }) return
        val now = System.currentTimeMillis()
        if (now - lastScreenshotAt < ScreenshotNotice.MIN_INTERVAL_MS) return
        lastScreenshotAt = now
        sendEncrypted(
            key, ScreenshotNotice.LEGACY_TEXT, ScreenshotNotice.LEGACY_TEXT, ScreenshotNotice.MESSAGE_TYPE,
            isAssistant = false, updateChat = false, notifyPeer = false
        )
    }

    /** Galeriden seçilen fotoğrafı sıkıştırır, şifreler ve gönderir. */
    fun sendImage(peerUid: String, uri: Uri) {
        val myUid = sessionUid ?: return
        if (!_config.value.imagesEnabled) {
            _events.tryEmit("Fotoğraf gönderimi şu an kapalı.")
            return
        }
        val isGroup = ChatIds.isGroup(peerUid)
        val peer = if (isGroup) null else _profiles.value[peerUid]
        if (isGroup) {
            val doc = chatDoc(peerUid)
            if (doc == null || myUid !in doc.memberUids) {
                _events.tryEmit("Bu grubun üyesi değilsiniz.")
                return
            }
        } else if (peer == null || peer.publicKey.isBlank()) {
            _events.tryEmit("Bu kişi henüz Dakk'ın güncel sürümüne giriş yapmamış. Şifreli mesaj gönderilemiyor.")
            return
        }
        val chatId = chatIdFor(peerUid, myUid)
        val mediaId = media.newMediaId()
        val placeholderId = "upload_$mediaId"
        viewModelScope.launch {
            try {
                val prepared = media.prepare(uri)
                val placeholder = UiMessage(
                    id = placeholderId,
                    text = null,
                    isFromMe = true,
                    timestamp = System.currentTimeMillis(),
                    status = MessageStatus.PENDING,
                    image = ImageAttachment(
                        mediaId, 1, prepared.width, prepared.height, prepared.jpeg.size.toLong(),
                        prepared.thumbBase64, null, ImageState.UPLOADING
                    ),
                    senderUid = myUid
                )
                _uploads.update { it + (peerUid to (it[peerUid].orEmpty() + placeholder)) }
                if (isGroup) {
                    val messageKey = crypto.newMessageKey()
                    val meta = media.upload(chatId, myUid, mediaId, prepared) { crypto.encryptBytesWithKey(it, messageKey) }
                    sendEncrypted(peerUid, meta.toJson(), "📷 Fotoğraf", "IMAGE", isAssistant = false, presetKey = messageKey)
                } else {
                    repo.ensureChat(chatId, myUid, peerUid)
                    val meta = media.upload(chatId, myUid, peer!!.publicKey, mediaId, prepared)
                    sendEncrypted(peerUid, meta.toJson(), "📷 Fotoğraf", "IMAGE", isAssistant = false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fotoğraf gönderilemedi", e)
                _events.tryEmit("Fotoğraf gönderilemedi. İnternet bağlantınızı kontrol edip tekrar deneyin.")
            } finally {
                _uploads.update { map ->
                    val rest = map[peerUid].orEmpty().filterNot { it.id == placeholderId }
                    if (rest.isEmpty()) map - peerUid else map + (peerUid to rest)
                }
            }
        }
    }

    // ------------------------------------------------------------------ "Yazıyor…"

    /** Giriş alanı değiştikçe çağrılır; yazma durumu sohbetin diğer üyelerine iletilir. */
    fun onDraftChanged(key: String, text: String) {
        val myUid = sessionUid ?: return
        typingClient.sendTyping(chatIdFor(key, myUid), text.isNotBlank())
    }

    private fun sendTypingStop() {
        val myUid = sessionUid ?: return
        val key = _activeKey.value ?: return
        typingClient.sendTyping(chatIdFor(key, myUid), false)
    }

    private fun typingTopics(docs: List<ChatDoc>, profiles: Map<String, UserProfile>, myUid: String): Map<String, String> =
        docs.sortedByDescending { it.lastMessageTime }
            .take(MAX_TYPING_CHANNELS)
            .mapNotNull { doc ->
                val topic = if (isGroupDoc(doc)) {
                    crypto.typingTopicForGroup(doc.chatId, doc.typingKey)
                } else {
                    val peerKey = peerOf(doc, myUid)?.let { profiles[it]?.publicKey }?.ifBlank { null }
                    peerKey?.let { crypto.typingTopicForPeer(doc.chatId, it) }
                }
                topic?.let { doc.chatId to it }
            }.toMap()

    // ------------------------------------------------------------------ Anketler

    /** Gruba anket gönderir. */
    fun sendPoll(key: String, spec: Polls.Spec): Boolean {
        if (!ChatIds.isGroup(key)) return false
        return sendEncrypted(key, spec.toJson(), previewOf("📊 ${spec.question}"), Polls.MESSAGE_TYPE, isAssistant = false)
    }

    /** Anket seçeneğine oy verir; aynı seçeneğe yeniden dokunmak oyu geri alır. */
    fun vote(key: String, messageId: String, option: Int) {
        val myUid = sessionUid ?: return
        val chatId = chatIdFor(key, myUid)
        val doc = lastActiveDocs.firstOrNull { it.id == messageId } ?: return
        if (doc.isPending) return
        val poll = _activeMessages.value.firstOrNull { it.id == messageId }?.poll ?: return
        if (option !in poll.options.indices) return
        val next = Polls.toggle(poll.myChoices, option, poll.multi)
        viewModelScope.launch {
            try {
                val cipher = if (next.isEmpty()) null else withContext(Dispatchers.Default) {
                    encryptForDoc(doc, myUid, Polls.encodeVote(next))
                } ?: run {
                    _events.tryEmit("Bu ankete oy verilemiyor (farklı bir cihazda şifrelenmiş).")
                    return@launch
                }
                repo.setVote(chatId, messageId, myUid, cipher)
            } catch (e: Exception) {
                Log.w(TAG, "Oy kaydedilemedi", e)
                _events.tryEmit("Oy gönderilemedi.")
            }
        }
    }

    /** Maç anketinde "Geliyorum" diyenleri rastgele iki takıma böler ve sonucu gruba yazar. */
    fun buildTeams(key: String, messageId: String) {
        val chat = activeChat.value ?: return
        val poll = _activeMessages.value.firstOrNull { it.id == messageId }?.poll ?: return
        val coming = poll.votes[0].orEmpty()
        if (coming.size < 2) {
            _events.tryEmit("Takım kurmak için en az 2 kişinin \"${poll.options.firstOrNull() ?: "Geliyorum"}\" demesi gerekiyor.")
            return
        }
        val names = coming.map { uid -> chat.member(uid)?.name ?: "Dakk Kullanıcısı" }
        val (a, b) = Polls.splitTeams(names)
        val text = Polls.teamsMessage(a, b)
        sendEncrypted(key, text, previewOf(text), "TEXT", isAssistant = false)
    }

    // ------------------------------------------------------------------ Dakk Asistan

    fun hasAiConsent(scope: String): Boolean = prefs.getBoolean(PREFIX_AI_CONSENT + scope, false)

    fun giveAiConsent(scope: String) {
        prefs.edit().putBoolean(PREFIX_AI_CONSENT + scope, true).apply()
    }

    /** Günlük asistan hakkını kullanır; hak kalmadıysa false. Kurucular sınırsızdır. */
    private fun consumeAssistantQuota(): Boolean {
        val limit = _config.value.assistantDailyLimit
        if (limit <= 0 || _isFounder.value) return true
        val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        val key = PREFIX_AI_COUNT + day
        val used = prefs.getInt(key, 0)
        if (used >= limit) {
            _events.tryEmit("Bugünkü asistan kullanım hakkınız doldu ($limit). Yarın tekrar deneyebilirsiniz.")
            return false
        }
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(PREFIX_AI_COUNT) && it != key }.forEach { editor.remove(it) }
        editor.putInt(key, used + 1).apply()
        return true
    }

    fun openAssistant() {
        if (!assistantHistoryLoaded) {
            assistantHistoryLoaded = true
            viewModelScope.launch {
                _assistantTurns.value = withContext(Dispatchers.IO) { assistant.loadHistory() }
            }
        }
        _screen.value = Screen.Assistant
    }

    fun sendToAssistant(rawText: String): Boolean {
        val text = rawText.trim()
        if (text.isEmpty() || _assistantBusy.value) return false
        if (!_config.value.assistantEnabled) {
            _events.tryEmit("Dakk Asistan şu an kullanılamıyor.")
            return false
        }
        if (!consumeAssistantQuota()) return false
        val history = _assistantTurns.value
        _assistantTurns.value = history + AssistantTurn("user", text, System.currentTimeMillis())
        _assistantBusy.value = true
        viewModelScope.launch {
            try {
                val reply = assistant.reply(_config.value, privateConfig, history, text)
                _assistantTurns.update { it + AssistantTurn("model", reply, System.currentTimeMillis()) }
            } catch (e: Exception) {
                _events.tryEmit((e as? AssistantException)?.message ?: "Asistan şu an yanıt veremiyor.")
                _assistantTurns.update { list -> if (list.lastOrNull()?.role == "user") list.dropLast(1) else list }
            } finally {
                _assistantBusy.value = false
                val snapshot = _assistantTurns.value
                withContext(Dispatchers.IO) { assistant.saveHistory(snapshot) }
            }
        }
        return true
    }

    fun clearAssistant() {
        _assistantTurns.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) { assistant.clearHistory() }
    }

    /** @dakk ile çağrıldığında sohbetin son mesajlarını bağlam olarak kullanıp yanıtı sohbete ekler. */
    private fun askAssistantInChat(peerUid: String, question: String) {
        if (_chatAssistantBusy.value != null) {
            _events.tryEmit("Dakk Asistan başka bir yanıt hazırlıyor, biraz bekleyin.")
            return
        }
        if (!consumeAssistantQuota()) return
        val myUid = sessionUid ?: return
        val chat = activeChat.value?.takeIf { it.key == peerUid }
        val isGroup = ChatIds.isGroup(peerUid)
        val myName = chat?.member(myUid)?.name ?: _profiles.value[myUid]?.displayName?.ifBlank { null } ?: "Ben"
        val peerName = if (isGroup) (chat?.title ?: "grup")
        else chat?.member(peerUid)?.name ?: _profiles.value[peerUid]?.displayName?.ifBlank { null } ?: "Karşı taraf"
        val transcript = _activeMessages.value.filterNot { it.isScreenshotNotice || it.systemText != null }
            .takeLast(CHAT_CONTEXT_MESSAGES).joinToString("\n") { m ->
            val who = when {
                m.isAssistant -> "Dakk Asistan"
                m.isFromMe -> myName
                else -> chat?.member(m.senderUid)?.name ?: peerName
            }
            val body = when {
                m.image != null -> "[fotoğraf]"
                m.poll != null -> "[anket: ${m.poll.question}]"
                m.text == null -> "[okunamayan mesaj]"
                else -> m.text
            }
            "$who: $body"
        }
        val prompt = buildString {
            append("Sohbetin son mesajları:\n")
            append(transcript.ifBlank { "(henüz mesaj yok)" })
            append("\n\n$myName seni çağırdı ve şunu soruyor: ")
            append(question.ifBlank { "Bu sohbete katkı yap." })
        }
        _chatAssistantBusy.value = peerUid
        viewModelScope.launch {
            try {
                val reply = assistant.reply(
                    _config.value, privateConfig, emptyList(), prompt,
                    extraInstruction = (if (isGroup) "Şu an \"$peerName\" adlı bir grup sohbetine @dakk yazılarak çağrıldın. " +
                        "Yanıtın gruba eklenecek ve tüm üyeler görecek. "
                    else "Şu an $myName ile $peerName arasındaki bir sohbete @dakk yazılarak çağrıldın. " +
                        "Yanıtın sohbete eklenecek ve iki kişi de görecek. ") + "Kısa ve doğal yaz, kendini tanıtma."
                )
                val fitted = fitBytes(reply, MAX_MESSAGE_BYTES)
                sendEncrypted(peerUid, fitted, previewOf("🤖 $fitted"), "TEXT", isAssistant = true)
            } catch (e: Exception) {
                _events.tryEmit((e as? AssistantException)?.message ?: "Asistan şu an yanıt veremiyor.")
            } finally {
                _chatAssistantBusy.value = null
            }
        }
    }

    private fun fitBytes(text: String, maxBytes: Int): String {
        if (text.toByteArray(Charsets.UTF_8).size <= maxBytes) return text
        var end = text.length
        while (end > 0 && text.substring(0, end).toByteArray(Charsets.UTF_8).size > maxBytes - 4) end -= 50
        return text.substring(0, end.coerceAtLeast(0)).trimEnd() + "…"
    }

    // ------------------------------------------------------------------ Duyurular & Kurucu paneli

    fun dismissAnnouncement(announcement: Announcement) {
        if (_previewAnnouncement.value != null) {
            _previewAnnouncement.value = null
            return
        }
        val seen = (_seenAnnouncements.value + announcement.id).toList().takeLast(30).toSet()
        _seenAnnouncements.value = seen
        prefs.edit().putString(PREF_SEEN_ANNOUNCEMENTS, seen.joinToString("\n")).apply()
    }

    fun previewAnnouncement(announcement: Announcement) {
        _previewAnnouncement.value = announcement.copy(id = announcement.id.ifBlank { "preview" })
    }

    private fun loadSeenAnnouncements(): Set<String> =
        prefs.getString(PREF_SEEN_ANNOUNCEMENTS, "").orEmpty().split('\n').filter { it.isNotBlank() }.toSet()

    fun openFounderPanel() {
        if (_isFounder.value) _screen.value = Screen.FounderPanel
    }

    /** Panelde ayarlar kaydedilince kurucu tarafında da hemen geçerli olsun. */
    fun onFounderConfigSaved() {
        viewModelScope.launch { refreshFounderStatus() }
    }

    fun closeSecondaryScreen() {
        _screen.value = Screen.Home
    }

    fun deleteMessage(peerUid: String, messageId: String) {
        val myUid = sessionUid ?: return
        val chatId = chatIdFor(peerUid, myUid)
        val isGroup = ChatIds.isGroup(peerUid)
        val wasLast = _activeMessages.value.lastOrNull { it.systemText == null && !it.isScreenshotNotice }?.id == messageId
        val image = _activeMessages.value.firstOrNull { it.id == messageId }?.image
        viewModelScope.launch {
            try {
                repo.deleteMessage(chatId, messageId)
                if (image != null) {
                    media.deleteMedia(chatId, ImageMeta(image.mediaId, image.chunks, 0, 0, 0, null))
                }
                if (!wasLast) return@launch
                if (isGroup) {
                    val doc = chatDoc(chatId) ?: return@launch
                    val messageKey = crypto.newMessageKey()
                    val keys = memberPublicKeys(doc.memberUids, myUid)
                    val fields = withContext(Dispatchers.Default) {
                        mapOf(
                            "lastMessage" to crypto.encryptWithKey("Bu mesaj silindi", messageKey),
                            "lastMessageSenderId" to myUid,
                            "lastMessageSenderKey" to crypto.publicKeyBase64(),
                            "lastMessageKeys" to keys.mapValues { (_, pub) -> crypto.wrapKey(messageKey, pub) },
                            "lastMessageExpiresAt" to 0L
                        )
                    }
                    repo.updateChatFields(chatId, fields)
                } else {
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
        val chatId = chatIdFor(peerUid, myUid)
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
                if (blocked && _activeKey.value == peerUid) closeChat()
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

    // ------------------------------------------------------------------ Gruplar

    /** Seçilen kişilerle yeni grup kurar ve grubu açar. */
    fun createGroup(name: String, photoUri: Uri?, members: List<String>) {
        val myUid = sessionUid ?: return
        val cleanName = name.trim().take(MAX_GROUP_NAME)
        val others = members.filter { it != myUid }.distinct()
        when {
            cleanName.isEmpty() -> { _events.tryEmit("Grup adı yazın."); return }
            others.isEmpty() -> { _events.tryEmit("Gruba en az bir kişi ekleyin."); return }
            others.size + 1 > MAX_GROUP_MEMBERS -> { _events.tryEmit("Bir grupta en fazla $MAX_GROUP_MEMBERS kişi olabilir."); return }
        }
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val photo = photoUri?.let { uri ->
                    runCatching { android.util.Base64.encodeToString(media.prepareAvatar(uri), android.util.Base64.NO_WRAP) }
                        .getOrNull()
                }.orEmpty()
                val chatId = ChatIds.newGroupId()
                repo.createGroup(
                    chatId = chatId,
                    myUid = myUid,
                    name = cleanName,
                    photo = photo,
                    members = others,
                    typingKey = ChatIds.randomToken(32),
                    inviteCode = ChatIds.randomToken(16)
                )
                repo.sendSystemNote(chatId, myUid, myDisplayName(myUid), SystemNotes.groupCreated(myDisplayName(myUid), cleanName))
                openChat(chatId)
            } catch (e: Exception) {
                Log.e(TAG, "Grup oluşturulamadı", e)
                _events.tryEmit("Grup oluşturulamadı. İnternet bağlantınızı kontrol edin.")
            } finally {
                _busy.value = false
            }
        }
    }

    fun openNewGroup(preselected: List<String> = emptyList()) {
        _screen.value = Screen.NewGroup(preselected = preselected)
    }

    fun openAddMembers(groupId: String) {
        _screen.value = Screen.NewGroup(addToGroup = groupId)
    }

    /** Yeni grup ekranından geri dönüş. */
    fun closeNewGroup() {
        val key = _activeKey.value
        _screen.value = if (key != null) Screen.ChatInfo(key) else Screen.Home
    }

    private fun requireAdmin(chatId: String): ChatDoc? {
        val myUid = sessionUid ?: return null
        val doc = chatDoc(chatId)
        if (doc == null || myUid !in doc.admins) {
            _events.tryEmit("Bu işlemi yalnızca grup yöneticileri yapabilir.")
            return null
        }
        return doc
    }

    private fun groupAction(failure: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                Log.w(TAG, failure, e)
                _events.tryEmit(failure)
            }
        }
    }

    fun addMembers(groupId: String, uids: List<String>) {
        val myUid = sessionUid ?: return
        val doc = requireAdmin(groupId) ?: return
        val fresh = uids.filter { it !in doc.memberUids }.distinct()
        if (fresh.isEmpty()) return
        if (doc.memberUids.size + fresh.size > MAX_GROUP_MEMBERS) {
            _events.tryEmit("Bir grupta en fazla $MAX_GROUP_MEMBERS kişi olabilir.")
            return
        }
        groupAction("Kişiler eklenemedi.") {
            repo.addMembers(groupId, fresh)
            val names = fresh.map { displayNameOf(it) }
            repo.sendSystemNote(groupId, myUid, myDisplayName(myUid), SystemNotes.added(myDisplayName(myUid), names))
            _screen.value = Screen.ChatInfo(groupId)
        }
    }

    fun removeMember(groupId: String, uid: String) {
        val myUid = sessionUid ?: return
        requireAdmin(groupId) ?: return
        if (uid == myUid) return leaveGroup(groupId)
        groupAction("Kişi gruptan çıkarılamadı.") {
            val name = memberName(groupId, uid)
            repo.removeMember(groupId, uid)
            // Çıkarılan kişi "yazıyor…" kanalını da dinleyemesin.
            repo.updateChatFields(groupId, mapOf("typingKey" to ChatIds.randomToken(32)))
            repo.sendSystemNote(groupId, myUid, myDisplayName(myUid), SystemNotes.removed(myDisplayName(myUid), name))
        }
    }

    fun setAdmin(groupId: String, uid: String, admin: Boolean) {
        val myUid = sessionUid ?: return
        val doc = requireAdmin(groupId) ?: return
        if (!admin && doc.admins.size <= 1 && uid in doc.admins) {
            _events.tryEmit("Grupta en az bir yönetici kalmalı.")
            return
        }
        groupAction("Yönetici ayarı değiştirilemedi.") {
            val value = if (admin) com.google.firebase.firestore.FieldValue.arrayUnion(uid)
            else com.google.firebase.firestore.FieldValue.arrayRemove(uid)
            repo.updateChatFields(groupId, mapOf("admins" to value))
            val name = memberName(groupId, uid)
            val note = if (admin) SystemNotes.madeAdmin(myDisplayName(myUid), name) else SystemNotes.removedAdmin(myDisplayName(myUid), name)
            repo.sendSystemNote(groupId, myUid, myDisplayName(myUid), note)
        }
    }

    fun renameGroup(groupId: String, name: String) {
        val myUid = sessionUid ?: return
        val doc = requireAdmin(groupId) ?: return
        val clean = name.trim().take(MAX_GROUP_NAME)
        if (clean.isEmpty() || clean == doc.name) return
        groupAction("Grup adı değiştirilemedi.") {
            repo.updateChatFields(groupId, mapOf("name" to clean))
            repo.sendSystemNote(groupId, myUid, myDisplayName(myUid), SystemNotes.renamed(myDisplayName(myUid), clean))
        }
    }

    /** Grup fotoğrafını değiştirir ([uri] null ise kaldırır). */
    fun setGroupPhoto(groupId: String, uri: Uri?) {
        val myUid = sessionUid ?: return
        requireAdmin(groupId) ?: return
        groupAction("Grup fotoğrafı değiştirilemedi.") {
            val photo = uri?.let { android.util.Base64.encodeToString(media.prepareAvatar(it), android.util.Base64.NO_WRAP) }
            repo.updateChatFields(groupId, mapOf("photo" to (photo ?: "")))
            repo.sendSystemNote(groupId, myUid, myDisplayName(myUid), SystemNotes.photoChanged(myDisplayName(myUid)))
        }
    }

    fun setInviteEnabled(groupId: String, enabled: Boolean) {
        val myUid = sessionUid ?: return
        requireAdmin(groupId) ?: return
        groupAction("Davet bağlantısı ayarlanamadı.") {
            repo.updateChatFields(groupId, mapOf("inviteEnabled" to enabled))
            repo.sendSystemNote(groupId, myUid, myDisplayName(myUid), SystemNotes.inviteChanged(myDisplayName(myUid), enabled))
        }
    }

    /** Davet bağlantısını yeniler; eski bağlantı artık çalışmaz. */
    fun resetInviteLink(groupId: String) {
        requireAdmin(groupId) ?: return
        groupAction("Davet bağlantısı yenilenemedi.") {
            repo.updateChatFields(groupId, mapOf("inviteCode" to ChatIds.randomToken(16)))
            _events.tryEmit("Yeni davet bağlantısı oluşturuldu. Eski bağlantı artık çalışmaz.")
        }
    }

    fun leaveGroup(groupId: String) {
        val myUid = sessionUid ?: return
        val doc = chatDoc(groupId) ?: return
        val others = doc.memberUids.filter { it != myUid }
        val promote = if (doc.admins == listOf(myUid) && others.isNotEmpty()) others.first() else null
        groupAction("Gruptan ayrılınamadı.") {
            repo.sendSystemNote(groupId, myUid, myDisplayName(myUid), SystemNotes.left(myDisplayName(myUid)))
            repo.leaveGroup(groupId, myUid, promote)
            if (_activeKey.value == groupId) closeChat()
            _events.tryEmit("Gruptan ayrıldınız")
        }
    }

    private fun offerGroupInvite(invite: InviteLinks.GroupInvite) {
        val myUid = sessionUid ?: return
        val doc = chatDoc(invite.groupId)
        if (doc != null && myUid in doc.memberUids) {
            openChat(invite.groupId)
            return
        }
        _groupInvite.value = invite
    }

    fun dismissGroupInvite() {
        _groupInvite.value = null
    }

    fun joinGroup(invite: InviteLinks.GroupInvite) {
        val myUid = sessionUid ?: return
        if (_joiningGroup.value) return
        _joiningGroup.value = true
        viewModelScope.launch {
            try {
                repo.joinGroup(invite.groupId, myUid, invite.code)
                repo.sendSystemNote(invite.groupId, myUid, myDisplayName(myUid), SystemNotes.joined(myDisplayName(myUid)))
                _groupInvite.value = null
                openChat(invite.groupId)
            } catch (e: Exception) {
                Log.w(TAG, "Gruba katılınamadı", e)
                _groupInvite.value = null
                _events.tryEmit("Gruba katılınamadı. Davet bağlantısı kapatılmış, yenilenmiş veya grup dolu olabilir.")
            } finally {
                _joiningGroup.value = false
            }
        }
    }

    // ------------------------------------------------------------------ Sohbet ayarları

    /** Birebir sohbet belgesi henüz yoksa oluşturur (takma ad, kaybolan mesaj gibi ayarlar için). */
    private suspend fun ensureChatFor(key: String, myUid: String): String {
        val chatId = chatIdFor(key, myUid)
        if (!ChatIds.isGroup(key) && chatDoc(chatId) == null) repo.ensureChat(chatId, myUid, key)
        return chatId
    }

    /**
     * Takma ad koyar/kaldırır. Takma adlar sohbetteki herkese görünür.
     * Birebirde iki taraf da birbirine (ve kendine) takma ad verebilir; grupta herkes kendine,
     * yöneticiler herkese takma ad verebilir.
     */
    fun setNickname(key: String, targetUid: String, nickname: String?) {
        val myUid = sessionUid ?: return
        val clean = nickname?.trim()?.take(MAX_NICKNAME)?.ifBlank { null }
        if (ChatIds.isGroup(key)) {
            val doc = chatDoc(key) ?: return
            if (targetUid != myUid && myUid !in doc.admins) {
                _events.tryEmit("Başkalarının takma adını yalnızca grup yöneticileri değiştirebilir.")
                return
            }
        } else if (targetUid != myUid && targetUid != key) return
        val targetName = displayNameOf(targetUid)
        groupAction("Takma ad kaydedilemedi.") {
            val chatId = ensureChatFor(key, myUid)
            val current = chatDoc(chatId)?.nicknames?.get(targetUid)
            if (current == clean) return@groupAction
            repo.setNickname(chatId, targetUid, clean)
            val note = SystemNotes.nickname(myDisplayName(myUid), targetUid == myUid, targetName, clean)
            repo.sendSystemNote(chatId, myUid, myDisplayName(myUid), note)
        }
    }

    fun setDisappearing(key: String, seconds: Long) {
        val myUid = sessionUid ?: return
        if (!Disappearing.isValid(seconds)) return
        groupAction("Kaybolan mesaj ayarı kaydedilemedi.") {
            val chatId = ensureChatFor(key, myUid)
            if ((chatDoc(chatId)?.disappearSeconds ?: 0L) == seconds) return@groupAction
            repo.setDisappearing(chatId, seconds)
            repo.sendSystemNote(chatId, myUid, myDisplayName(myUid), SystemNotes.disappearing(myDisplayName(myUid), seconds))
        }
    }

    /** Sohbeti sessize alır ([duration] null ise sesi açar). Yalnızca bu cihazı etkiler. */
    fun setMute(key: String, duration: Long?) {
        val myUid = sessionUid ?: return
        val chatId = chatIdFor(key, myUid)
        val until = duration?.let { MuteOptions.until(it, System.currentTimeMillis()) } ?: 0L
        if (until > 0) {
            prefs.edit().putLong(ChatPrefs.PREFIX_MUTE + chatId, until).apply()
            _mutes.update { it + (chatId to until) }
            notifications.cancelFor(key)
            _events.tryEmit(if (duration == MuteOptions.FOREVER) "Sohbet sessize alındı" else "Sohbet sessize alındı (${MuteOptions.label(until, System.currentTimeMillis())})")
        } else {
            prefs.edit().remove(ChatPrefs.PREFIX_MUTE + chatId).apply()
            _mutes.update { it - chatId }
            _events.tryEmit("Sohbetin sesi açıldı")
        }
    }

    /** Birebir sohbette kişiye yalnızca bu cihazda görünen bir fotoğraf verir ([uri] null ise kaldırır). */
    fun setCustomPhoto(peerUid: String, uri: Uri?) {
        if (ChatIds.isGroup(peerUid)) return
        viewModelScope.launch {
            try {
                if (uri == null) media.deleteCustomPhoto(peerUid) else media.saveCustomPhoto(peerUid, uri)
                _customPhotos.update {
                    if (uri == null) it - peerUid else it + (peerUid to System.currentTimeMillis())
                }
                _events.tryEmit(if (uri == null) "Özel fotoğraf kaldırıldı" else "Fotoğraf yalnızca bu cihazda değiştirildi")
            } catch (e: Exception) {
                _events.tryEmit("Fotoğraf değiştirilemedi.")
            }
        }
    }

    private fun loadCustomPhotos() {
        val dir = media.customPhotoFile("x").parentFile ?: return
        _customPhotos.value = dir.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".jpg") }
            .associate { it.name.removeSuffix(".jpg") to it.lastModified() }
    }

    // ------------------------------------------------------------------ Kişi ekleme

    fun myInviteLink(): String? = sessionUid?.let { InviteLinks.build(BuildConfig.INVITE_BASE_URL, it) }

    fun handleIncomingLink(link: String) {
        InviteLinks.parseGroup(link)?.let { invite ->
            if (sessionUid == null) pendingGroupInvite = invite else offerGroupInvite(invite)
            return
        }
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
            sessionUid?.let { uid -> withContext(Dispatchers.IO) { push.unregister(uid) } }
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

    fun openReleasePage() {
        _update.value.release?.let { updater.openReleasePage(it) }
    }

    /** Zorunlu güncelleme ekranı: sürüm bilgisi alınamadıysa son sürüm sayfasını açar. */
    fun openReleasesPage() {
        val release = _update.value.release
        if (release != null) {
            updater.openReleasePage(release)
        } else {
            runCatching {
                getApplication<Application>().startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/${BuildConfig.GITHUB_REPO}/releases/latest")
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
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
                if (updater.hasSameSignature(apk) == false) {
                    // Android farklı imzalı APK'yı mevcut kurulumun üzerine yüklemez.
                    updater.deleteDownloads()
                    _update.update { it.copy(downloading = false, progress = 0, showDialog = true, signatureMismatch = true) }
                    return@launch
                }
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

    private fun isGroupDoc(doc: ChatDoc): Boolean = doc.isGroup || ChatIds.isGroup(doc.chatId)

    /** Sohbet anahtarından (kişi veya grup kimliği) Firestore sohbet kimliği. */
    private fun chatIdFor(key: String, myUid: String): String =
        if (ChatIds.isGroup(key)) key else ChatIds.of(myUid, key)

    private fun chatDoc(chatId: String): ChatDoc? = _chatDocs.value.firstOrNull { it.chatId == chatId }

    /** Takma ad yoksa profil adı, o da yoksa kişi listesindeki ad. */
    private fun resolveName(
        uid: String,
        nicknames: Map<String, String>,
        profiles: Map<String, UserProfile>,
        contacts: List<Contact>
    ): String =
        nicknames[uid]?.ifBlank { null }
            ?: profiles[uid]?.displayName?.ifBlank { null }
            ?: contacts.firstOrNull { it.uid == uid }?.alias?.ifBlank { null }
            ?: "Dakk Kullanıcısı"

    /** Takma ad olmadan kişinin adı. */
    private fun displayNameOf(uid: String): String =
        resolveName(uid, emptyMap(), _profiles.value, _rawContacts.value)

    private fun myDisplayName(myUid: String): String =
        _profiles.value[myUid]?.displayName?.ifBlank { null } ?: _user.value?.displayName ?: "Dakk Kullanıcısı"

    private fun memberName(chatId: String, uid: String): String =
        resolveName(uid, chatDoc(chatId)?.nicknames.orEmpty(), _profiles.value, _rawContacts.value)

    private val groupPhotoCache = HashMap<Int, ByteArray?>()

    /** Base64 grup fotoğrafını bayt dizisine çevirir (önbellekli). */
    private fun groupPhotoBytes(photo: String): ByteArray? {
        if (photo.isBlank()) return null
        val key = photo.hashCode()
        return synchronized(groupPhotoCache) {
            groupPhotoCache.getOrPut(key) {
                runCatching { android.util.Base64.decode(photo, android.util.Base64.NO_WRAP) }.getOrNull()
            }
        }
    }

    /** Birebir sohbette kişinin fotoğrafı: bu cihazdaki özel fotoğraf, yoksa profil fotoğrafı. */
    private fun peerPhotoModel(uid: String, url: String?, customPhotos: Map<String, Long>): Any? =
        if (customPhotos.containsKey(uid) && media.hasCustomPhoto(uid)) LocalPhoto(media.customPhotoFile(uid), customPhotos[uid] ?: 0L)
        else url?.ifBlank { null }

    private fun buildActiveChat(
        key: String,
        docs: List<ChatDoc>,
        profiles: Map<String, UserProfile>,
        contacts: List<Contact>,
        mutes: Map<String, Long>,
        customPhotos: Map<String, Long>
    ): ActiveChat? {
        val myUid = sessionUid ?: return null
        val chatId = chatIdFor(key, myUid)
        val doc = docs.firstOrNull { it.chatId == chatId }
        val isGroup = ChatIds.isGroup(key)
        val nicknames = doc?.nicknames.orEmpty()
        val memberUids = when {
            isGroup -> doc?.memberUids.orEmpty()
            else -> listOf(myUid, key)
        }
        val admins = doc?.admins.orEmpty()
        val members = memberUids.map { uid ->
            val p = profiles[uid]
            val contact = contacts.firstOrNull { it.uid == uid }
            val isMe = uid == myUid
            ChatMember(
                uid = uid,
                name = nicknames[uid]?.ifBlank { null }
                    ?: (if (isMe) myDisplayName(myUid) else null)
                    ?: p?.displayName?.ifBlank { null } ?: contact?.alias?.ifBlank { null } ?: "Dakk Kullanıcısı",
                realName = (if (isMe) myDisplayName(myUid) else null)
                    ?: p?.displayName?.ifBlank { null } ?: contact?.alias?.ifBlank { null } ?: "Dakk Kullanıcısı",
                nickname = nicknames[uid]?.ifBlank { null },
                photoUrl = p?.photoUrl?.ifBlank { null } ?: contact?.photoUrl.orEmpty(),
                email = p?.email?.ifBlank { null } ?: contact?.email.orEmpty(),
                isAdmin = uid in admins,
                isMe = isMe,
                hasKey = isMe || !p?.publicKey.isNullOrBlank()
            )
        }.sortedWith(compareByDescending<ChatMember> { it.isMe }.thenByDescending { it.isAdmin }.thenBy { it.name.lowercase() })
        val peer = if (isGroup) null else profiles[key]
        val peerMember = members.firstOrNull { it.uid == key }
        val isMember = if (isGroup) doc != null && myUid in doc.memberUids else true
        val amAdmin = isGroup && myUid in admins
        return ActiveChat(
            key = key,
            chatId = chatId,
            isGroup = isGroup,
            title = if (isGroup) doc?.name?.ifBlank { null } ?: "Grup" else peerMember?.name ?: "Dakk Kullanıcısı",
            photoModel = if (isGroup) groupPhotoBytes(doc?.photo.orEmpty()) else peerPhotoModel(key, peerMember?.photoUrl, customPhotos),
            subtitle = if (isGroup) "${memberUids.size} kişi" else peerMember?.email.orEmpty(),
            members = members,
            amAdmin = amAdmin,
            isMember = isMember,
            exists = doc != null,
            disappearSeconds = doc?.disappearSeconds ?: 0L,
            muteUntil = mutes[chatId] ?: 0L,
            inviteEnabled = doc?.inviteEnabled == true,
            inviteLink = if (isGroup && doc != null && doc.inviteCode.isNotBlank()) {
                InviteLinks.buildGroup(BuildConfig.INVITE_BASE_URL, chatId, doc.inviteCode)
            } else null,
            canSend = if (isGroup) isMember else peer != null && peer.publicKey.isNotBlank(),
            hasCustomPhoto = !isGroup && customPhotos.containsKey(key),
            peer = peer
        )
    }

    private fun buildChatSummaries(
        docs: List<ChatDoc>,
        profiles: Map<String, UserProfile>,
        contacts: List<Contact>,
        marks: ListMarks
    ): List<ChatSummary> {
        val myUid = sessionUid ?: return emptyList()
        val blocked = contacts.filter { it.isBlocked }.map { it.uid }.toSet()
        val now = System.currentTimeMillis()
        return docs.mapNotNull { doc ->
            val isGroup = isGroupDoc(doc)
            if (!isGroup && doc.lastMessage.isBlank() && doc.lastMessageTime == 0L) return@mapNotNull null
            if (doc.lastMessageTime <= (marks.clears[doc.chatId] ?: 0L) && !(isGroup && doc.lastMessage.isBlank())) {
                return@mapNotNull null
            }
            val fromMe = doc.lastMessageSenderId == myUid
            val expired = Disappearing.isExpired(doc.lastMessageExpiresAt, now)
            val muted = MuteOptions.isMuted(marks.mutes[doc.chatId] ?: 0L, now)
            val unread = !fromMe && doc.lastMessageSenderId.isNotBlank() && doc.lastMessageTime > (marks.reads[doc.chatId] ?: 0L)
            if (isGroup) {
                val sender = resolveName(doc.lastMessageSenderId, doc.nicknames, profiles, contacts)
                val text = when {
                    doc.lastMessage.isBlank() -> "Grup oluşturuldu"
                    expired -> "⏱ Kaybolan mesaj"
                    else -> decryptGroupPreview(doc, myUid) ?: "🔒 Şifreli mesaj"
                }
                val preview = if (doc.lastMessage.isBlank()) text else if (fromMe) "Sen: $text" else "$sender: $text"
                ChatSummary(
                    chatId = doc.chatId,
                    peerUid = doc.chatId,
                    peerName = doc.name.ifBlank { "Grup" },
                    peerPhotoUrl = "",
                    preview = preview,
                    lastMessageTime = doc.lastMessageTime,
                    lastFromMe = fromMe && doc.lastMessage.isNotBlank(),
                    isUnread = unread && doc.lastMessage.isNotBlank(),
                    isGroup = true,
                    isMuted = muted,
                    photoModel = groupPhotoBytes(doc.photo)
                )
            } else {
                val peerUid = peerOf(doc, myUid) ?: return@mapNotNull null
                if (peerUid in blocked) return@mapNotNull null
                val profile = profiles[peerUid]
                val contact = contacts.firstOrNull { it.uid == peerUid }
                val url = profile?.photoUrl?.ifBlank { null } ?: contact?.photoUrl.orEmpty()
                ChatSummary(
                    chatId = doc.chatId,
                    peerUid = peerUid,
                    peerName = resolveName(peerUid, doc.nicknames, profiles, contacts),
                    peerPhotoUrl = url,
                    preview = if (expired) "⏱ Kaybolan mesaj"
                    else crypto.decryptMessage(doc.lastMessage, doc.lastMessageSenderKey, doc.lastMessageRecipientKey)
                        ?: "🔒 Şifreli mesaj",
                    lastMessageTime = doc.lastMessageTime,
                    lastFromMe = fromMe,
                    isUnread = unread,
                    isMuted = muted,
                    photoModel = peerPhotoModel(peerUid, url, marks.photos)
                )
            }
        }.sortedWith(compareByDescending<ChatSummary> { it.lastMessageTime }.thenBy { it.chatId })
    }

    private fun decryptGroupPreview(doc: ChatDoc, myUid: String): String? {
        val wrapped = doc.lastMessageKeys[myUid] ?: return null
        val cacheKey = "gp:${doc.chatId}:${doc.lastMessage.hashCode()}"
        return synchronized(decryptCache) {
            if (decryptCache.containsKey(cacheKey)) return@synchronized decryptCache[cacheKey]
            val key = crypto.unwrapKey(wrapped, doc.lastMessageSenderKey)
            val plain = key?.let { crypto.decryptWithKey(doc.lastMessage, it) }
            decryptCache[cacheKey] = plain
            plain
        }
    }

    private fun handleNotifications(docs: List<ChatDoc>, myUid: String) {
        val editor = prefs.edit()
        val blocked = _rawContacts.value.filter { it.isBlocked }.map { it.uid }.toSet()
        docs.forEach { doc ->
            if (doc.lastMessageSenderId.isBlank() || doc.lastMessageSenderId == myUid) return@forEach
            if (doc.lastMessage.isBlank()) return@forEach
            val key = PREFIX_NOTIFIED + doc.chatId
            val last = prefs.getLong(key, -1L)
            if (doc.lastMessageTime <= last) return@forEach
            editor.putLong(key, doc.lastMessageTime)
            if (!notificationsPrimed) return@forEach

            val isGroup = isGroupDoc(doc)
            val chatKey = if (isGroup) doc.chatId else peerOf(doc, myUid) ?: return@forEach
            if (doc.lastMessageSenderId in blocked) return@forEach
            if (MuteOptions.isMuted(_mutes.value[doc.chatId] ?: 0L, System.currentTimeMillis())) return@forEach
            val viewing = isForeground && _activeKey.value == chatKey && _screen.value is Screen.Chat
            if (viewing) return@forEach
            // Uygulama arka plandaysa ve anlık bildirim etkinse bildirimi sunucu gönderir (çift bildirim olmasın).
            if (!isForeground && AppState.pushActive) return@forEach

            viewModelScope.launch {
                val text = withContext(Dispatchers.Default) {
                    if (isGroup) decryptGroupPreview(doc, myUid)
                    else crypto.decryptMessage(doc.lastMessage, doc.lastMessageSenderKey, doc.lastMessageRecipientKey)
                } ?: "Yeni şifreli mesaj"
                val sender = resolveName(doc.lastMessageSenderId, doc.nicknames, _profiles.value, _rawContacts.value)
                val title = if (isGroup) "${doc.name.ifBlank { "Grup" }} · $sender" else sender
                notifications.showMessage(chatKey, title, text, chatId = doc.chatId)
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
        private const val PREFIX_AI_CONSENT = "ai_consent_"
        private const val PREFIX_AI_COUNT = "ai_count_"
        private const val PREF_SEEN_ANNOUNCEMENTS = "seen_announcements"
        private const val CHAT_CONTEXT_MESSAGES = 30
        const val MAX_MESSAGE_BYTES = 3_500
        const val MAX_MESSAGE_CHARS = 2_000
        const val MAX_GROUP_MEMBERS = 50
        const val MAX_GROUP_NAME = 50
        const val MAX_NICKNAME = 40
        private const val MAX_TYPING_CHANNELS = 40
    }
}
