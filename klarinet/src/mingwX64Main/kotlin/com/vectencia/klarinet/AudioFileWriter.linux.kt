@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.vectencia.klarinet

import cnames.structs.*
import klarinet_native.*
import kotlinx.cinterop.*

actual class AudioFileWriter actual constructor(
    filePath: String, format: AudioFileFormat, sampleRate: Int,
    channelCount: Int, tags: AudioFileTags?,
) {
    private var encoderPtr: CPointer<KlarinetEncoder>? = when (format) {
        AudioFileFormat.WAV -> klarinet_encoder_init_file(filePath, 0, channelCount, sampleRate)
            ?: throw AudioFileException("Failed to create WAV file: $filePath")
        AudioFileFormat.MP3 -> throw UnsupportedFormatException("MP3 encoding is not supported")
        AudioFileFormat.AAC -> throw UnsupportedFormatException("AAC encoding is not available on Windows")
        AudioFileFormat.M4A -> throw UnsupportedFormatException("M4A encoding is not available on Windows")
    }

    actual fun writeFrames(data: FloatArray, numFrames: Int) {
        val enc = encoderPtr ?: throw AudioFileException("AudioFileWriter has been closed")
        memScoped {
            val pinned = data.pin()
            val result = klarinet_encoder_write_frames(enc, pinned.addressOf(0), numFrames)
            pinned.unpin()
            if (result != 0) throw AudioFileException("Failed to write audio frames")
        }
    }

    actual fun close() {
        encoderPtr?.let { klarinet_encoder_uninit(it) }
        encoderPtr = null
    }
}
