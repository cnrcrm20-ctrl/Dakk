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
import com.example.media.VoiceClip
import com.example.media.VoiceMeta
import com.example.media.VoicePlayback
import com.example.media.VoicePlayer
import com.example.media.VoiceRecorder
import com.example.data.VoiceAttachment
import com.example.push.AppState
import com.example.push.PushClient
import com.example.auth.GoogleAuth
import com.example.crypto.CryptoEngine
import com.example.data.ChatDoc
import com.example.data.ChatIds
import com.example.data.ChatPrefs
import com.example.data.LocalMessageStore
import com.example.data.Mailbox
import com.example.data.RestoreBundle
import com.example.data.LocalPhoto
import com.example.data.PollView
import com.example.push.TypingClient
import com.example.util.DataSaver
import com.example.util.Disappearing
import com.example.util.MuteOptions
import com.example.util.Polls
import com.example.util.SystemNotes
import com.example.util.TurkishSuffix
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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
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
    /** Ayarlar → İzinler. */
    object Permissions : Screen()

    /** Geçiş animasyonu için ekran derinliği. */
    val depth: Int
        get() = when (this) {
            Home -> 0
            is Chat, Assistant, FounderPanel, Permissions -> 1
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
    val peer: UserProfile?,
    /** Sabitlenen mesajın kimliği (boşsa yok). */
    val pinnedId: String = "",
    val pinnedBy: String = ""
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

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class DakkViewModel(app: Application) : AndroidViewModel(app) {

    private val auth = Firebase.auth
    val crypto = CryptoEngine(app)
    private val repo = FirestoreRepository(app)
    private val notifications = NotificationHelper(app)
    private val updater = GitHubUpdateManager(app)
    private val prefs = app.getSharedPreferences("dakk_prefs", Context.MODE_PRIVATE)
    val configRepo = ConfigRepository(app)
    private val assistant = AssistantEngine(app)
    private val push = PushClient(repo)
    private val media = MediaManager(app, repo, crypto, push)
    private val localStore = LocalMessageStore(app)
    private val typingClient = TypingClient(viewModelScope)
    private val voiceRecorder = VoiceRecorder(app, viewModelScope)
    private val voicePlayer = VoicePlayer(viewModelScope)

    /** Sesli mesaj çalma durumu (aynı anda tek mesaj). */
    val voicePlayback: StateFlow<VoicePlayback> = voicePlayer.state
    /** Kayıt sürüyorsa geçen süre (ms), yoksa -1. */
    val voiceElapsed: StateFlow<Long> = voiceRecorder.elapsed
    /** Kayıt sırasında ses seviyesi (0–1). */
    val voiceLevel: StateFlow<Float> = voiceRecorder.level

    val appVersion: String = BuildConfig.VERSION_NAME

    /**
     * Bu kurulum 2.5.0 ile mi başladı (ilk kurulum veya yeniden kurulum)? Sihirbazın tamamı ve tanıtım turu
     * yalnızca yeni kurulumlarda gösterilir; var olan kullanıcılar yalnızca yeni adımları görür.
     */
    val isNewInstall: Boolean = prefs.getString(PREF_INSTALL_KIND, null)?.let { it == "new" } ?: run {
        val existing = crypto.hasStoredKey() || prefs.contains(PREF_LOCAL_OWNER) ||
            prefs.contains("notification_permission_asked")
        prefs.edit().putString(PREF_INSTALL_KIND, if (existing) "existing" else "new").apply()
        !existing
    }

    private val _wizardDone = MutableStateFlow(prefs.getStringSet(PREF_WIZARD_DONE, emptySet()).orEmpty().toSet())
    /** Tamamlanan (veya atlanan) kurulum sihirbazı adımları. */
    val wizardDone: StateFlow<Set<String>> = _wizardDone

    private val _tutorialVisible = MutableStateFlow(false)
    val tutorialVisible: StateFlow<Boolean> = _tutorialVisible

    /** Android 12+: Dakk Güncelleyici onay sormadan kurar. */
    val silentUpdates: Boolean get() = updater.supportsSilentInstall

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

    private val _jumpRequest = MutableStateFlow<Pair<String, String>?>(null)
    /** (sohbet anahtarı, mesaj kimliği): sohbet açılınca o mesaja kaydırılır (yıldızlı/sabitlenen mesajlar). */
    val jumpRequest: StateFlow<Pair<String, String>?> = _jumpRequest

    private val _starred = MutableStateFlow(prefs.getStringSet(PREF_STARRED, emptySet()).orEmpty().toSet())
    /** Yıldızlı mesajlar ("sohbetKimliği/mesajKimliği"). Yalnızca bu cihazda tutulur. */
    val starred: StateFlow<Set<String>> = _starred

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

    /** Veri tasarrufu ayarı (Normal / Tasarruf / Otomatik). */
    private val _dataSaver = MutableStateFlow(DataSaver.fromPref(prefs.getInt(PREF_DATA_SAVER, DataSaver.AUTO.pref)))
    val dataSaver: StateFlow<DataSaver> = _dataSaver

    /** Şu an uygulanan tasarruf seviyesi: 0 normal, 1 küçük fotoğraf + grup fotoğrafı dokununca, 2 ayrıca "yazıyor…" kapalı. */
    private val _saverLevel = MutableStateFlow(0)
    val saverLevel: StateFlow<Int> = _saverLevel

    /** Sohbet yedeği: arkadaşlar yeniden kurulumda geçmişi geri gönderir, ben de onlara gönderirim. */
    private val _chatBackup = MutableStateFlow(prefs.getBoolean(PREF_CHAT_BACKUP, true))
    val chatBackup: StateFlow<Boolean> = _chatBackup

    /** Dokunarak indirilmesi istenen grup fotoğrafları. */
    private val manualDownloads = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events

    // Uzak ayarlar (Kurucu paneli) ---------------------------------------
    private val _config = MutableStateFlow(PublicConfig())
    val config: StateFlow<PublicConfig> = _config

    // ------------------------------------------------------------------ Duyuru bandı (sunucudan)

    /** Bandın bitiş tarihini kontrol etmek için dakikada bir ilerleyen saat. */
    private val bannerClock: StateFlow<Long> = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000L)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, System.currentTimeMillis())

    private val _dismissedBanner = MutableStateFlow(prefs.getString(PREF_BANNER_DISMISSED, "").orEmpty())

    /** Sohbet listesinin üstündeki duyuru bandı (sunucudan: home_banner). */
    val homeBanner: StateFlow<PublicConfig?> = combine(_config, bannerClock, _dismissedBanner) { cfg, now, dismissed ->
        cfg.takeIf {
            it.homeBanner.isNotBlank() && (it.homeBannerUntil <= 0 || now < it.homeBannerUntil) &&
                bannerKey(it) != dismissed
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun bannerKey(cfg: PublicConfig): String = (cfg.homeBanner + "|" + cfg.homeBannerUrl).hashCode().toString()

    fun dismissHomeBanner() {
        val cfg = _config.value
        _dismissedBanner.value = bannerKey(cfg)
        prefs.edit().putString(PREF_BANNER_DISMISSED, _dismissedBanner.value).apply()
    }

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

    private var sessionUid: String? = null
    private var sessionJob: Job? = null
    private val profileJobs = mutableMapOf<String, Job>()
    private var activeChatJob: Job? = null
    private var lastActiveDocs: List<MessageDoc> = emptyList()

    /** Sunucuya henüz yazılmamış kendi oylarım: mesaj kimliği → seçimler. Hızlı dokunuşlarda oy kaybolmasın diye. */
    private val pendingVotes = java.util.concurrent.ConcurrentHashMap<String, Set<Int>>()
    private val voteMutex = Mutex()
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
    private val sweptIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val restoreMutex = Mutex()

    private val authListener = FirebaseAuth.AuthStateListener { a -> onAuthChanged(a.currentUser) }

    init {
        auth.addAuthStateListener(authListener)
        if (_autoUpdate.value) checkForUpdates(manual = false)
    }

    override fun onCleared() {
        voiceRecorder.cancel()
        voicePlayer.stop()
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

        // Telefondaki arşiv başka bir hesaba aitse temizlenir.
        if (prefs.getString(PREF_LOCAL_OWNER, null) != uid) {
            runCatching { localStore.clearAll() }
            prefs.edit().putString(PREF_LOCAL_OWNER, uid).apply()
        }

        sessionJob = viewModelScope.launch {
            launch { publishMyProfile(user) }
            launch {
                repo.observeContacts(uid).retrying("Kişiler", uid).collect { list ->
                    _rawContacts.value = list
                    AppState.rememberBlocked(getApplication(), list.filter { it.isBlocked }.map { it.uid }.toSet())
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
                            refreshSaver()
                            // Jeton, sunucunun ne desteklediği öğrenildikten sonra kaydedilir.
                            pushTokenRegistered = push.registerToken(uid, if (serverReady) cfg.serverUrl else "")
                            updatePushActive()
                        }
                    }
                    if (cfg.requiresUpdate(appVersion) && _update.value.release == null) checkForUpdates(manual = false)
                }
            }
            launch { refreshFounderStatus() }
            launch { runCatching { media.cleanupExpiredUploads() } }
            launch(Dispatchers.Default) {
                // Anahtarı değişen kişiler: bekleyen mesajlar yeni anahtarla yeniden gönderilir,
                // Sohbet yedeği açıksa ortak geçmiş geri gönderilir.
                _profiles.debounce(1_500).collect { profiles -> onProfilesChanged(profiles, uid) }
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
        media.mediaServer = if (push.supportsMedia) server else ""
    }

    fun setDataSaver(mode: DataSaver) {
        _dataSaver.value = mode
        prefs.edit().putInt(PREF_DATA_SAVER, mode.pref).apply()
        refreshSaver()
    }

    /** Ayara ve (Otomatik'te) sunucunun bildirdiği kota durumuna göre tasarruf seviyesini belirler. */
    private fun refreshSaver() {
        val level = when (_dataSaver.value) {
            DataSaver.NORMAL -> 0
            DataSaver.SAVER -> 1
            DataSaver.AUTO -> if (serverReady) push.serverSaver else 0
        }
        if (level >= 2) sendTypingStop()
        _saverLevel.value = level
        val myUid = sessionUid ?: return
        val chatId = activeChatId(myUid) ?: return
        viewModelScope.launch { rebuildActiveMessages(chatId, myUid) }
    }

    /** Kurucu yetkisini Firestore kurallarına sorar (yalnızca kurucular app_config/private okuyabilir). */
    suspend fun refreshFounderStatus() {
        try {
            privateConfig = configRepo.loadPrivateIfFounder()
            _isFounder.value = privateConfig != null
            // Sunucu, kota %70'i geçince bu listedeki kuruculara bildirim gönderir.
            val uid = sessionUid
            if (privateConfig != null && uid != null) runCatching { repo.registerFounder(uid) }
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
            val restoreAt = restoreRequestFor(user.uid, publicKey)
            repo.publishProfile(
                uid = user.uid,
                displayName = name,
                email = user.email.orEmpty(),
                photoUrl = user.photoUrl?.toString().orEmpty(),
                publicKey = publicKey,
                caps = CAPS,
                backup = _chatBackup.value,
                restoreAt = restoreAt
            )
        } catch (e: Exception) {
            Log.e(TAG, "Profil yayınlanamadı", e)
        }
    }

    /**
     * Yeniden kurulumu algılar: anahtar bu kurulumda üretildiyse ve sunucudaki profilde başka bir anahtar
     * varsa, arkadaşlardan geçmiş istenir (Sohbet yedeği açıksa). İstek zamanı bir kez belirlenir.
     */
    private suspend fun restoreRequestFor(uid: String, publicKey: String): Long {
        val at = computeRestoreRequest(uid, publicKey)
        _restorePending.value = at > 0L
        return at
    }

    private suspend fun computeRestoreRequest(uid: String, publicKey: String): Long {
        val saved = prefs.getLong(PREF_RESTORE_AT + uid, -1L)
        if (saved >= 0L) return if (_chatBackup.value) saved else 0L
        if (crypto.keyCreatedAt() == 0L) {
            prefs.edit().putLong(PREF_RESTORE_AT + uid, 0L).apply()
            return 0L
        }
        val previous = runCatching { repo.getProfile(uid) }.getOrNull() ?: return 0L
        val at = if (previous.publicKey.isNotBlank() && previous.publicKey != publicKey) System.currentTimeMillis() else 0L
        prefs.edit().putLong(PREF_RESTORE_AT + uid, at).apply()
        return if (_chatBackup.value) at else 0L
    }

    fun markWizardStep(id: String) {
        _wizardDone.update { it + id }
        prefs.edit().putStringSet(PREF_WIZARD_DONE, _wizardDone.value).apply()
    }

    fun unmarkWizardStep(id: String) {
        _wizardDone.update { it - id }
        prefs.edit().putStringSet(PREF_WIZARD_DONE, _wizardDone.value).apply()
    }

    /** Sihirbaz bitti: yeni kurulumsa 30 saniyelik tanıtım turu başlar. */
    fun finishWizard(ids: List<String>) {
        _wizardDone.update { it + ids }
        prefs.edit().putStringSet(PREF_WIZARD_DONE, _wizardDone.value).apply()
        if (isNewInstall && !prefs.getBoolean(PREF_TUTORIAL_DONE, false)) {
            _tab.value = HomeTab.CHATS
            _tutorialVisible.value = true
        }
    }

    fun finishTutorial() {
        prefs.edit().putBoolean(PREF_TUTORIAL_DONE, true).apply()
        _tutorialVisible.value = false
    }

    /** Ayarlar → "Tanıtım turunu izle". */
    fun replayTutorial() {
        _screen.value = Screen.Home
        _tab.value = HomeTab.CHATS
        _tutorialVisible.value = true
    }

    private val _restorePending = MutableStateFlow(false)
    /** Bu kurulum arkadaşlardan sohbet geçmişi istedi mi? (Sihirbazın geri yükleme adımı için.) */
    val restorePending: StateFlow<Boolean> = _restorePending

    fun openPermissions() {
        _screen.value = Screen.Permissions
    }

    fun setChatBackup(enabled: Boolean) {
        _chatBackup.value = enabled
        prefs.edit().putBoolean(PREF_CHAT_BACKUP, enabled).apply()
        auth.currentUser?.let { u -> viewModelScope.launch { publishMyProfile(u) } }
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
        voiceRecorder.cancel()
        voicePlayer.stop()
        _activeKey.value = key
        activeChatJob?.cancel()
        _activeMessages.value = emptyList()
        lastActiveDocs = emptyList()
        markChatRead(chatId, 0L)

        activeChatJob = viewModelScope.launch {
            // Önce telefondaki arşiv: sohbet internet beklemeden açılır.
            var local = withContext(Dispatchers.IO) { runCatching { localStore.load(chatId) }.getOrDefault(emptyList()) }
            if (local.isNotEmpty()) {
                lastActiveDocs = local
                rebuildActiveMessages(chatId, myUid)
            }
            // 2.4.0'a geçişte her sohbetin geçmişi bir kez tamamen alınır; sonra yalnızca son mesajlar dinlenir.
            val synced = withContext(Dispatchers.IO) { runCatching { localStore.isSynced(chatId) }.getOrDefault(false) }
            // Posta kutusu kararı üyelerin sürümüne bağlı; profiller gelmeden pencere işlenmesin.
            awaitMemberProfiles(chatId, myUid)
            _chatDocs
                .map { docs -> docs.any { it.chatId == chatId } }
                .distinctUntilChanged()
                .flatMapLatest { exists ->
                    when {
                        !exists -> flowOf<List<MessageDoc>?>(null)
                        synced -> repo.observeRecentMessages(chatId, LocalMessageStore.LIVE_WINDOW).retrying("Mesajlar", myUid)
                        else -> repo.observeMessages(chatId).retrying("Mesajlar", myUid)
                    }
                }
                .collect { serverWindow ->
                    if (serverWindow == null) {
                        lastActiveDocs = local
                        rebuildActiveMessages(chatId, myUid)
                        return@collect
                    }
                    val complete = !synced || serverWindow.size < LocalMessageStore.LIVE_WINDOW
                    val restored = consumeRestoreNotes(chatId, myUid, serverWindow)
                    if (restored.isNotEmpty()) {
                        // Geri yüklenen geçmiş sunucuda yok; bu sohbette arşiv artık telefondadır.
                        prefs.edit().putBoolean(PREFIX_MAILBOX + chatId, true).apply()
                        val restoredIds = restored.map { it.id }.toSet()
                        local = (local.filter { it.id !in restoredIds } + restored).sortedBy { it.createdAt }
                    }
                    val mailbox = isMailboxChat(chatId, myUid)
                    val window = preferReadableLocal(serverWindow, local, myUid)
                    val merged = LocalMessageStore.merge(local, window, complete, mailbox)
                    lastActiveDocs = merged
                    rebuildActiveMessages(chatId, myUid)
                    if (isForeground) markIncomingAsRead(chatId, myUid, window)
                    local = merged.filterNot { it.isPending }
                    withContext(Dispatchers.IO) {
                        runCatching {
                            if (synced) localStore.applyWindow(chatId, window, complete, mailbox)
                            else localStore.replaceAll(chatId, window, mailbox)
                        }.onFailure { Log.w(TAG, "Mesajlar telefona kaydedilemedi", it) }
                    }
                    if (mailbox) sweepMailbox(chatId, myUid, window)
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

    private fun membersOf(chatId: String): List<String>? =
        chatDoc(chatId)?.memberUids?.takeIf { it.isNotEmpty() }
            ?: if (ChatIds.isGroup(chatId)) null else chatId.split("_").takeIf { it.size == 2 }

    /** Sohbetteki herkes 2.5.0+ mı? (Sıkıştırma, WebP aktarımı ve sunucudan kaldırma buna bağlı.) */
    private fun allMembersSupportV2(chatId: String, myUid: String): Boolean {
        val members = membersOf(chatId) ?: return false
        val profiles = _profiles.value
        return members.all { it == myUid || (profiles[it]?.caps ?: 0) >= CAPS_PACK }
    }

    /** Sohbetteki herkes en az [level] özellik düzeyinde mi? Değilse eski sürümdeki ilk kişinin adı döner. */
    private fun firstMemberBelow(chatId: String, myUid: String, level: Int): String? {
        val members = membersOf(chatId) ?: return null
        val profiles = _profiles.value
        val old = members.firstOrNull { it != myUid && (profiles[it]?.caps ?: 0) < level } ?: return null
        return displayNameOf(old)
    }

    /**
     * Posta kutusu sohbeti: sunucu yalnızca teslim edilmemiş mesajları tutar, arşiv telefondadır. Bir kez
     * öyle olduğu görülen sohbet bu cihazda öyle kalır (sunucudan kaldırılan mesajlar silinmiş sanılmasın).
     */
    private fun isMailboxChat(chatId: String, myUid: String): Boolean {
        if (prefs.getBoolean(PREFIX_MAILBOX + chatId, false)) return true
        if (!allMembersSupportV2(chatId, myUid)) return false
        prefs.edit().putBoolean(PREFIX_MAILBOX + chatId, true).apply()
        return true
    }

    private suspend fun awaitMemberProfiles(chatId: String, myUid: String) {
        val members = (membersOf(chatId) ?: return).filter { it != myUid }
        members.forEach { ensureProfile(it) }
        withTimeoutOrNull(4_000) { _profiles.first { p -> members.all { it in p } } }
    }

    /** Okunmuş ve günü geçmiş kendi mesajlarımı sunucudan kaldırır (telefonlarda kalırlar). */
    private fun sweepMailbox(chatId: String, myUid: String, window: List<MessageDoc>) {
        if (!allMembersSupportV2(chatId, myUid)) return
        val isGroup = ChatIds.isGroup(chatId)
        val now = System.currentTimeMillis()
        val fromWindow = Mailbox.sweepable(window, myUid, isGroup, now).filter { it !in sweptIds }
        // Birebir sohbette okuduğum ve telefonuma kaydettiğim gelen mesajlar da (gönderen çevrimdışı kalsa bile).
        val peerUid = if (isGroup) null else membersOf(chatId)?.firstOrNull { it != myUid }
        val received = if (peerUid == null) emptyList()
        else Mailbox.receivedSweepable(window, myUid, now).filter { it !in sweptIds }
        val deepDue = now - prefs.getLong(PREFIX_SWEPT + chatId, 0L) > SWEEP_INTERVAL_MS
        if (fromWindow.isEmpty() && received.isEmpty() && !deepDue) return
        sweptIds += fromWindow
        sweptIds += received
        viewModelScope.launch {
            runCatching { repo.deleteMessages(chatId, fromWindow) }
                .onFailure { Log.w(TAG, "Teslim edilen mesajlar sunucudan kaldırılamadı", it) }
            runCatching { repo.deleteMessages(chatId, received) }
                .onFailure { Log.w(TAG, "Okunan mesajlar sunucudan kaldırılamadı", it) }
            if (!deepDue) return@launch
            prefs.edit().putLong(PREFIX_SWEPT + chatId, now).apply()
            // Pencerenin dışında kalan eski mesajlar: haftada bir, en fazla 200 tanesi.
            runCatching {
                val mine = repo.myOldMessages(chatId, myUid, if (isGroup) null else "READ", 200)
                val ids = Mailbox.sweepable(mine, myUid, isGroup, now).filter { it !in sweptIds }
                sweptIds += ids
                ids.chunked(400).forEach { repo.deleteMessages(chatId, it) }
            }.onFailure { Log.w(TAG, "Eski mesajlar sunucudan kaldırılamadı", it) }
            if (peerUid != null) runCatching {
                // Yalnızca telefonda zaten olanlar silinir.
                val stored = withContext(Dispatchers.IO) { localStore.load(chatId) }.map { it.id }.toSet()
                val theirs = repo.myOldMessages(chatId, peerUid, "READ", 200).filter { it.id in stored }
                val ids = Mailbox.receivedSweepable(theirs, myUid, now).filter { it !in sweptIds }
                sweptIds += ids
                ids.chunked(400).forEach { repo.deleteMessages(chatId, it) }
            }.onFailure { Log.w(TAG, "Eski gelen mesajlar sunucudan kaldırılamadı", it) }
        }
    }

    /**
     * Profiller değişince: anahtarı değişen kişilere bekleyen mesajları yeniden gönder; yeni kurulumda
     * geçmiş isteyenlere (Sohbet yedeği açıksa) ortak sohbetleri geri gönder.
     */
    private suspend fun onProfilesChanged(profiles: Map<String, UserProfile>, myUid: String) {
        if (sessionUid != myUid) return
        val changed = ArrayList<Triple<UserProfile, String, String>>()
        val editor = prefs.edit()
        profiles.values.forEach { p ->
            if (p.uid == myUid || p.publicKey.isBlank()) return@forEach
            val known = prefs.getString(PREFIX_PEER_KEY + p.uid, null)
            if (known != p.publicKey) editor.putString(PREFIX_PEER_KEY + p.uid, p.publicKey)
            if (known != null && known != p.publicKey) changed += Triple(p, known, p.publicKey)
        }
        editor.apply()
        changed.forEach { (p, old, new) ->
            runCatching { resendPendingTo(p, old, new, myUid) }.onFailure { Log.w(TAG, "Bekleyen mesajlar yeniden gönderilemedi", it) }
        }
        val now = System.currentTimeMillis()
        profiles.values
            .filter { it.uid != myUid }
            .filter { RestoreBundle.shouldServe(it, prefs.getLong(PREFIX_RESTORE_SERVED + it.uid, 0L), _chatBackup.value, now) }
            .forEach { p ->
                restoreMutex.withLock {
                    runCatching { serveRestore(p, myUid) }.onFailure { Log.w(TAG, "Sohbet geçmişi gönderilemedi", it) }
                }
            }
    }

    /**
     * Karşı taraf uygulamayı yeniden kurduysa eski anahtarla şifrelenmiş ve henüz okunmamış mesajlarımı açamaz.
     * Bu mesajlar (telefonumda çözülebildikleri için) yeni anahtarla yeniden şifrelenip yerinde güncellenir.
     */
    private suspend fun resendPendingTo(peer: UserProfile, oldKey: String, newKey: String, myUid: String) {
        val myKey = withContext(Dispatchers.Default) { crypto.publicKeyBase64() }
        val pack = peer.caps >= CAPS_PACK
        val direct = ChatIds.of(myUid, peer.uid)
        if (chatDoc(direct) != null) {
            val docs = repo.recentMessagesOnce(direct, 100)
            var resent = 0
            docs.filter {
                it.senderId == myUid && it.recipientKey == oldKey && it.senderKey == myKey && it.deliveryStatus == "SENT" &&
                    !it.deleted && it.messageType in RESENDABLE_TYPES
            }.forEach { d ->
                val plain = crypto.decryptMessage(d.content, d.senderKey, d.recipientKey) ?: return@forEach
                val fields = HashMap<String, Any>()
                fields["content"] = withContext(Dispatchers.Default) { crypto.encrypt(plain, newKey, pack) }
                fields["recipientKey"] = newKey
                d.replyPreview?.let { q ->
                    crypto.decryptMessage(q, d.senderKey, d.recipientKey)?.let { fields["replyPreview"] = crypto.encrypt(it, newKey) }
                }
                runCatching { repo.updateMessageFields(direct, d.id, fields) }.onSuccess { resent++ }
            }
            val chat = chatDoc(direct)
            if (chat != null && chat.lastMessageSenderId == myUid && chat.lastMessageRecipientKey == oldKey) {
                crypto.decryptMessage(chat.lastMessage, chat.lastMessageSenderKey, chat.lastMessageRecipientKey)?.let { preview ->
                    repo.sendPreviewOnly(direct, myUid, peer.uid, crypto.encrypt(preview, newKey, pack), myKey, newKey)
                }
            }
            if (resent > 0) Log.i(TAG, "$resent bekleyen mesaj yeni anahtarla yeniden gönderildi")
        }
        // Gruplar: son 7 gündeki mesajlarımın anahtarı bu kişi için yeniden sarılır.
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        _chatDocs.value.filter { isGroupDoc(it) && peer.uid in it.memberUids && myUid in it.memberUids }.forEach { g ->
            runCatching {
                repo.recentMessagesOnce(g.chatId, 60)
                    .filter { it.senderId == myUid && it.createdAt >= weekAgo && !it.deleted && peer.uid in it.keys }
                    .forEach { d ->
                        val key = messageKey(d, myUid) ?: return@forEach
                        repo.updateMessageFields(g.chatId, d.id, mapOf("keys.${peer.uid}" to crypto.wrapKey(key, newKey)))
                    }
            }
        }
    }

    /** Ortak sohbetlerin geçmişini, yeniden kurulum yapan kişinin yeni anahtarıyla şifreleyip gönderir. */
    private suspend fun serveRestore(peer: UserProfile, myUid: String) {
        val server = media.mediaServer
        if (server.isBlank()) return
        val chats = buildList {
            val direct = ChatIds.of(myUid, peer.uid)
            if (chatDoc(direct) != null) add(direct)
            _chatDocs.value.filter { isGroupDoc(it) && peer.uid in it.memberUids && myUid in it.memberUids }.forEach { add(it.chatId) }
        }
        val myKey = withContext(Dispatchers.Default) { crypto.publicKeyBase64() }
        val myName = myDisplayName(myUid)
        for (chatId in chats) {
            // Başka bir üye (veya diğer cihazım) zaten göndermişse tekrar gönderilmez.
            val already = runCatching { repo.recentMessagesOnce(chatId, 40) }.getOrDefault(emptyList()).any {
                it.restoreTo == peer.uid && it.createdAt >= peer.restoreAt
            }
            if (already) continue
            val entries = withContext(Dispatchers.Default) {
                val docs = withContext(Dispatchers.IO) { runCatching { localStore.load(chatId) }.getOrDefault(emptyList()) }
                docs.filter { !it.deleted && it.restoreTo == null && it.createdAt < peer.restoreAt }.mapNotNull { d -> restoreEntry(d, myUid) }
            }
            if (entries.isEmpty()) continue
            val (packed, count) = withContext(Dispatchers.Default) { RestoreBundle.encodeWithinLimit(chatId, entries) } ?: continue
            val encrypted = withContext(Dispatchers.Default) { crypto.encryptBytes(packed, peer.publicKey) }
            val mediaId = media.newMediaId()
            if (!push.uploadMedia(server, chatId, mediaId, encrypted)) continue
            val pointer = crypto.encrypt(RestoreBundle.encodePointer(RestoreBundle.Pointer(mediaId, count)), peer.publicKey)
            val peerName = resolveName(peer.uid, chatDoc(chatId)?.nicknames.orEmpty(), _profiles.value, _rawContacts.value)
            repo.sendRestoreNote(
                chatId = chatId,
                myUid = myUid,
                senderName = myName,
                systemText = "${TurkishSuffix.dative(peerName)} sohbet geçmişi gönderildi · güvenlik numarası değişti",
                restoreTo = peer.uid,
                encryptedPointer = pointer,
                senderKey = myKey,
                recipientKey = peer.publicKey
            )
            Log.i(TAG, "$count mesajlık geçmiş gönderildi")
        }
        prefs.edit().putLong(PREFIX_RESTORE_SERVED + peer.uid, peer.restoreAt).apply()
    }

    /** Arşivdeki bir mesajın geri yükleme paketindeki hali (fotoğraflar ve anketler kısa metin olarak). */
    private fun restoreEntry(d: MessageDoc, myUid: String): RestoreBundle.Entry? {
        val (kind, text) = when (d.messageType) {
            SystemNotes.MESSAGE_TYPE -> "S" to (d.systemText ?: return null)
            ScreenshotNotice.MESSAGE_TYPE -> return null
            "IMAGE" -> "T" to "📷 Fotoğraf"
            VoiceMeta.MESSAGE_TYPE -> "T" to VoiceMeta.LABEL
            Polls.MESSAGE_TYPE -> "T" to ("📊 " + (Polls.parse(decryptContent(d, myUid))?.question ?: return null))
            else -> "T" to (decryptContent(d, myUid) ?: return null)
        }
        return RestoreBundle.Entry(d.id, d.senderId, d.senderName, d.createdAt, kind, text)
    }

    /**
     * Pencerede bana gönderilmiş bir geri yükleme notu varsa paketi indirir, çözer ve mesajları telefondaki
     * arşive ekler. Mesajlar arşivde yalnızca bu cihazın anahtarıyla çözülebilecek şekilde yeniden şifrelenir.
     */
    private suspend fun consumeRestoreNotes(chatId: String, myUid: String, window: List<MessageDoc>): List<MessageDoc> {
        val notes = window.filter { it.restoreTo == myUid && it.content.isNotBlank() && !it.isPending }
        if (notes.isEmpty()) return emptyList()
        val done = prefs.getStringSet(PREF_RESTORED_NOTES, emptySet()).orEmpty()
        val fresh = notes.filter { it.id !in done }
        if (fresh.isEmpty()) return emptyList()
        val server = media.mediaServer.ifBlank { return emptyList() }
        val myKey = withContext(Dispatchers.Default) { crypto.publicKeyBase64() }
        val out = ArrayList<MessageDoc>()
        for (note in fresh) {
            if (note.recipientKey != myKey) {
                markRestoreNote(note.id)
                continue
            }
            val pointer = RestoreBundle.decodePointer(crypto.decryptMessage(note.content, note.senderKey, note.recipientKey))
            if (pointer == null) {
                markRestoreNote(note.id)
                continue
            }
            val bytes = when (val fetched = push.downloadMedia(server, chatId, pointer.mediaId)) {
                is PushClient.MediaFetch.Ok -> fetched.bytes
                PushClient.MediaFetch.Expired -> {
                    markRestoreNote(note.id)
                    continue
                }
                PushClient.MediaFetch.Failed -> continue
            }
            val docs = withContext(Dispatchers.Default) {
                val plain = crypto.decryptBytes(bytes, note.senderKey, note.recipientKey) ?: return@withContext null
                val (bundleChat, entries) = RestoreBundle.decode(plain) ?: return@withContext null
                if (bundleChat != chatId) return@withContext null
                restoredDocs(chatId, entries, myKey)
            }
            markRestoreNote(note.id)
            if (docs == null) continue
            withContext(Dispatchers.IO) { runCatching { localStore.upsert(chatId, docs) } }
            runCatching { push.deleteMedia(server, chatId, pointer.mediaId) }
            out += docs
            _events.tryEmit("Sohbet geçmişi geri yüklendi (${docs.size} mesaj)")
        }
        return out
    }

    /**
     * Sunucudaki kopya bu cihazda çözülemiyor ama telefondaki (ör. arkadaşlardan geri yüklenen) kopya
     * çözülebiliyorsa telefondaki kopya kullanılır; yoksa okunabilen geçmiş "çözülemedi" satırına dönerdi.
     */
    private fun preferReadableLocal(window: List<MessageDoc>, local: List<MessageDoc>, myUid: String): List<MessageDoc> {
        if (local.isEmpty()) return window
        val byId = local.associateBy { it.id }
        return window.map { w ->
            val l = byId[w.id] ?: return@map w
            if (w.deleted || w.isPending || w.content.isBlank() || l.content.isBlank()) return@map w
            if (decryptContent(w, myUid) == null && decryptContent(l, myUid) != null) l else w
        }
    }

    private fun markRestoreNote(id: String) {
        val done = prefs.getStringSet(PREF_RESTORED_NOTES, emptySet()).orEmpty()
        prefs.edit().putStringSet(PREF_RESTORED_NOTES, (done + id).toList().takeLast(200).toSet()).apply()
    }

    private fun restoredDocs(chatId: String, entries: List<RestoreBundle.Entry>, myKey: String): List<MessageDoc> {
        val isGroup = ChatIds.isGroup(chatId)
        val groupKey = if (isGroup) crypto.newMessageKey() else null
        val myUid = sessionUid ?: return emptyList()
        val keys = if (groupKey != null) mapOf(myUid to crypto.wrapKey(groupKey, myKey)) else emptyMap()
        return entries.map { e ->
            val system = e.kind == "S"
            val content = when {
                system -> ""
                groupKey != null -> crypto.encryptWithKey(e.text, groupKey, pack = true)
                else -> crypto.encrypt(e.text, myKey, pack = true)
            }
            MessageDoc(
                id = e.id,
                senderId = e.senderId,
                content = content,
                senderKey = myKey,
                recipientKey = if (isGroup) "" else myKey,
                deliveryStatus = "READ",
                createdAt = e.createdAt,
                isPending = false,
                messageType = if (system) SystemNotes.MESSAGE_TYPE else "TEXT",
                keys = if (system) emptyMap() else keys,
                systemText = if (system) e.text else null,
                senderName = e.senderName
            )
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

    /** Yıldızlı mesajlar listesinden: sohbete dönüp mesaja kaydırır. */
    fun openMessage(key: String, messageId: String) {
        _jumpRequest.value = key to messageId
        _screen.value = Screen.Chat(key)
    }

    fun consumeJumpRequest() {
        _jumpRequest.value = null
    }

    // ------------------------------------------------------------------ Yıldızlı mesajlar (yalnızca bu cihaz)

    fun starKey(chatId: String, messageId: String): String = "$chatId/$messageId"

    fun toggleStar(peerUid: String, messageId: String) {
        val myUid = sessionUid ?: return
        val k = starKey(chatIdFor(peerUid, myUid), messageId)
        val starredNow = k !in _starred.value
        _starred.update { if (starredNow) it + k else it - k }
        prefs.edit().putStringSet(PREF_STARRED, _starred.value).apply()
        _events.tryEmit(if (starredNow) "⭐ Yıldızlı mesajlara eklendi" else "Yıldız kaldırıldı")
    }

    /** Silinen mesajların yıldızlarını temizler. */
    private fun dropStars(chatId: String, ids: Collection<String>) {
        if (ids.isEmpty()) return
        val gone = ids.map { starKey(chatId, it) }.toSet()
        if (_starred.value.none { it in gone }) return
        _starred.update { it - gone }
        prefs.edit().putStringSet(PREF_STARRED, _starred.value).apply()
    }

    // ------------------------------------------------------------------ Sabitlenen mesaj

    /** Mesajı sohbette herkes için sabitler ([messageId] null ise sabitlemeyi kaldırır). */
    fun setPinned(key: String, messageId: String?) {
        val myUid = sessionUid ?: return
        if (messageId != null && messageId.startsWith("upload_")) return
        groupAction(if (messageId == null) "Sabitleme kaldırılamadı." else "Mesaj sabitlenemedi.") {
            val chatId = ensureChatFor(key, myUid)
            if (chatDoc(chatId)?.pinnedId.orEmpty() == messageId.orEmpty()) return@groupAction
            repo.setPinned(chatId, myUid, messageId)
            repo.sendSystemNote(chatId, myUid, myDisplayName(myUid), SystemNotes.pinned(myDisplayName(myUid), messageId != null))
        }
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
            fresh.filter { it.messageType == VoiceMeta.MESSAGE_TYPE }.forEach { doc ->
                val meta = withContext(Dispatchers.Default) { VoiceMeta.parse(decryptContent(doc, myUid)) } ?: return@forEach
                if (voicePlayback.value.id == meta.mediaId) voicePlayer.stop()
                runCatching { media.deleteVoice(chatId, meta) }
            }
            runCatching { repo.deleteMessages(chatId, fresh.map { it.id }) }
                .onFailure { Log.w(TAG, "Kaybolan mesajlar silinemedi", it) }
            dropStars(chatId, fresh.map { it.id })
            withContext(Dispatchers.IO) { runCatching { localStore.delete(chatId, fresh.map { it.id }) } }
        }
    }

    /** Görünen sohbette henüz cihazda olmayan fotoğrafları indirir. */
    private fun startImageDownloads(chatId: String, docs: List<MessageDoc>, ui: List<UiMessage>) {
        val myUid = sessionUid ?: return
        val byId = docs.associateBy { it.id }
        ui.forEach { m -> m.voice?.let { startVoiceDownload(chatId, myUid, m.id, it, byId) } }
        ui.forEach { m ->
            val image = m.image ?: return@forEach
            if (image.state != ImageState.DOWNLOADING || image.mediaId in downloadsInFlight) return@forEach
            val doc = byId[m.id] ?: return@forEach
            if (doc.isPending) return@forEach
            downloadsInFlight += image.mediaId
            viewModelScope.launch {
                val meta = ImageMeta(
                    image.mediaId, image.chunks, image.width, image.height, image.size, null, image.remote, image.transitWebp
                )
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

    /** Sesli mesajlar küçük olduğu için (dakikası ≈240 KB) veri tasarrufunda bile kendiliğinden indirilir. */
    private fun startVoiceDownload(chatId: String, myUid: String, id: String, voice: VoiceAttachment, byId: Map<String, MessageDoc>) {
        if (voice.state != ImageState.DOWNLOADING || voice.mediaId in downloadsInFlight) return
        val doc = byId[id] ?: return
        if (doc.isPending) return
        downloadsInFlight += voice.mediaId
        viewModelScope.launch {
            val meta = VoiceMeta(voice.mediaId, voice.chunks, voice.durationMs, voice.size, voice.waveform, voice.remote)
            val result = if (doc.keys.isNotEmpty()) {
                val key = messageKey(doc, myUid)
                if (key == null) DownloadResult.Failed
                else media.downloadVoice(chatId, meta, deleteAfter = false) { crypto.decryptBytesWithKey(it, key) }
            } else {
                media.downloadVoice(chatId, meta, doc.senderKey, doc.recipientKey, deleteAfter = doc.senderId != myUid)
            }
            downloadsInFlight -= voice.mediaId
            val state = when (result) {
                is DownloadResult.Ok -> ImageState.READY
                DownloadResult.Expired -> ImageState.EXPIRED
                DownloadResult.Failed -> ImageState.FAILED
            }
            _imageStates.update { it + (voice.mediaId to state) }
            rebuildActiveMessages(chatId, myUid)
        }
    }

    /** İndirilemeyen bir fotoğrafı yeniden dener. */
    fun retryImage(peerUid: String, mediaId: String) {
        val myUid = sessionUid ?: return
        manualDownloads += mediaId
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
        voiceRecorder.cancel()
        voicePlayer.stop()
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
                viewModelScope.launch(Dispatchers.IO) { runCatching { updater.installWithSession(apk) } }
            }
        }
    }

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
                senderUid = senderId, systemText = restoreTo?.let { restoreNoteText(this, it, myUid) } ?: systemText ?: ""
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
                    remote = meta.remote,
                    transitWebp = meta.transitWebp,
                    state = when {
                        local -> ImageState.READY
                        else -> _imageStates.value[meta.mediaId] ?: if (
                            keys.isNotEmpty() && !fromMe && _saverLevel.value >= 1 && meta.mediaId !in manualDownloads
                        ) ImageState.TAP_TO_DOWNLOAD else ImageState.DOWNLOADING
                    }
                )
            }
        } else null
        val voice = if (messageType == VoiceMeta.MESSAGE_TYPE) {
            VoiceMeta.parse(plain)?.let { meta ->
                val local = media.hasVoice(meta.mediaId)
                VoiceAttachment(
                    mediaId = meta.mediaId,
                    chunks = meta.chunks,
                    durationMs = meta.durationMs,
                    size = meta.size,
                    waveform = meta.waveform,
                    localPath = if (local) media.voiceFile(meta.mediaId).absolutePath else null,
                    state = if (local) ImageState.READY else _imageStates.value[meta.mediaId] ?: ImageState.DOWNLOADING,
                    remote = meta.remote
                )
            }
        } else null
        val poll = if (messageType == Polls.MESSAGE_TYPE) pollView(this, myUid, plain) else null
        val isGroupMessage = keys.isNotEmpty()
        return UiMessage(
            id = id,
            text = when {
                messageType == "IMAGE" -> null
                messageType == VoiceMeta.MESSAGE_TYPE -> if (voice == null && plain != null) "🎤 Sesli mesaj açılamadı" else null
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
            voice = voice,
            isAssistant = isAssistant,
            reactions = decryptReactions(this, myUid),
            reply = replyQuote(this, myUid),
            senderUid = senderId,
            poll = poll,
            expiresAt = expiresAt,
            lockedText = if (plain == null && isGroupMessage && myUid !in keys) {
                if (messageType == Polls.MESSAGE_TYPE) "🔒 Bu anket sen gruba katılmadan önce açıldı."
                else if (messageType == VoiceMeta.MESSAGE_TYPE) "🔒 Bu sesli mesaj sen gruba katılmadan önce gönderildi."
                else "🔒 Bu mesaj sen gruba katılmadan önce gönderildi."
            } else null
        )
    }

    /** Geri yükleme notu her kişiye kendi açısından gösterilir. */
    private fun restoreNoteText(doc: MessageDoc, to: String, myUid: String): String {
        val chatId = activeChatId(myUid)
        val nick = chatId?.let { chatDoc(it)?.nicknames }.orEmpty()
        val sender = if (doc.senderId == myUid) "Sen" else resolveName(doc.senderId, nick, _profiles.value, _rawContacts.value)
        val target = resolveName(to, nick, _profiles.value, _rawContacts.value)
        return when (myUid) {
            to -> "$sender sohbet geçmişini geri gönderdi · güvenlik numaran değişti"
            doc.senderId -> "${TurkishSuffix.dative(target)} sohbet geçmişi gönderildi · güvenlik numarası değişti"
            else -> "${TurkishSuffix.genitive(target)} güvenlik numarası değişti"
        }
    }

    private fun pollView(doc: MessageDoc, myUid: String, plain: String?): PollView? {
        val spec = Polls.parse(plain) ?: return null
        // Gruptan ayrılan üyelerin oyları sayılmaz.
        val members = activeChatId(myUid)?.let { id -> _chatDocs.value.firstOrNull { it.chatId == id } }
            ?.memberUids?.toSet()?.takeIf { it.isNotEmpty() }
        val choicesByUid = LinkedHashMap<String, Set<Int>>()
        doc.votes.forEach { (uid, cipher) ->
            if (members != null && uid !in members) return@forEach
            choicesByUid[uid] = Polls.decodeVote(
                decryptField(doc, myUid, cipher, "v:${doc.id}:$uid:${cipher.hashCode()}"),
                spec.options.size
            ).let { if (spec.multi) it else it.take(1).toSet() }
        }
        pendingVotes[doc.id]?.let { choicesByUid[myUid] = it }
        val byOption = HashMap<Int, MutableList<String>>()
        choicesByUid.forEach { (uid, choices) -> choices.forEach { byOption.getOrPut(it) { mutableListOf() }.add(uid) } }
        return PollView(spec.question, spec.options, spec.multi, spec.isMatch, byOption, choicesByUid[myUid].orEmpty())
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
                it.messageType != SystemNotes.MESSAGE_TYPE && !it.deleted
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
            ReplyDraft(
                it.id, sender,
                ReplySnippets.of(it.poll?.let { p -> "📊 ${p.question}" } ?: it.voice?.let { VoiceMeta.LABEL } ?: it.text, it.image != null)
            )
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

        // Karşı taraf 2.5.0+ ise metin şifrelemeden önce sıkıştırılır.
        val pack = peer.caps >= CAPS_PACK
        viewModelScope.launch {
            try {
                val (content, encPreview, myKey) = withContext(Dispatchers.Default) {
                    Triple(
                        crypto.encrypt(plain, peer.publicKey, pack),
                        crypto.encrypt(preview, peer.publicKey, pack),
                        crypto.publicKeyBase64()
                    )
                }
                val replyFields = reply?.let {
                    val encQuote = withContext(Dispatchers.Default) { crypto.encrypt(it.snippet, peer.publicKey, pack) }
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
                val pack = allMembersSupportV2(chatId, myUid)
                val messageKey = presetKey ?: crypto.newMessageKey()
                val encrypted = withContext(Dispatchers.Default) {
                    val keys = publicKeys.mapValues { (_, pub) -> crypto.wrapKey(messageKey, pub) }
                    Triple(
                        crypto.encryptWithKey(plain, messageKey, pack),
                        crypto.encryptWithKey(preview, messageKey, pack),
                        keys
                    )
                }
                val replyFields = reply?.let {
                    FirestoreRepository.ReplyFields(it.messageId, it.senderUid, crypto.encryptWithKey(it.snippet, messageKey, pack))
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
                if (serverReady) launch { push.refreshIfStale(_config.value.serverUrl); refreshSaver() }
                // WebP yalnızca yolda: alıcıların hepsi 2.5.0+ ise; saklanan ve paylaşılan hal her zaman JPEG.
                val prepared = media.prepare(uri, saver = _saverLevel.value >= 1, transitWebp = allMembersSupportV2(chatId, myUid))
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

    // ------------------------------------------------------------------ Sesli mesajlar (2.6.0+)

    /** Sesli mesaj düğmesi gösterilsin mi (sunucudan `feature_sesli_mesaj` ile kapatılabilir)? */
    fun voiceEnabled(): Boolean = _config.value.feature(FEATURE_VOICE, true)

    /**
     * Kaydı başlatır. Sohbetteki biri sesli mesajları desteklemeyen eski bir sürümdeyse veya mikrofon
     * açılamazsa false döner (kullanıcıya nedeni bildirilir).
     */
    fun startVoiceRecording(peerUid: String): Boolean {
        val myUid = sessionUid ?: return false
        if (!voiceEnabled()) {
            _events.tryEmit("Sesli mesajlar şu an kapalı.")
            return false
        }
        val chatId = chatIdFor(peerUid, myUid)
        if (ChatIds.isGroup(peerUid)) {
            val doc = chatDoc(peerUid)
            if (doc == null || myUid !in doc.memberUids) {
                _events.tryEmit("Bu grubun üyesi değilsiniz.")
                return false
            }
        } else {
            val peer = _profiles.value[peerUid]
            if (peer == null || peer.publicKey.isBlank()) {
                _events.tryEmit("Bu kişi henüz Dakk'ın güncel sürümüne giriş yapmamış. Şifreli mesaj gönderilemiyor.")
                return false
            }
        }
        firstMemberBelow(chatId, myUid, CAPS_VOICE)?.let { name ->
            _events.tryEmit("$name Dakk'ı güncelleyince sesli mesaj gönderebilirsin.")
            return false
        }
        voicePlayer.stop()
        sendTypingStop()
        val started = voiceRecorder.start(onLimit = { finishVoiceRecording(peerUid) })
        if (!started) _events.tryEmit("Mikrofon açılamadı. Başka bir uygulama kullanıyor olabilir.")
        return started
    }

    /** Kaydı bitirip gönderir. */
    fun finishVoiceRecording(peerUid: String) {
        if (!voiceRecorder.isRecording) return
        val clip = voiceRecorder.stop()
        if (clip == null) {
            _events.tryEmit("Kayıt çok kısa. Basılı tutarak konuş.")
            return
        }
        sendVoice(peerUid, clip)
    }

    fun cancelVoiceRecording() {
        voiceRecorder.cancel()
    }

    /** Sesli mesajı çalar/duraklatır. */
    fun toggleVoice(mediaId: String) {
        if (voiceRecorder.isRecording) return
        val file = media.voiceFile(mediaId)
        if (!file.isFile) return
        voicePlayer.toggle(mediaId, file)
    }

    fun seekVoice(mediaId: String, fraction: Float) {
        val state = voicePlayback.value
        if (state.id == mediaId) voicePlayer.seek(mediaId, fraction)
        else media.voiceFile(mediaId).takeIf { it.isFile }?.let { voicePlayer.toggle(mediaId, it, fraction) }
    }

    fun cycleVoiceSpeed() = voicePlayer.cycleSpeed()

    fun stopVoicePlayback() = voicePlayer.stop()

    /** İndirilemeyen bir sesli mesajı yeniden dener. */
    fun retryVoice(peerUid: String, mediaId: String) = retryImage(peerUid, mediaId)

    private fun sendVoice(peerUid: String, clip: VoiceClip) {
        val myUid = sessionUid ?: return
        val isGroup = ChatIds.isGroup(peerUid)
        val peer = if (isGroup) null else _profiles.value[peerUid]
        if (!isGroup && (peer == null || peer.publicKey.isBlank())) return
        val chatId = chatIdFor(peerUid, myUid)
        val mediaId = media.newMediaId()
        val placeholderId = "upload_$mediaId"
        val preview = VoiceMeta.preview(clip.durationMs)
        viewModelScope.launch {
            try {
                if (serverReady) launch { push.refreshIfStale(_config.value.serverUrl) }
                val placeholder = UiMessage(
                    id = placeholderId,
                    text = null,
                    isFromMe = true,
                    timestamp = System.currentTimeMillis(),
                    status = MessageStatus.PENDING,
                    voice = VoiceAttachment(
                        mediaId, 1, clip.durationMs, clip.file.length(), clip.waveform,
                        clip.file.absolutePath, ImageState.UPLOADING
                    ),
                    senderUid = myUid
                )
                _uploads.update { it + (peerUid to (it[peerUid].orEmpty() + placeholder)) }
                if (isGroup) {
                    val messageKey = crypto.newMessageKey()
                    val meta = media.uploadVoice(chatId, myUid, mediaId, clip) { crypto.encryptBytesWithKey(it, messageKey) }
                    sendEncrypted(peerUid, meta.toJson(), preview, VoiceMeta.MESSAGE_TYPE, isAssistant = false, presetKey = messageKey)
                } else {
                    repo.ensureChat(chatId, myUid, peerUid)
                    val meta = media.uploadVoice(chatId, myUid, peer!!.publicKey, mediaId, clip)
                    sendEncrypted(peerUid, meta.toJson(), preview, VoiceMeta.MESSAGE_TYPE, isAssistant = false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sesli mesaj gönderilemedi", e)
                clip.file.delete()
                _events.tryEmit("Sesli mesaj gönderilemedi. İnternet bağlantınızı kontrol edip tekrar deneyin.")
            } finally {
                _uploads.update { map ->
                    val rest = map[peerUid].orEmpty().filterNot { it.id == placeholderId }
                    if (rest.isEmpty()) map - peerUid else map + (peerUid to rest)
                }
            }
        }
    }

    /** Giriş alanı değiştikçe çağrılır; yazma durumu sohbetin diğer üyelerine iletilir. */
    fun onDraftChanged(key: String, text: String) {
        val myUid = sessionUid ?: return
        if (_saverLevel.value >= 2 && text.isNotBlank()) return
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
        // Ekrandaki seçim, sunucuya henüz ulaşmamış önceki dokunuşu da içerir.
        val current = pendingVotes[messageId] ?: poll.myChoices
        val next = Polls.toggle(current, option, poll.multi)
        pendingVotes[messageId] = next
        viewModelScope.launch {
            rebuildActiveMessages(chatId, myUid)
            voteMutex.withLock {
                // Arka arkaya dokunuşlarda yalnızca en son seçim yazılır.
                if (pendingVotes[messageId] != next) return@withLock
                try {
                    val cipher = if (next.isEmpty()) null else withContext(Dispatchers.Default) {
                        encryptForDoc(doc, myUid, Polls.encodeVote(next))
                    } ?: run {
                        pendingVotes.remove(messageId, next)
                        _events.tryEmit("Bu ankete oy verilemiyor (farklı bir cihazda şifrelenmiş).")
                        return@withLock
                    }
                    repo.setVote(chatId, messageId, myUid, cipher)
                } catch (e: Exception) {
                    Log.w(TAG, "Oy kaydedilemedi", e)
                    _events.tryEmit("Oy gönderilemedi.")
                }
                pendingVotes.remove(messageId, next)
            }
            rebuildActiveMessages(chatId, myUid)
        }
    }

    /** Bu maç anketi için takımlar daha önce kurulmuş mu? (Anketten sonra gelen bir "Takımlar hazır" mesajı.) */
    fun teamsAlreadyBuilt(messageId: String): Boolean {
        val myUid = sessionUid ?: return false
        val poll = lastActiveDocs.firstOrNull { it.id == messageId } ?: return false
        return lastActiveDocs.any { d ->
            d.createdAt >= poll.createdAt && d.id != poll.id && d.messageType == "TEXT" &&
                decryptContent(d, myUid)?.startsWith(Polls.TEAMS_HEADER) == true
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
        val (a, b) = Polls.splitTeams(coming.distinct())
        val nameOf = { uid: String -> chat.member(uid)?.name ?: "Dakk Kullanıcısı" }
        val text = Polls.teamsMessage(a.map(nameOf), b.map(nameOf))
        sendEncrypted(key, text, previewOf(text), "TEXT", isAssistant = false)
    }

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
                m.voice != null -> "[sesli mesaj]"
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
        val voice = _activeMessages.value.firstOrNull { it.id == messageId }?.voice
        if (voice != null && voicePlayback.value.id == voice.mediaId) voicePlayer.stop()
        viewModelScope.launch {
            try {
                // 2.5.0+ sohbetlerde mesaj arşivi telefonlarda: silme, boş bir kayıtla herkese iletilir.
                if (allMembersSupportV2(chatId, myUid)) repo.tombstoneMessage(chatId, messageId, myUid, myDisplayName(myUid))
                else repo.deleteMessage(chatId, messageId)
                withContext(Dispatchers.IO) { runCatching { localStore.delete(chatId, listOf(messageId)) } }
                dropStars(chatId, listOf(messageId))
                if ((chatDoc(chatId)?.pinnedId ?: "") == messageId) runCatching { repo.setPinned(chatId, myUid, null) }
                if (image != null) {
                    media.deleteMedia(chatId, ImageMeta(image.mediaId, image.chunks, 0, 0, 0, null, image.remote))
                }
                if (voice != null) {
                    media.deleteVoice(chatId, VoiceMeta(voice.mediaId, voice.chunks, 0, 0, emptyList(), voice.remote))
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
            val server = if (serverReady) _config.value.serverUrl else ""
            sessionUid?.let { uid -> withContext(Dispatchers.IO) { push.unregister(uid, server) } }
            withContext(Dispatchers.IO) { runCatching { localStore.clearAll() } }
            GoogleAuth.signOut(getApplication())
        }
    }

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
                    withContext(Dispatchers.IO) { updater.installWithSession(apk) }
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
            peer = peer,
            pinnedId = doc?.pinnedId.orEmpty(),
            pinnedBy = doc?.pinnedBy.orEmpty()
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
        private const val PREF_STARRED = "starred_messages"
        private const val PREF_BANNER_DISMISSED = "home_banner_dismissed"
        private const val PREF_AUTO_UPDATE = "auto_update"
        private const val PREF_DATA_SAVER = "data_saver"
        private const val PREF_LOCAL_OWNER = "local_store_owner"
        private const val PREF_NAME_PREFIX = "display_name_"
        private const val PREFIX_READ = "read_"
        private const val PREFIX_CLEARED = "cleared_"
        private const val PREFIX_NOTIFIED = "notified_"
        private const val PREFIX_AI_CONSENT = "ai_consent_"
        private const val PREFIX_AI_COUNT = "ai_count_"
        private const val PREF_SEEN_ANNOUNCEMENTS = "seen_announcements"
        const val PREF_CHAT_BACKUP = "chat_backup"
        private const val PREF_INSTALL_KIND = "install_kind"
        private const val PREF_WIZARD_DONE = "wizard_done_steps"
        private const val PREF_TUTORIAL_DONE = "tutorial_done"
        private const val PREF_RESTORE_AT = "restore_at_"
        private const val PREF_RESTORED_NOTES = "restored_notes"
        private const val PREFIX_PEER_KEY = "peer_key_"
        private const val PREFIX_RESTORE_SERVED = "restore_served_"
        private const val PREFIX_MAILBOX = "mailbox_"
        private const val PREFIX_SWEPT = "swept_"
        /**
         * Bu sürümün özellik düzeyi (profilde "caps"). Her düzey öncekileri de kapsar:
         * 1 = sıkıştırma, posta kutusu, WebP aktarımı, geri yükleme (2.5.0); 2 = sesli mesaj (2.6.0).
         */
        const val CAPS = 2
        const val CAPS_PACK = 1
        const val CAPS_VOICE = 2
        const val FEATURE_VOICE = "sesli_mesaj"
        private const val SWEEP_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
        private val RESENDABLE_TYPES = setOf("TEXT", "POLL")
        private const val CHAT_CONTEXT_MESSAGES = 30
        const val MAX_MESSAGE_BYTES = 3_500
        const val MAX_MESSAGE_CHARS = 2_000
        const val MAX_GROUP_MEMBERS = 50
        const val MAX_GROUP_NAME = 50
        const val MAX_NICKNAME = 40
        private const val MAX_TYPING_CHANNELS = 40
    }
}
