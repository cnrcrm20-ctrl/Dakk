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

    fun muteUntil(context: Context, chatId: String): Long =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(PREFIX_MUTE + chatId, 0L)

    fun isMuted(context: Context, chatId: String, now: Long = System.currentTimeMillis()): Boolean =
        MuteOptions.isMuted(muteUntil(context, chatId), now)
}
