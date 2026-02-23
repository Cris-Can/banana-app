package com.eventos.banana.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.MessageRepository
import com.eventos.banana.util.AudioPlayerHelper
import com.eventos.banana.util.AudioRecorderHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch


@HiltViewModel(assistedFactory = ChatViewModel.Factory::class)
class ChatViewModel @AssistedInject constructor(
    val repository: MessageRepository,
    @Assisted("chat_conversationId") private val conversationId: String,
    @Assisted("chat_currentUserId") private val currentUserId: String,
    private val audioRecorderHelper: AudioRecorderHelper,
    private val audioPlayerHelper: AudioPlayerHelper
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("chat_conversationId") conversationId: String,
            @Assisted("chat_currentUserId") currentUserId: String
        ): ChatViewModel
    }


    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _playingMessageId = MutableStateFlow<String?>(null)
    val playingMessageId = _playingMessageId.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude = _amplitude.asStateFlow()

    // 🆕 Lista de amplitudes para waveform en tiempo real
    private val _amplitudes = MutableStateFlow<List<Float>>(emptyList())
    val amplitudes = _amplitudes.asStateFlow()

    // 🆕 Estado de envío de audio
    private val _isSendingAudio = MutableStateFlow(false)
    val isSendingAudio = _isSendingAudio.asStateFlow()

    // 🆕 Preview de audio antes de enviar
    private val _pendingAudioBytes = MutableStateFlow<ByteArray?>(null)
    val pendingAudioBytes = _pendingAudioBytes.asStateFlow()

    private val _pendingAudioDuration = MutableStateFlow(0)
    val pendingAudioDuration = _pendingAudioDuration.asStateFlow()

    private var amplitudeJob: kotlinx.coroutines.Job? = null

    fun startRecording() {
        _isRecording.value = true
        _amplitudes.value = emptyList()
        audioRecorderHelper.startRecording()
        startAmplitudePolling()
    }

    private fun startAmplitudePolling() {
        amplitudeJob?.cancel()
        amplitudeJob = viewModelScope.launch {
            while (_isRecording.value) {
                val rawAmplitude = audioRecorderHelper.getAmplitude()
                val normalized = (rawAmplitude / 32767f).coerceIn(0f, 1f)
                _amplitude.value = normalized
                // Acumular amplitudes para waveform (máx 60 barras)
                val current = _amplitudes.value.toMutableList()
                current.add(normalized.coerceAtLeast(0.05f))
                if (current.size > 60) current.removeAt(0)
                _amplitudes.value = current
                kotlinx.coroutines.delay(100)
            }
        }
    }

    // 🆕 Detener grabación y guardar en preview (NO enviar aún)
    fun stopRecordingForPreview(durationMs: Int) {
        amplitudeJob?.cancel()
        val audioFile = audioRecorderHelper.stopRecording()
        if (audioFile != null && audioFile.exists()) {
            _pendingAudioBytes.value = audioFile.readBytes()
            _pendingAudioDuration.value = durationMs
        }
        _isRecording.value = false
        _amplitude.value = 0f
    }

    // 🆕 Confirmar envío del audio en preview
    fun confirmSendAudio(replyToId: String? = null) {
        val bytes = _pendingAudioBytes.value ?: return
        val duration = _pendingAudioDuration.value
        _isSendingAudio.value = true
        _pendingAudioBytes.value = null
        viewModelScope.launch {
            repository.uploadAudio(conversationId, currentUserId, bytes).onSuccess { url ->
                repository.sendMessage(
                    conversationId = conversationId,
                    senderId = currentUserId,
                    content = "",
                    replyToId = replyToId,
                    audioUrl = url,
                    audioDurationMs = duration
                )
            }
            _isSendingAudio.value = false
            _amplitudes.value = emptyList()
        }
    }

    // 🆕 Descartar audio del preview
    fun discardPendingAudio() {
        _pendingAudioBytes.value = null
        _pendingAudioDuration.value = 0
        _amplitudes.value = emptyList()
    }

    fun stopRecording(onComplete: (ByteArray) -> Unit) {
        amplitudeJob?.cancel()
        val audioFile = audioRecorderHelper.stopRecording()
        if (audioFile != null && audioFile.exists()) {
            onComplete(audioFile.readBytes())
        }
        _isRecording.value = false
        _amplitude.value = 0f
    }

    fun cancelRecording() {
        viewModelScope.launch {
            audioRecorderHelper.cancelRecording()
            _isRecording.value = false
            _amplitudes.value = emptyList()
        }
    }

    fun playAudio(messageId: String, url: String) {
        if (_playingMessageId.value == messageId) {
            audioPlayerHelper.stopAudio()
            _playingMessageId.value = null
        } else {
            audioPlayerHelper.stopAudio()
            _playingMessageId.value = messageId
            audioPlayerHelper.playAudio(url) {
                _playingMessageId.value = null
            }
        }
    }

    fun sendMessage(content: String, replyToId: String? = null) {
        viewModelScope.launch {
            repository.sendMessage(
                conversationId = conversationId,
                senderId = currentUserId,
                content = content,
                replyToId = replyToId
            )
        }
    }

    fun sendAudio(audioBytes: ByteArray, durationMs: Int, replyToId: String? = null) {
        _isSendingAudio.value = true
        viewModelScope.launch {
            repository.uploadAudio(conversationId, currentUserId, audioBytes).onSuccess { url ->
                repository.sendMessage(
                    conversationId = conversationId,
                    senderId = currentUserId,
                    content = "",
                    replyToId = replyToId,
                    audioUrl = url,
                    audioDurationMs = durationMs
                )
            }
            _isSendingAudio.value = false
        }
    }

    fun toggleReaction(messageId: String, emoji: String) {
        viewModelScope.launch {
            repository.toggleReaction(conversationId, messageId, currentUserId, emoji)
        }
    }

    fun setTypingStatus(isTyping: Boolean) {
        viewModelScope.launch {
            repository.setTypingStatus(conversationId, currentUserId, isTyping)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayerHelper.stopAudio()
    }
}
