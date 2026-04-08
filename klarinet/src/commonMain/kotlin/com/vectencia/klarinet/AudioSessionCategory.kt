package com.vectencia.klarinet

/**
 * Defines the audio session category, which determines how the app interacts with
 * the system audio and other apps.
 *
 * On Apple platforms, each value maps to an `AVAudioSession.Category`. On Android,
 * audio session categories are managed by the system and these values serve as
 * declarative hints processed by the platform-specific implementation.
 *
 * @see AudioSessionManager.configure
 * @see AudioSessionMode
 */
expect enum class AudioSessionCategory {

    /**
     * The app plays audio that is central to the user experience.
     *
     * Audio continues when the screen locks and silences other apps' audio.
     * On Apple, this maps to `AVAudioSession.Category.playback`.
     */
    PLAYBACK,

    /**
     * The app records audio input (e.g., from the microphone).
     *
     * On Apple, this maps to `AVAudioSession.Category.record`. The app can capture
     * audio but cannot play audio through the speaker.
     */
    RECORD,

    /**
     * The app simultaneously plays and records audio.
     *
     * Use this for voice/video chat, real-time audio processing, or any scenario
     * requiring both input and output. On Apple, this maps to
     * `AVAudioSession.Category.playAndRecord`.
     */
    PLAY_AND_RECORD,

    /**
     * The app plays non-essential audio that mixes with other apps.
     *
     * Audio is silenced by the silent switch and does not interrupt other apps'
     * audio. On Apple, this maps to `AVAudioSession.Category.ambient`.
     */
    AMBIENT,
}
