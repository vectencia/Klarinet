package com.vectencia.klarinet

/**
 * Manages the platform audio session, controlling how the app interacts with
 * the system audio infrastructure and other audio apps.
 *
 * On Apple platforms, this wraps `AVAudioSession` and must be configured before
 * opening audio streams. On Android, call `bind(context)` then [setActive] to
 * request audio focus; interruptions are reported via [observeInterruptions].
 *
 * **Example usage:**
 * ```kotlin
 * val sessionManager = AudioSessionManager()
 *
 * // Configure for simultaneous recording and playback
 * sessionManager.configure(
 *     category = AudioSessionCategory.PLAY_AND_RECORD,
 *     mode = AudioSessionMode.MEASUREMENT,
 * )
 *
 * // Activate the session before opening streams
 * sessionManager.setActive(true)
 *
 * // Listen for route changes (e.g., headphones plugged in/out)
 * sessionManager.observeRouteChanges { info ->
 *     println("Audio route changed: ${info.reason}")
 * }
 *
 * // Deactivate when audio is no longer needed
 * sessionManager.setActive(false)
 * ```
 *
 * @see AudioSessionCategory
 * @see AudioSessionMode
 * @see AudioRouteChangeInfo
 */
expect class AudioSessionManager {

    /**
     * Configure the audio session category and mode.
     *
     * @param category The audio session category (e.g., playback, recording).
     * @param mode The audio session mode (e.g., default, measurement).
     * @throws AudioSessionException if the configuration fails.
     */
    fun configure(category: AudioSessionCategory, mode: AudioSessionMode)

    /**
     * Activate or deactivate the audio session.
     *
     * @param active Whether the session should be active.
     * @throws AudioSessionException if activation/deactivation fails.
     */
    fun setActive(active: Boolean)

    /**
     * Register a listener for audio route change events.
     *
     * @param listener Callback invoked when the audio route changes
     *   (e.g., headphones plugged in/out).
     */
    fun observeRouteChanges(listener: (AudioRouteChangeInfo) -> Unit)

    /**
     * Register a listener for audio interruptions (phone calls, focus loss).
     *
     * On Apple this is `AVAudioSessionInterruptionNotification`. On Android
     * this is audio-focus change after [setActive] (requires `bind(context)`
     * on Android). JVM, JS, and desktop never fire.
     *
     * Attached streams are paused on [AudioInterruptionType.BEGAN] and
     * restarted on [AudioInterruptionType.ENDED] when [AudioInterruptionInfo.shouldResume]
     * is true. The listener still runs after that.
     *
     * @param listener Callback invoked when an interruption begins or ends.
     */
    fun observeInterruptions(listener: (AudioInterruptionInfo) -> Unit)

    /**
     * Pause and resume this stream automatically on interruptions.
     * Does not start playback by itself. Does not close the stream.
     */
    fun attach(stream: AudioStream)

    /**
     * Stop auto-handling [stream]. No-op if it was not [attach]ed.
     */
    fun detach(stream: AudioStream)
}
