package com.eventos.banana.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorderHelper(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var audioFile: File? = null

    /** Extensión del último archivo grabado (.ogg en API 29+, .m4a en menor) */
    val fileExtension: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "ogg" else "m4a"

    fun startRecording() {
        try {
            val ext = fileExtension
            audioFile = File(context.cacheDir, "temp_recording.$ext")

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // 🚀 OGG/Opus: ~70% más pequeños que AAC para voz
                    setOutputFormat(MediaRecorder.OutputFormat.OGG)
                    setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                    setAudioSamplingRate(16000)
                    setAudioEncodingBitRate(16000) // 16kbps Opus = excelente para voz
                } else {
                    // Fallback AAC para API 26-28
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioSamplingRate(12000)
                    setAudioEncodingBitRate(24000)
                }

                setAudioChannels(1) // Mono
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioRecorder", "Failed to start recording", e)
            recorder?.release()
            recorder = null
        }
    }

    fun stopRecording(): File? {
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            audioFile
        } catch (e: Exception) {
            recorder = null
            null
        }
    }

    fun getAmplitude(): Float {
        return try {
            recorder?.maxAmplitude?.toFloat() ?: 0f
        } catch (e: Exception) {
            0f
        }
    }

    fun release() {
        recorder?.release()
        recorder = null
    }

    fun cancelRecording() {
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {}
        recorder = null
        audioFile?.delete()
    }
}
