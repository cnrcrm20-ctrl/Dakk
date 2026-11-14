package com.example.util

/** Sohbet içinde ortada gösterilen kısa sistem notları. */
object SystemNotes {

    const val MESSAGE_TYPE = "SYSTEM"
    const val MAX_LENGTH = 300

    fun nickname(actor: String, actorIsTarget: Boolean, target: String, nickname: String?): String {
        val nick = nickname?.trim().orEmpty()
        return when {
            actorIsTarget && nick.isEmpty() -> "$actor takma adını kaldırdı"
            actorIsTarget -> "$actor, takma adını $nick yaptı"
            nick.isEmpty() -> "$actor, $target için takma adı kaldırdı"
            else -> "$actor, $target için takma adı $nick yaptı"
        }.take(MAX_LENGTH)
    }

    fun disappearing(actor: String, seconds: Long): String =
        if (seconds <= 0) "$actor kaybolan mesajları kapattı"
        else "$actor kaybolan mesajları açtı: yeni mesajlar ${Disappearing.label(seconds)} sonra kaybolur"

    fun groupCreated(actor: String, name: String): String = "$actor \"$name\" grubunu oluşturdu".take(MAX_LENGTH)
    fun renamed(actor: String, name: String): String = "$actor grubun adını \"$name\" yaptı".take(MAX_LENGTH)
    fun photoChanged(actor: String): String = "$actor grup fotoğrafını değiştirdi"
    fun added(actor: String, names: List<String>): String = "$actor gruba ekledi: ${names.joinToString(", ")}".take(MAX_LENGTH)
    fun removed(actor: String, name: String): String = "$actor, $name kişisini gruptan çıkardı".take(MAX_LENGTH)
    fun left(actor: String): String = "$actor gruptan ayrıldı"
    fun joined(actor: String): String = "$actor davet bağlantısıyla katıldı"
    fun madeAdmin(actor: String, name: String): String = "$actor, $name kişisini yönetici yaptı".take(MAX_LENGTH)
    fun removedAdmin(actor: String, name: String): String = "$actor, $name kişisinin yöneticiliğini kaldırdı".take(MAX_LENGTH)
    fun inviteChanged(actor: String, enabled: Boolean): String =
        if (enabled) "$actor davet bağlantısını açtı" else "$actor davet bağlantısını kapattı"
}
