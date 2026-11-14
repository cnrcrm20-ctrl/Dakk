package com.example.data

/** Firestore: users/{uid} */
data class UserProfile(
    val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String,
    val publicKey: String
)

/** Firestore: users/{me}/contacts/{uid} */
data class Contact(
    val uid: String,
    val alias: String,
    val email: String,
    val photoUrl: String,
    val isBlocked: Boolean
)

/** Firestore: chats/{chatId} */
data class ChatDoc(
    val chatId: String,
    val memberUids: List<String>,
    val lastMessage: String,
    val lastMessageSenderId: String,
    val lastMessageSenderKey: String,
    val lastMessageRecipientKey: String,
    val lastMessageTime: Long,
    /** Grup sohbeti mi? (Grup kimlikleri "g_" ile başlar.) */
    val isGroup: Boolean = false,
    /** Grup adı (yalnızca gruplarda). */
    val name: String = "",
    /** Küçük grup fotoğrafı, Base64 JPEG (yalnızca gruplarda). */
    val photo: String = "",
    /** Grup yöneticileri. */
    val admins: List<String> = emptyList(),
    /** Grup davet bağlantısı açık mı? */
    val inviteEnabled: Boolean = false,
    /** Grup davet kodu. */
    val inviteCode: String = "",
    /** "Yazıyor…" sinyali için gruba özel gizli değer. */
    val typingKey: String = "",
    /** Sohbetteki herkesin gördüğü takma adlar: kullanıcı kimliği → takma ad. */
    val nicknames: Map<String, String> = emptyMap(),
    /** Kaybolan mesaj süresi (saniye); 0 = kapalı. */
    val disappearSeconds: Long = 0L,
    /** Grup son mesaj önizlemesinin her üye için sarılmış anahtarı. */
    val lastMessageKeys: Map<String, String> = emptyMap(),
    /** Son mesaj kaybolan bir mesajsa silineceği zaman (0 = süresiz). */
    val lastMessageExpiresAt: Long = 0L
)

/** Firestore: chats/{chatId}/messages/{id} */
data class MessageDoc(
    val id: String,
    val senderId: String,
    val content: String,
    val senderKey: String,
    val recipientKey: String,
    val deliveryStatus: String,
    val createdAt: Long,
    val isPending: Boolean,
    val messageType: String = "TEXT",
    val isAssistant: Boolean = false,
    /** Kullanıcı kimliği → şifreli tepki emojisi. */
    val reactions: Map<String, String> = emptyMap(),
    /** Yanıtlanan mesajın kimliği (yoksa null). */
    val replyTo: String? = null,
    /** Yanıtlanan mesajı gönderen kişinin kimliği. */
    val replyToSender: String? = null,
    /** Yanıtlanan mesajdan kısa, şifreli alıntı. */
    val replyPreview: String? = null,
    /** Grup mesajlarında, mesaj anahtarının her üye için sarılmış hali: kullanıcı kimliği → şifreli anahtar. */
    val keys: Map<String, String> = emptyMap(),
    /** Kaybolan mesajın silineceği zaman (0 = süresiz). */
    val expiresAt: Long = 0L,
    /** Sistem notu metni (ör. "Ali, takma adını ronaldoo7 yaptı"). */
    val systemText: String? = null,
    /** Anket oyları: kullanıcı kimliği → şifreli seçim. */
    val votes: Map<String, String> = emptyMap(),
    /** Gönderenin o anki adı. */
    val senderName: String = ""
)

enum class MessageStatus { PENDING, SENT, READ }

/** Fotoğrafın bu cihazdaki durumu. */
enum class ImageState { READY, UPLOADING, DOWNLOADING, EXPIRED, FAILED }

/** Mesajdaki fotoğraf. localPath doluysa fotoğraf cihazda hazırdır. */
data class ImageAttachment(
    val mediaId: String,
    val chunks: Int,
    val width: Int,
    val height: Int,
    val size: Long,
    val thumbBase64: String?,
    val localPath: String?,
    val state: ImageState
)

