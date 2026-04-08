package com.vectencia.klarinet

/**
 * Manages the platform audio session.
 *
 * On Apple platforms, this maps to `AVAudioSession` configuration.
 * On Android, audio session management is handled by the system,
 * so methods are no-ops.
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
