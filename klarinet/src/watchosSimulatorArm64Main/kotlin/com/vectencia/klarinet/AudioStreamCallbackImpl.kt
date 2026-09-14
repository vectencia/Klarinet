package com.vectencia.klarinet

/**
 * Concrete implementation of [AudioStreamCallback] that delegates to a lambda.
 *
 * This class exists to provide a Swift-friendly way to create callback instances,
 * since Swift cannot directly create anonymous implementations of Kotlin interfaces.
 *
 * Usage from Swift:
 * ```swift
 * let callback = AudioStreamCallbackImpl { buffer, numFrames in
 *     // Fill buffer with audio data
 *     return numFrames
 * }
 * ```
 */
class AudioStreamCallbackImpl(
    private val onAudioReadyBlock: (buffer: FloatArray, numFrames: Int) -> Int,
) : AudioStreamCallback {
    var onUnderrun: ((AudioStream, Int) -> Unit)? = null

    override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
        return onAudioReadyBlock(buffer, numFrames)
    }

    override fun onStreamUnderrun(stream: AudioStream, count: Int) {
        onUnderrun?.invoke(stream, count)
    }
}
