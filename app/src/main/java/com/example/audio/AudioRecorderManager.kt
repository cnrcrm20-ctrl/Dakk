package com.example.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioRecorderManager(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var currentRecordingFile: File? = null
    private var playingFilePath: String? = null

    companion object {
        private const val TAG = "AudioRecorderManager"
    }

    fun startRecording(): File? {
        stopRecording()
        try {
            val file = File(context.cacheDir, "dakk_voice_${System.currentTimeMillis()}.m4a")
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            currentRecordingFile = file
            Log.d(TAG, "Recording started: ${file.absolutePath}")
            return file
        } catch (e: Exception) {
            Log.e(TAG, "Start recording failed: ${e.message}")
            stopRecording()
            return null
        }
    }

    fun stopRecording(): File? {
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            val file = currentRecordingFile
            currentRecordingFile = null
            Log.d(TAG, "Recording stopped: ${file?.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Stop recording failed: ${e.message}")
            recorder?.release()
            recorder = null
            null
        }
    }

    fun playAudio(filePath: String, onComplete: () -> Unit) {
        stopPlaying()
        try {
            playingFilePath = filePath
            player = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                start()
                setOnCompletionListener {
                    stopPlaying()
                    onComplete()
                }
            }
            Log.d(TAG, "Audio playback started: $filePath")
        } catch (e: Exception) {
            Log.e(TAG, "Play audio failed: ${e.message}")
            stopPlaying()
            onComplete()
        }
    }

    fun stopPlaying() {
        try {
            player?.apply {
                if (isPlaying) stop()
                release()
            }
            player = null
            playingFilePath = null
        } catch (e: Exception) {
            Log.e(TAG, "Stop playing failed: ${e.message}")
        }
    }

    fun isPlaying(filePath: String): Boolean {
        return player?.isPlaying == true && playingFilePath == filePath
    }
}
