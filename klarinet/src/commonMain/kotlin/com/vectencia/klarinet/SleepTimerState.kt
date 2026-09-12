package com.vectencia.klarinet

/**
 * Lifecycle of a [SleepTimer].
 *
 * ```
 * IDLE -> SCHEDULED -> PAUSED -> SCHEDULED
 *                   -> FADING -> COMPLETED
 *       -> IDLE (cancel / close)
 * ```
 */
enum class SleepTimerState {
    /** No countdown is active. */
    IDLE,

    /** Counting down toward fade-out. */
    SCHEDULED,

    /** Countdown is frozen; playback is not stopped. */
    PAUSED,

    /** Expiry reached; gain is fading toward silence. */
    FADING,

    /** Fade finished and the stream has been stopped. */
    COMPLETED,
}