/**
 * Arayüzde gösterilen mesaj. text == null ve image == null ise mesaj bu cihazda çözülemedi.
 * isAssistant: mesaj Dakk Asistan'ın sohbete eklediği bir yanıttır.
 */
data class UiMessage(
    val id: String,
    val text: String?,
    val isFromMe: Boolean,
    val timestamp: Long,
    val status: MessageStatus,
    val image: ImageAttachment? = null,
    val isAssistant: Boolean = false,
    /** Tepkiler: kullanıcı kimliği → emoji (çözülmüş). */
    val reactions: Map<String, String> = emptyMap(),
    /** Bu mesaj bir yanıtsa alıntılanan mesajın özeti. */
    val reply: ReplyQuote? = null,
    /** "Ekran görüntüsü aldı" gibi sohbet içi sistem satırı. */
    val isScreenshotNotice: Boolean = false,
    /** Mesajı gönderen kişinin kimliği. */
    val senderUid: String = "",
    /** Sistem notu (takma ad, kaybolan mesaj, grup değişiklikleri). Doluysa ortada küçük satır olarak gösterilir. */
    val systemText: String? = null,
    /** Anket mesajı. */
    val poll: PollView? = null,
    /** Kaybolan mesajsa silineceği zaman (0 = süresiz). */
    val expiresAt: Long = 0L
)

/** Ekranda gösterilen anket. votes: seçenek sırası → oy veren kişi kimlikleri. */
data class PollView(
    val question: String,
    val options: List<String>,
    val multi: Boolean,
    val isMatch: Boolean,
    val votes: Map<Int, List<String>>,
    val myChoices: Set<Int>
) {
    val voterCount: Int get() = votes.values.flatten().toSet().size
}

/** Yanıtlanan mesajın baloncukta gösterilen alıntısı. */
data class ReplyQuote(
    val messageId: String,
    val fromMe: Boolean,
    val text: String,
    /** Alıntılanan mesajı gönderen kişi (gruplarda adı göstermek için). */
    val senderUid: String = ""
)

/**
 * Sohbet listesi satırı. Birebir sohbetlerde peerUid karşı tarafın kimliği, gruplarda grubun kimliğidir
 * (ikisi de sohbeti açmak için kullanılan anahtardır).
 */
data class ChatSummary(
    val chatId: String,
    val peerUid: String,
    val peerName: String,
    val peerPhotoUrl: String,
    val preview: String,
    val lastMessageTime: Long,
    val lastFromMe: Boolean,
    val isUnread: Boolean,
    val isGroup: Boolean = false,
    val isMuted: Boolean = false,
    /** Fotoğraf modeli: adres, yerel dosya veya bayt dizisi (grup fotoğrafı). */
    val photoModel: Any? = null
)

object ChatIds {
    fun of(a: String, b: String): String = listOf(a, b).sorted().joinToString("_")

    /** Grup sohbetlerinin kimliği "g_" ile başlar; Firebase kullanıcı kimliklerinde alt çizgi bulunmaz. */
    const val GROUP_PREFIX = "g_"

    fun isGroup(key: String): Boolean = key.startsWith(GROUP_PREFIX)

    private val GROUP_ID = Regex("^g_[A-Za-z0-9]{16,40}$")

    fun isValidGroupId(id: String): Boolean = GROUP_ID.matches(id)

    fun newGroupId(random: java.util.Random = java.security.SecureRandom()): String =
        GROUP_PREFIX + randomToken(20, random)

    fun randomToken(length: Int, random: java.util.Random = java.security.SecureRandom()): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"
        return buildString { repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) } }
    }
}

/** Bu cihazda saklanan fotoğraf; sürüm değişince yeniden yüklenir. */
data class LocalPhoto(val file: java.io.File, val version: Long)
