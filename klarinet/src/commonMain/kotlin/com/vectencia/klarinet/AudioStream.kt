package com.vectencia.klarinet

/**
 * A platform audio stream for playback or recording.
 *
 * ## Lifecycle
 *
 * A stream moves through the following states:
 *
 * ```
 * UNINITIALIZED -> OPEN -> STARTING -> STARTED
 *                                    -> PAUSING -> PAUSED
 *                                    -> STOPPING -> STOPPED
 *                          CLOSING -> CLOSED
 * ```
 *
 * Create a stream via [AudioEngine.openStream]. The stream starts in the
 * [StreamState.OPEN] state. Call [start] to begin audio processing, [pause]
 * to temporarily halt, [stop] to end processing, and [close] to release
 * all resources.
 *
 * ## Thread Safety
 *
 * Stream control methods ([start], [pause], [stop], [close]) are safe to
 * call from any thread. The [write] and [read] methods should be called
 * from a dedicated audio thread or from within an [AudioStreamCallback].
 */
expect class AudioStream {

    /**
     * The configuration used to create this stream.
     *
     * Reflects the actual negotiated values, which may differ from what was
     * requested in [AudioEngine.openStream] if the platform adjusted them
     * (e.g., sample rate resampling in [SharingMode.SHARED] on Android).
     */
    val config: AudioStreamConfig

    /**
     * The current lifecycle state of this stream.
     *
     * This property is updated atomically and can be read from any thread.
     * To be notified of state transitions asynchronously, implement
     * [AudioStreamCallback.onStreamStateChanged].
     *
     * @see StreamState
     */
    val state: StreamState

    /**
     * Current latency measurements for this stream.
     *
     * The values are only meaningful while the stream is in the
     * [StreamState.STARTED] state. Before starting or after stopping,
     * both values will typically be zero.
     *
     * @see LatencyInfo
     */
    val latencyInfo: LatencyInfo

    /**
     * Start audio processing.
     *
     * Transitions the stream from [StreamState.OPEN] or [StreamState.PAUSED]
     * to [StreamState.STARTED]. Once started, the
     * [AudioStreamCallback.onAudioReady] callback will begin firing on the
     * audio thread (if a callback was provided at creation time).
     *
     * This method is safe to call from any thread.
     *
     * @throws StreamOperationException if the stream cannot be started,
     *   for example if it is already in the [StreamState.STOPPED] or
     *   [StreamState.CLOSED] state.
     */
    fun start()

    /**
     * Pause audio processing.
     *
     * Transitions the stream from [StreamState.STARTED] to [StreamState.PAUSED].
     * The stream retains its resources and can be resumed by calling [start]
     * again. No audio callbacks will fire while paused.
     *
     * This method is safe to call from any thread.
     *
     * @throws StreamOperationException if the stream cannot be paused,
     *   for example if it is not currently started.
     */
    fun pause()

    /**
     * Stop audio processing.
     *
     * Transitions the stream to [StreamState.STOPPED]. After stopping,
     * the stream must be closed via [close]; it **cannot** be restarted.
     * Use [pause]/[start] if you need to temporarily suspend and resume.
     *
     * This method is safe to call from any thread.
     *
     * @throws StreamOperationException if the stream cannot be stopped.
     */
    fun stop()

    /**
     * Close the stream and release all associated native resources.
     *
     * After closing, this stream instance cannot be reused. Create a new
     * stream via [AudioEngine.openStream] if needed. It is safe to call
     * [close] on an already-closed stream (it will be a no-op).
     *
     * This method is safe to call from any thread.
     */
    fun close()

    /**
     * Write audio data to an output stream.
     *
     * This method is used in blocking I/O mode (when no [AudioStreamCallback]
     * was provided). The samples in [data] are interleaved by channel:
     * for stereo, the layout is `[L0, R0, L1, R1, ...]`.
     *
     * @param data Float array containing interleaved audio samples to write.
     *   The array must contain at least `numFrames * channelCount` elements.
     * @param numFrames Number of frames to write. One frame contains one
     *   sample per channel.
     * @param timeoutNanos Maximum time to wait in nanoseconds for buffer
     *   space to become available. Use `0` for non-blocking behavior;
     *   `Long.MAX_VALUE` to block indefinitely.
     * @return The number of frames actually written, or a negative error code.
     * @throws StreamOperationException if this stream's direction is
     *   [StreamDirection.INPUT].
     */
    fun write(data: FloatArray, numFrames: Int, timeoutNanos: Long = 0): Int

    /**
     * Read audio data from an input stream.
     *
     * This method is used in blocking I/O mode (when no [AudioStreamCallback]
     * was provided). The samples in [data] are written interleaved by channel:
     * for stereo, the layout is `[L0, R0, L1, R1, ...]`.
     *
     * @param data Float array to fill with interleaved audio samples. The
     *   array must have capacity for at least `numFrames * channelCount`
     *   elements.
     * @param numFrames Number of frames to read. One frame contains one
     *   sample per channel.
     * @param timeoutNanos Maximum time to wait in nanoseconds for data to
     *   become available. Use `0` for non-blocking behavior;
     *   `Long.MAX_VALUE` to block indefinitely.
     * @return The number of frames actually read, or a negative error code.
     * @throws StreamOperationException if this stream's direction is
     *   [StreamDirection.OUTPUT].
     */
    fun read(data: FloatArray, numFrames: Int, timeoutNanos: Long = 0): Int

    /**
     * The effect chain currently attached to this stream, or `null` if none.
     *
     * Setting this property attaches the chain to the stream's native audio
     * pipeline. Effects in the chain are applied in order to every buffer
     * processed by the stream. Setting it to `null` detaches the current
     * chain, returning the stream to unprocessed pass-through.
     *
     * The chain can be swapped while the stream is running. The switch is
     * performed atomically at the next buffer boundary to avoid glitches.
     *
     * @see AudioEngine.createEffectChain
     * @see AudioEffectChain
     */
    var effectChain: AudioEffectChain?

    /**
     * Current peak audio level from the most recently processed callback buffer.
     *
     * Values range from `0.0f` (silence) to `1.0f` (full scale, 0 dBFS).
     * This property is updated on every audio callback and is intended for
     * driving level meters or VU displays. It is primarily consumed by the
     * `klarinet-coroutines` module, which exposes it as a `Flow`.
     *
     * For input streams, this reflects the peak level of the captured audio.
     * For output streams, it reflects the peak level of the rendered audio.
     *
     * This property is thread-safe and can be read from any thread.
     */
    val peakLevel: Float

    /**
     * Internal atomic storage for [peakLevel], written by the audio callback
     * thread and read by the `klarinet-coroutines` module.
     *
     * This is an implementation detail and should not be used by SDK consumers.
     */
    internal val peakLevelAtomic: AtomicFloat
}
