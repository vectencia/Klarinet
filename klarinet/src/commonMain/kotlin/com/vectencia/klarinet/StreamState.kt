package com.vectencia.klarinet

/**
 * Lifecycle state of an [AudioStream].
 *
 * A stream progresses through these states as the application calls control
 * methods on it. The typical happy-path lifecycle is:
 *
 * ```
 * UNINITIALIZED -> OPEN -> STARTING -> STARTED
 *                                    -> PAUSING -> PAUSED -> STARTING -> STARTED
 *                                    -> STOPPING -> STOPPED -> CLOSING -> CLOSED
 * ```
 *
 * Transitional states ([STARTING], [PAUSING], [STOPPING], [CLOSING]) are
 * short-lived and indicate that the platform is processing the requested
 * state change. You can observe transitions via
 * [AudioStreamCallback.onStreamStateChanged].
 *
 * @see AudioStream.state
 * @see AudioStreamCallback.onStreamStateChanged
 */
enum class StreamState {
    /**
     * The stream has not been initialized yet.
     *
     * This is the initial state before [AudioEngine.openStream] completes.
     * You should never see this state on a successfully created stream.
     */
    UNINITIALIZED,

    /**
     * The stream has been created and configured but is not yet processing
     * audio.
     *
     * This is the state immediately after [AudioEngine.openStream] returns.
     * Call [AudioStream.start] to begin audio processing.
     */
    OPEN,

    /**
     * The stream is transitioning to the [STARTED] state.
     *
     * This is a transient state. The audio hardware is being activated.
     */
    STARTING,

    /**
     * The stream is actively processing audio.
     *
     * [AudioStreamCallback.onAudioReady] will be called repeatedly while
     * the stream remains in this state. From here, you can call
     * [AudioStream.pause] or [AudioStream.stop].
     */
    STARTED,

    /**
     * The stream is transitioning from [STARTED] to [PAUSED].
     *
     * This is a transient state while the platform suspends audio processing.
     */
    PAUSING,

    /**
     * Audio processing is temporarily suspended.
     *
     * The stream retains its resources and can be resumed by calling
     * [AudioStream.start] again. No audio callbacks will fire while paused.
     */
    PAUSED,

    /**
     * The stream is transitioning from [STARTED] or [PAUSED] to [STOPPED].
     *
     * This is a transient state while the platform finalizes audio processing.
     */
    STOPPING,

    /**
     * Audio processing has ended.
     *
     * The stream can no longer be restarted. Call [AudioStream.close] to
     * release its resources.
     */
    STOPPED,

    /**
     * The stream is transitioning to the [CLOSED] state.
     *
     * This is a transient state while native resources are being released.
     */
    CLOSING,

    /**
     * The stream has been closed and all resources have been released.
     *
     * This is a terminal state. The [AudioStream] instance can no longer be
     * used. Create a new stream via [AudioEngine.openStream] if needed.
     */
    CLOSED
}
