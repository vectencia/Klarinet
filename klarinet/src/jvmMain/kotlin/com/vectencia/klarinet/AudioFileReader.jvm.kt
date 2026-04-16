package com.vectencia.klarinet

import java.io.File

actual class AudioFileReader actual constructor(filePath: String) {

    private var decoderPtr: Long
    private val _info: AudioFileInfo
    private val channelCount: Int
    private var _isAtEnd = false

    actual val info: AudioFileInfo get() = _info
    actual val isAtEnd: Boolean get() = _isAtEnd

    init {
        val extension = filePath.substringAfterLast('.', "").lowercase()
        val format = when (extension) {
            "wav" -> AudioFileFormat.WAV
            "mp3" -> AudioFileFormat.MP3
            "aac" -> throw UnsupportedFormatException("AAC decoding is not available on JVM")
            "m4a" -> throw UnsupportedFormatException("M4A decoding is not available on JVM")
            else -> throw AudioFileException("Unsupported file extension: $extension")
        }

        if (!filePath.startsWith("http://") && !filePath.startsWith("https://")) {
            if (!File(filePath).exists()) {
                throw AudioFileException("File not found: $filePath")
            }
        }

        decoderPtr = JniBridge.nativeDecoderInitFile(filePath)
        if (decoderPtr == 0L) throw AudioFileException("Failed to open audio file: $filePath")

        val sampleRate = JniBridge.nativeDecoderGetSampleRate(decoderPtr)
        channelCount = JniBridge.nativeDecoderGetChannels(decoderPtr)
        val totalFrames = JniBridge.nativeDecoderGetTotalFrames(decoderPtr)
        val durationMs = if (sampleRate > 0) totalFrames * 1000L / sampleRate else 0L

        _info = AudioFileInfo(
            format = format, sampleRate = sampleRate, channelCount = channelCount,
            durationMs = durationMs, bitRate = 0, tags = AudioFileTags(),
        )
    }

    actual fun readAll(): FloatArray {
        val chunks = mutableListOf<FloatArray>()
        while (!_isAtEnd) {
            val frames = readFrames(4096)
            if (frames.isEmpty()) break
            chunks.add(frames)
        }
        if (chunks.isEmpty()) return FloatArray(0)
        val total = chunks.sumOf { it.size }
        val result = FloatArray(total)
        var offset = 0
        for (chunk in chunks) { chunk.copyInto(result, offset); offset += chunk.size }
        return result
    }

    actual fun readFrames(maxFrames: Int): FloatArray {
        if (_isAtEnd || decoderPtr == 0L) return FloatArray(0)
        val buffer = FloatArray(maxFrames * channelCount)
        val framesRead = JniBridge.nativeDecoderReadFrames(decoderPtr, buffer, maxFrames)
        if (framesRead <= 0) { _isAtEnd = true; return FloatArray(0) }
        val actualSamples = (framesRead * channelCount).toInt()
        return if (actualSamples == buffer.size) buffer else buffer.copyOf(actualSamples)
    }

    actual fun seekTo(framePosition: Long) {
        check(decoderPtr != 0L) { "AudioFileReader has been closed" }
        JniBridge.nativeDecoderSeek(decoderPtr, framePosition)
        _isAtEnd = false
    }

    actual fun close() {
        if (decoderPtr != 0L) { JniBridge.nativeDecoderUninit(decoderPtr); decoderPtr = 0L }
    }
}
