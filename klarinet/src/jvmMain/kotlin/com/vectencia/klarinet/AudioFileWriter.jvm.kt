package com.vectencia.klarinet

actual class AudioFileWriter actual constructor(
    filePath: String, format: AudioFileFormat, sampleRate: Int,
    channelCount: Int, tags: AudioFileTags?,
) {
    private var encoderPtr: Long = when (format) {
        AudioFileFormat.WAV -> {
            val ptr = JniBridge.nativeEncoderInitFile(filePath, 0, channelCount, sampleRate)
            if (ptr == 0L) throw AudioFileException("Failed to create WAV file: $filePath")
            ptr
        }
        AudioFileFormat.MP3 -> throw UnsupportedFormatException("MP3 encoding is not supported")
        AudioFileFormat.AAC -> throw UnsupportedFormatException("AAC encoding is not available on JVM")
        AudioFileFormat.M4A -> throw UnsupportedFormatException("M4A encoding is not available on JVM")
    }

    actual fun writeFrames(data: FloatArray, numFrames: Int) {
        check(encoderPtr != 0L) { "AudioFileWriter has been closed" }
        if (!JniBridge.nativeEncoderWriteFrames(encoderPtr, data, numFrames)) {
            throw AudioFileException("Failed to write audio frames")
        }
    }

    actual fun close() {
        if (encoderPtr != 0L) { JniBridge.nativeEncoderUninit(encoderPtr); encoderPtr = 0L }
    }
}
