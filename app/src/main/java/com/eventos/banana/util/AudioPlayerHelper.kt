package com.eventos.banana.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

class AudioPlayerHelper(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private val cacheDir = File(context.cacheDir, "audio_cache").apply { mkdirs() }

    fun playAudio(url: String, onComplete: () -> Unit = {}) {
        stopAudio()
        try {
            // Verificar cache local primero
            val cachedFile = getCachedFile(url)
            val dataSource = if (cachedFile.exists()) {
                cachedFile.absolutePath // 🚀 Reproducción instantánea desde cache
            } else {
                url // Primera vez: streaming desde Firebase
            }

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(dataSource)
                setOnPreparedListener { mp ->
                    mp.start()
                    // Si no estaba en cache, descargarlo en background
                    if (!cachedFile.exists()) {
                        cacheAudioAsync(url)
                    }
                }
                setOnCompletionListener {
                    stopAudio()
                    onComplete()
                }
                setOnErrorListener { _, what, extra ->
                    android.util.Log.e("AudioPlayer", "Error: what=$what extra=$extra")
                    // Si cache corrupto, borrar y reintentar desde URL
                    if (cachedFile.exists()) {
                        cachedFile.delete()
                    }
                    stopAudio()
                    onComplete()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayer", "Error setting data source", e)
            stopAudio()
            onComplete()
        }
    }

    fun stopAudio() {
        mediaPlayer?.apply {
            try {
                if (isPlaying) stop()
            } catch (e: Exception) {}
            release()
        }
        mediaPlayer = null
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false

    /** Genera un nombre de archivo de cache basado en la URL */
    private fun getCachedFile(url: String): File {
        val hash = url.hashCode().toUInt().toString(16)
        return File(cacheDir, "audio_$hash")
    }

    /** Descarga el audio al cache en background (no bloquea reproducción) */
    private fun cacheAudioAsync(url: String) {
        Thread {
            try {
                val cachedFile = getCachedFile(url)
                if (cachedFile.exists()) return@Thread
                val bytes = URL(url).readBytes()
                cachedFile.writeBytes(bytes)
            } catch (e: Exception) {
                // Cache falla silenciosamente, no afecta reproducción
            }
        }.start()
    }

    /** Limpiar cache de audios viejos (> 7 días) */
    fun cleanOldCache() {
        val sevenDaysMs = 7 * 24 * 60 * 60 * 1000L
        val now = System.currentTimeMillis()
        cacheDir.listFiles()?.forEach { file ->
            if (now - file.lastModified() > sevenDaysMs) {
                file.delete()
            }
        }
    }
}
