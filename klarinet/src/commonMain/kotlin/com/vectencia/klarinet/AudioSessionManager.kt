package com.vectencia.klarinet

/**
 * Manages the platform audio session, controlling how the app interacts with
 * the system audio infrastructure and other audio apps.
 *
 * On Apple platforms, this wraps `AVAudioSession` and must be configured before
 * opening audio streams. On Android, audio session management is handled by the
 * system, so methods are no-ops.
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
}
