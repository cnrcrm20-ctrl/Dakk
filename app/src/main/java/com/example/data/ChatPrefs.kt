package com.example.data

import android.content.Context
import com.example.util.MuteOptions

/**
 * Sohbete özel, yalnızca bu cihazda tutulan ayarlar (sessize alma). Uygulama ve bildirim servisi
 * aynı kaydı kullanır.
 */
object ChatPrefs {
    const val PREFS = "dakk_prefs"
    const val PREFIX_MUTE = "mute_"
    /** Gizli sohbetler (2.6.1+): bildirimlerde ad ve içerik gösterilmez. */
    const val PREF_HIDDEN = "hidden_chats"

    fun muteUntil(context: Context, chatId: String): Long =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(PREFIX_MUTE + chatId, 0L)

    fun isMuted(context: Context, chatId: String, now: Long = System.currentTimeMillis()): Boolean =
        MuteOptions.isMuted(muteUntil(context, chatId), now)

    fun isHidden(context: Context, chatId: String?): Boolean =
        !chatId.isNullOrBlank() && chatId in
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(PREF_HIDDEN, emptySet()).orEmpty()
}
