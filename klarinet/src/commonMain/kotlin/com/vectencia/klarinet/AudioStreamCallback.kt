package com.vectencia.klarinet

/**
 * Callback interface for audio stream events.
 *
 * Implement this interface to receive callbacks from an [AudioStream].
 * Pass your implementation to [AudioEngine.openStream] to enable callback-driven
 * (push/pull) audio I/O.
 *
 * ## Threading
 *
 * [onAudioReady] is called on a **high-priority, real-time audio thread**.
 * Implementations must be real-time safe:
 *
 * - **Do not** allocate memory (no `listOf`, `arrayOf`, string concatenation, etc.).
 * - **Do not** acquire locks, mutexes, or synchronized blocks.
 * - **Do not** perform file or network I/O.
 * - **Do not** call any function that may block or take an unpredictable amount of time.
 * - **Do not** call logging functions (e.g., `println`, `Log.d`).
 *
 * On Android, each [onAudioReady] invocation crosses a JNI boundary, so keep
 * work minimal to avoid adding latency. On Apple platforms, the same real-time
 * constraints apply via the Core Audio render callback.
 *
 * The other callbacks ([onStreamStateChanged], [onStreamError],
 * [onStreamUnderrun]) may be called on any thread and do not have real-time
 * constraints, though they should still return promptly.
 *
 * ## Example
 *
 * ```kotlin
 * val callback = object : AudioStreamCallback {
 *     override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
 *         for (i in 0 until numFrames) {
 *             buffer[i] = sin(phase) // generate a sine wave
 *             phase += phaseIncrement
 *         }
 *         return numFrames
 *     }
 *
 *     override fun onStreamError(stream: AudioStream, error: KlarinetException) {
 *         // Handle error, e.g., attempt to reopen the stream
 *     }
 * }
 * ```
 *
 * @see AudioEngine.openStream
 */
interface AudioStreamCallback {

    /**
     * Called when the audio stream needs data (output) or has data available (input).
     *
     * For **output** streams, fill [buffer] with [numFrames] frames of audio
     * data. Samples are interleaved by channel (e.g., `[L0, R0, L1, R1, ...]`
     * for stereo).
     *
     * For **input** streams, [buffer] already contains [numFrames] frames of
     * captured audio data for the application to consume.
     *
     * **Warning -- real-time thread**: This is called on a high-priority audio
     * thread. Do not allocate memory, acquire locks, perform I/O, or call any
     * blocking function. On Android, each invocation crosses the JNI boundary
     * -- minimize work to avoid added latency.
     *
     * @param buffer The audio buffer to read from (input) or write to (output).
     *   The array length is at least `numFrames * channelCount`.
     * @param numFrames The number of frames requested (output) or available (input).
     * @return The number of frames actually processed. Return [numFrames] for
     *   normal operation. Returning fewer frames than requested for an output
     *   stream will result in silence for the remaining frames.
     */
    fun onAudioReady(buffer: FloatArray, numFrames: Int): Int = numFrames

    /**
     * Called when the stream's lifecycle state changes.
     *
     * This is called on a platform thread (not the real-time audio thread)
     * and may be used to update UI or log state transitions. Avoid long-running
     * work to prevent blocking the platform's audio management.
     *
     * @param stream The stream whose state changed.
     * @param state The new [StreamState]. See [StreamState] for the full
     *   lifecycle diagram.
     */
    fun onStreamStateChanged(stream: AudioStream, state: StreamState) {}

    /**
     * Called when an unrecoverable error occurs on the stream.
     *
     * After this callback fires, the stream is typically in a failed state
     * and should be closed. You may attempt to reopen a new stream via
     * [AudioEngine.openStream].
     *
     * This is called on a platform thread (not the real-time audio thread).
     *
     * @param stream The stream that encountered the error.
     * @param error The [KlarinetException] describing what went wrong.
     */
    fun onStreamError(stream: AudioStream, error: KlarinetException) {}

    /**
     * Called when an audio underrun (output) or overrun (input) is detected.
     *
     * An **underrun** occurs when the application fails to provide audio data
     * fast enough, causing gaps (glitches) in playback. An **overrun** occurs
     * when the application fails to consume captured audio data fast enough,
     * causing samples to be dropped.
     *
     * Frequent underruns indicate that the [AudioStreamCallback.onAudioReady]
     * implementation is too slow, or that the buffer size is too small.
     * Consider increasing [AudioStreamConfig.bufferCapacityInFrames] or
     * reducing the amount of work done in the callback.
     *
     * This is called on a platform thread (not the real-time audio thread).
     *
     * @param stream The stream that experienced the underrun or overrun.
     * @param count The cumulative number of underruns (or overruns) detected
     *   since the stream was started. This counter resets when the stream
     *   is stopped and restarted.
     */
    fun onStreamUnderrun(stream: AudioStream, count: Int) {}
}
