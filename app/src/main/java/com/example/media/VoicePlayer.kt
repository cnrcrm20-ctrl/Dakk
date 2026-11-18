package com.example.media

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Çalınan sesli mesajın durumu. id null ise hiçbir şey çalınmıyor. */
data class VoicePlayback(
    val id: String? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f
) {
    fun progressOf(mediaId: String): Float =
        if (id == mediaId && durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** Tek bir sesli mesaj çalar; yenisi başlayınca öncekisi durur. Hız: 1× / 1,5× / 2×. */
class VoicePlayer(private val scope: CoroutineScope) {

    private var player: MediaPlayer? = null
    private var ticker: Job? = null
    private val _state = MutableStateFlow(VoicePlayback())
    val state: StateFlow<VoicePlayback> = _state

    /** Aynı mesaja dokunulursa duraklatır/sürdürür; başka mesajsa onu baştan çalar. */
    fun toggle(mediaId: String, file: File, startFraction: Float = 0f) {
        val current = _state.value
        val p = player
        if (current.id == mediaId && p != null) {
            if (current.playing) pause() else resume()
            return
        }
        stop()
        scope.launch {
            val mp = withContext(Dispatchers.IO) {
                try {
                    MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        setDataSource(file.absolutePath)
                        prepare()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Ses çalınamadı", e)
                    null
                }
            } ?: return@launch
            player = mp
            mp.setOnCompletionListener {
                _state.value = _state.value.copy(playing = false, positionMs = 0L)
                runCatching { it.seekTo(0) }
                ticker?.cancel()
            }
            val duration = mp.duration.toLong().coerceAtLeast(0)
            if (startFraction > 0f && duration > 0) mp.seekTo((duration * startFraction.coerceIn(0f, 0.99f)).toInt())
            _state.value = VoicePlayback(mediaId, false, mp.currentPosition.toLong(), duration, current.speed)
            resume()
        }
    }

    private fun resume() {
        val p = player ?: return
        try {
            applySpeed(p, _state.value.speed) // Android'de hız ayarı oynatmayı da başlatır
            if (!p.isPlaying) p.start()
        } catch (e: Exception) {
            Log.w(TAG, "Ses sürdürülemedi", e)
            return
        }
        _state.value = _state.value.copy(playing = true)
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val mp = player ?: break
                val pos = runCatching { mp.currentPosition.toLong() }.getOrDefault(0L)
                if (_state.value.playing) _state.value = _state.value.copy(positionMs = pos)
                delay(TICK_MS)
            }
        }
    }

    fun pause() {
        val p = player ?: return
        runCatching { if (p.isPlaying) p.pause() }
        ticker?.cancel()
        _state.value = _state.value.copy(playing = false, positionMs = runCatching { p.currentPosition.toLong() }.getOrDefault(0L))
    }

    /** Çalan mesajda konuma atlar (0–1). */
    fun seek(mediaId: String, fraction: Float) {
        val p = player ?: return
        val s = _state.value
        if (s.id != mediaId || s.durationMs <= 0) return
        val pos = (s.durationMs * fraction.coerceIn(0f, 1f)).toLong()
        runCatching { p.seekTo(pos.toInt()) }
        _state.value = s.copy(positionMs = pos)
    }

    /** 1× → 1,5× → 2× → 1× */
    fun cycleSpeed() {
        val next = when (_state.value.speed) {
            1f -> 1.5f
            1.5f -> 2f
            else -> 1f
        }
        _state.value = _state.value.copy(speed = next)
        val p = player ?: return
        if (_state.value.playing) runCatching { applySpeed(p, next) }
    }

    private fun applySpeed(p: MediaPlayer, speed: Float) {
        p.playbackParams = p.playbackParams.setSpeed(speed)
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
        player?.let { p ->
            runCatching { p.stop() }
            runCatching { p.release() }
        }
        player = null
        _state.value = VoicePlayback(speed = _state.value.speed)
    }

    companion object {
        private const val TAG = "DakkVoice"
        private const val TICK_MS = 60L
    }
}
