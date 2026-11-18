package com.example.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** Bitmiş ses kaydı. */
class VoiceClip(val file: File, val durationMs: Long, val waveform: List<Int>)

/**
 * Sesli mesaj kaydedici: AAC (m4a), tek kanal, 32 kbps (dakikası ≈240 KB). Kayıt sırasında genlik
 * örnekleri toplanır ve dalga formu çıkarılır. Aynı anda tek kayıt yapılır.
 */
class VoiceRecorder(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val dir = File(appContext.cacheDir, "voice_rec").apply { mkdirs() }
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private var sampler: Job? = null
    private val samples = ArrayList<Int>()

    /** Kayıt sürüyorsa geçen süre (ms); kayıt yoksa -1. */
    private val _elapsed = MutableStateFlow(-1L)
    val elapsed: StateFlow<Long> = _elapsed

    /** Son genlik (0–1): kayıt düğmesinin nabız efekti için. */
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level

    val isRecording: Boolean get() = recorder != null

    /** Kaydı başlatır; mikrofon kullanılamıyorsa false döner. [onLimit] süre sınırına gelince çağrılır. */
    fun start(onLimit: () -> Unit): Boolean {
        if (recorder != null) return true
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "rec_${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(appContext) else MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(SAMPLE_RATE)
            r.setAudioEncodingBitRate(BIT_RATE)
            r.setMaxDuration(VoiceMeta.MAX_DURATION_MS.toInt())
            r.setOutputFile(target.absolutePath)
            r.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onLimit()
            }
            r.prepare()
            r.start()
            recorder = r
            file = target
            startedAt = SystemClock.elapsedRealtime()
            samples.clear()
            _elapsed.value = 0L
            sampler = scope.launch {
                while (isActive && recorder === r) {
                    val amp = runCatching { r.maxAmplitude }.getOrDefault(0)
                    samples += amp
                    _level.value = (amp / 20_000f).coerceIn(0f, 1f)
                    _elapsed.value = SystemClock.elapsedRealtime() - startedAt
                    delay(SAMPLE_INTERVAL_MS)
                }
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Kayıt başlatılamadı", e)
            runCatching { r.release() }
            target.delete()
            false
        }
    }

    /** Kaydı bitirir. Çok kısaysa veya kayıt bozuksa null döner. */
    fun stop(): VoiceClip? {
        val r = recorder ?: return null
        val f = file
        val duration = SystemClock.elapsedRealtime() - startedAt
        val ok = runCatching { r.stop() }.isSuccess
        cleanup(r)
        if (!ok || f == null || !f.isFile || f.length() == 0L || duration < VoiceMeta.MIN_DURATION_MS) {
            f?.delete()
            return null
        }
        return VoiceClip(f, duration.coerceAtMost(VoiceMeta.MAX_DURATION_MS), Waveform.fromAmplitudes(samples.toList()))
    }

    /** Kaydı iptal eder ve dosyayı siler. */
    fun cancel() {
        val r = recorder ?: return
        runCatching { r.stop() }
        cleanup(r)
        file?.delete()
    }

    private fun cleanup(r: MediaRecorder) {
        sampler?.cancel()
        sampler = null
        runCatching { r.reset() }
        runCatching { r.release() }
        recorder = null
        _elapsed.value = -1L
        _level.value = 0f
    }

    companion object {
        private const val TAG = "DakkVoice"
        private const val SAMPLE_RATE = 22_050
        private const val BIT_RATE = 32_000
        private const val SAMPLE_INTERVAL_MS = 80L
    }
}
