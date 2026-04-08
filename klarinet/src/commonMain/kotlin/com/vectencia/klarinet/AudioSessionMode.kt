package com.vectencia.klarinet

/**
 * Defines the audio session mode, which further specializes the behavior of the
 * selected [AudioSessionCategory].
 *
 * On Apple platforms, each value maps to an `AVAudioSession.Mode`. On Android,
 * these values serve as declarative hints processed by the platform-specific implementation.
 *
 * @see AudioSessionManager.configure
 * @see AudioSessionCategory
 */
expect enum class AudioSessionMode {

    /**
     * The default mode with no special audio processing behavior.
     *
     * Suitable for general-purpose playback and recording. On Apple, this maps
     * to `AVAudioSession.Mode.default`.
     */
    DEFAULT,

    /**
     * Optimized for audio measurement and analysis.
     *
     * Disables system-provided signal processing (e.g., automatic gain control,
     * noise suppression) to deliver a flat, unaltered signal. Ideal for tuner
     * apps, spectrum analyzers, or audio metering. On Apple, this maps to
     * `AVAudioSession.Mode.measurement`.
     */
    MEASUREMENT,

    /**
     * Optimized for two-way voice communication.
     *
     * Enables echo cancellation and may route audio to the earpiece speaker
     * by default. On Apple, this maps to `AVAudioSession.Mode.voiceChat`.
     */
    VOICE_CHAT,

    /**
     * Optimized for voice communication in gaming contexts.
     *
     * Similar to [VOICE_CHAT] but tuned for game audio scenarios where voice
     * chat is mixed with game sounds. On Apple, this maps to
     * `AVAudioSession.Mode.gameChat`.
     */
    GAME_CHAT,
}
