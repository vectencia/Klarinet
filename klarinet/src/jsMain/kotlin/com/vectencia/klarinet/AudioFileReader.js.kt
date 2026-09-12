package com.vectencia.klarinet

actual class AudioFileReader actual constructor(filePath: String) {

    private val stored: StoredAudio = WebAudioStore.get(filePath)
        ?: throw AudioFileException(
            "Audio file is not available: $filePath. " +
                "On JS, decode a URL with decodeAudioFile() or write PCM with AudioFileWriter first.",
        )
    private val channelCount: Int = stored.info.channelCount.coerceAtLeast(1)
    private var framePosition = 0
    private var closed = false
    private var _isAtEnd = stored.samples.isEmpty()

    actual val info: AudioFileInfo = stored.info
    actual val isAtEnd: Boolean get() = _isAtEnd

    actual fun readAll(): FloatArray {
        checkOpen()
        if (_isAtEnd) return FloatArray(0)
        val remaining = stored.samples.size - framePosition * channelCount
        if (remaining <= 0) {
            _isAtEnd = true
            return FloatArray(0)
        }
        val result = stored.samples.copyOfRange(framePosition * channelCount, stored.samples.size)
        framePosition = stored.samples.size / channelCount
        _isAtEnd = true
        return result
    }

    actual fun readFrames(maxFrames: Int): FloatArray {
        checkOpen()
        if (_isAtEnd || maxFrames <= 0) return FloatArray(0)
        val totalFrames = stored.samples.size / channelCount
        val remaining = totalFrames - framePosition
        if (remaining <= 0) {
            _isAtEnd = true
            return FloatArray(0)
        }
        val frames = if (maxFrames < remaining) maxFrames else remaining
        val start = framePosition * channelCount
        val end = start + frames * channelCount
        framePosition += frames
        if (framePosition >= totalFrames) _isAtEnd = true
        return stored.samples.copyOfRange(start, end)
    }

    actual fun seekTo(framePosition: Long) {
        checkOpen()
        if (framePosition < 0) throw AudioFileException("Frame position must be non-negative")
        val totalFrames = stored.samples.size / channelCount
        this.framePosition = framePosition.toInt().coerceAtLeast(0)
        _isAtEnd = this.framePosition >= totalFrames
    }

    actual fun close() {
        closed = true
    }

    private fun checkOpen() {
        if (closed) throw AudioFileException("AudioFileReader has been closed")
    }
}
