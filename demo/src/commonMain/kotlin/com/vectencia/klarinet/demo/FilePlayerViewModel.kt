package com.vectencia.klarinet.demo

import androidx.lifecycle.ViewModel
import com.vectencia.klarinet.AudioEngine
import com.vectencia.klarinet.AudioFileInfo
import com.vectencia.klarinet.AudioFileReader
import com.vectencia.klarinet.AudioStream
import com.vectencia.klarinet.playFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class FilePlayerUiState(
    val filePath: String = "",
    val isPlaying: Boolean = false,
    val errorMessage: String? = null,
    val fileInfo: AudioFileInfo? = null,
)

sealed interface FilePlayerEvent {
    data class FilePathChanged(val path: String) : FilePlayerEvent
    data object LoadFile : FilePlayerEvent
    data object TogglePlayback : FilePlayerEvent
}

class FilePlayerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(FilePlayerUiState())
    val uiState: StateFlow<FilePlayerUiState> = _uiState.asStateFlow()

    private var engine: AudioEngine? = null
    private var stream: AudioStream? = null

    fun onEvent(event: FilePlayerEvent) {
        when (event) {
            is FilePlayerEvent.FilePathChanged -> {
                _uiState.update { it.copy(filePath = event.path) }
            }
            is FilePlayerEvent.LoadFile -> loadFile()
            is FilePlayerEvent.TogglePlayback -> {
                if (_uiState.value.isPlaying) stop() else play()
            }
        }
    }

    private fun loadFile() {
        _uiState.update { it.copy(errorMessage = null, fileInfo = null) }
        try {
            val reader = AudioFileReader(_uiState.value.filePath)
            _uiState.update { it.copy(fileInfo = reader.info) }
            reader.close()
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = e.message ?: "Failed to load file") }
        }
    }

    private fun play() {
        try {
            val newEngine = AudioEngine.create()
            engine = newEngine
            val newStream = newEngine.playFile(_uiState.value.filePath)
            stream = newStream
            newStream.start()
            _uiState.update { it.copy(isPlaying = true) }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    errorMessage = e.message ?: "Playback failed",
                    isPlaying = false,
                )
            }
        }
    }

    private fun stop() {
        try {
            stream?.stop()
            stream?.close()
        } catch (_: Exception) {}
        try {
            engine?.release()
        } catch (_: Exception) {}
        stream = null
        engine = null
        _uiState.update { it.copy(isPlaying = false) }
    }

    override fun onCleared() {
        stop()
    }
}
