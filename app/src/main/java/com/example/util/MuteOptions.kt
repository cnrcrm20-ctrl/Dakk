package com.example.util

/** Sessize alma süreleri. */
object MuteOptions {
    const val EIGHT_HOURS = 8L * 60 * 60 * 1000
    const val ONE_WEEK = 7L * 24 * 60 * 60 * 1000
    const val FOREVER = Long.MAX_VALUE

    fun until(duration: Long, now: Long): Long = if (duration == FOREVER) FOREVER else now + duration

    fun isMuted(until: Long, now: Long): Boolean = until > now

    fun label(until: Long, now: Long): String? = when {
        !isMuted(until, now) -> null
        until == FOREVER -> "Her zaman sessiz"
        else -> {
            val remaining = until - now
            val hours = (remaining + 59 * 60 * 1000) / (60 * 60 * 1000)
            if (hours <= 24) "$hours saat sessiz" else "${(hours + 23) / 24} gün sessiz"
        }
    }
}
