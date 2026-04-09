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

    /** The configuration used to create this stream. */
    val config: AudioStreamConfig

    /** The current lifecycle state of this stream. */
    val state: StreamState

    /** Current latency measurements for this stream. */
    val latencyInfo: LatencyInfo

    /**
     * Start audio processing.
     *
     * Transitions the stream from [StreamState.OPEN] or [StreamState.PAUSED]
     * to [StreamState.STARTED].
     *
     * @throws StreamOperationException if the stream cannot be started.
     */
    fun start()

    /**
     * Pause audio processing.
     *
     * Transitions the stream from [StreamState.STARTED] to [StreamState.PAUSED].
     * The stream can be resumed by calling [start] again.
     *
     * @throws StreamOperationException if the stream cannot be paused.
     */
    fun pause()

    /**
     * Stop audio processing.
     *
     * Transitions the stream to [StreamState.STOPPED]. After stopping,
     * the stream must be closed via [close]; it cannot be restarted.
     *
     * @throws StreamOperationException if the stream cannot be stopped.
     */
    fun stop()

    /**
     * Close the stream and release all associated resources.
     *
     * After closing, this stream instance cannot be reused. Create a new
     * stream via [AudioEngine.openStream] if needed.
     */
    fun close()

    /**
     * Write audio data to an output stream.
     *
     * @param data Float array containing audio samples to write.
     * @param numFrames Number of frames to write.
     * @param timeoutNanos Maximum time to wait in nanoseconds. 0 = non-blocking.
     * @return The number of frames actually written, or a negative error code.
     * @throws StreamOperationException if the stream is not an output stream.
     */
    fun write(data: FloatArray, numFrames: Int, timeoutNanos: Long = 0): Int

    /**
     * Read audio data from an input stream.
     *
     * @param data Float array to fill with audio samples.
     * @param numFrames Number of frames to read.
     * @param timeoutNanos Maximum time to wait in nanoseconds. 0 = non-blocking.
     * @return The number of frames actually read, or a negative error code.
     * @throws StreamOperationException if the stream is not an input stream.
     */
    fun read(data: FloatArray, numFrames: Int, timeoutNanos: Long = 0): Int

    /**
     * The effect chain attached to this stream, or null if none.
     *
     * Setting this property attaches the chain to the stream's audio
     * pipeline. Setting it to null detaches the current chain.
     */
    var effectChain: AudioEffectChain?
}
