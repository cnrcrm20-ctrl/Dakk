package com.example.util

/** Kaybolan mesaj seçenekleri. */
object Disappearing {
    const val OFF = 0L
    const val DAY = 24L * 60 * 60
    const val WEEK = 7L * 24 * 60 * 60

    val OPTIONS = listOf(OFF, DAY, WEEK)

    fun isValid(seconds: Long): Boolean = seconds in OPTIONS

    fun label(seconds: Long): String = when (seconds) {
        DAY -> "24 saat"
        WEEK -> "7 gün"
        else -> "Kapalı"
    }

    /** Mesajın silineceği zaman; kapalıysa 0. */
    fun expiryFor(seconds: Long, now: Long): Long = if (isValid(seconds) && seconds > 0) now + seconds * 1000 else 0L

    fun isExpired(expiresAt: Long, now: Long): Boolean = expiresAt in 1..now
}
