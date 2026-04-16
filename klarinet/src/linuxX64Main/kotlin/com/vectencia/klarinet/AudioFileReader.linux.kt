@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.vectencia.klarinet

import cnames.structs.*
import klarinet_native.*
import kotlinx.cinterop.*
import platform.posix.access
import platform.posix.F_OK

actual class AudioFileReader actual constructor(filePath: String) {

    private var decoderPtr: CPointer<KlarinetDecoder>?
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
            "aac" -> throw UnsupportedFormatException("AAC decoding is not available on Linux")
            "m4a" -> throw UnsupportedFormatException("M4A decoding is not available on Linux")
            else -> throw AudioFileException("Unsupported file extension: $extension")
        }

        if (access(filePath, F_OK) != 0) {
            throw AudioFileException("File not found: $filePath")
        }

        decoderPtr = klarinet_decoder_init_file(filePath)
            ?: throw AudioFileException("Failed to open audio file: $filePath")

        val dec = decoderPtr!!
        val sampleRate = klarinet_decoder_get_sample_rate(dec)
        channelCount = klarinet_decoder_get_channels(dec)
        val totalFrames = klarinet_decoder_get_total_frames(dec)
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
        if (_isAtEnd) return FloatArray(0)
        val dec = decoderPtr ?: return FloatArray(0)
        val totalSamples = maxFrames * channelCount
        val buffer = FloatArray(totalSamples)
        val framesRead = memScoped {
            val pinned = buffer.pin()
            val result = klarinet_decoder_read_frames(dec, pinned.addressOf(0), maxFrames)
            pinned.unpin()
            result
        }
        if (framesRead <= 0) { _isAtEnd = true; return FloatArray(0) }
        val actualSamples = (framesRead * channelCount).toInt()
        return if (actualSamples == totalSamples) buffer else buffer.copyOf(actualSamples)
    }

    actual fun seekTo(framePosition: Long) {
        val dec = decoderPtr ?: throw AudioFileException("AudioFileReader has been closed")
        klarinet_decoder_seek(dec, framePosition)
        _isAtEnd = false
    }

    actual fun close() {
        decoderPtr?.let { klarinet_decoder_uninit(it) }
        decoderPtr = null
    }
}
