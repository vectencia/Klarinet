package com.vectencia.klarinet

/**
 * watchOS implementation of [AudioFileReader].
 *
 * The Kotlin/Native watchOS bindings do not include the ExtAudioFile APIs
 * required for audio file decoding. File reading is not supported on watchOS.
 * Use [AudioStream] for real-time audio I/O instead.
 */
actual class AudioFileReader actual constructor(filePath: String) {

    actual val info: AudioFileInfo = throwUnsupported()
    actual val isAtEnd: Boolean get() = true

    actual fun readAll(): FloatArray = throwUnsupported()
    actual fun readFrames(maxFrames: Int): FloatArray = throwUnsupported()
    actual fun seekTo(framePosition: Long): Unit = throwUnsupported()
    actual fun close() {}
}

private fun throwUnsupported(): Nothing {
    throw UnsupportedFormatException(
        "AudioFileReader is not available on watchOS. " +
            "The ExtAudioFile APIs required for audio decoding are not " +
            "exposed in the Kotlin/Native watchOS bindings."
    )
}
