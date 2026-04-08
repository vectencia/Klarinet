package com.vectencia.klarinet

/**
 * Hint to the platform about the desired trade-off between audio latency
 * and power consumption.
 *
 * This is a *hint* -- the platform may not honor the request if the hardware
 * or current system state does not support it. The actual latency achieved
 * can be queried via [AudioStream.latencyInfo] after the stream is started.
 *
 * ## Choosing a Mode
 *
 * | Mode            | Use case                                      |
 * |-----------------|-----------------------------------------------|
 * | [LOW_LATENCY]   | Live audio, instruments, real-time effects     |
 * | [POWER_SAVING]  | Background music, long-running playback        |
 * | [NONE]          | No preference; let the platform decide         |
 *
 * @see AudioStreamConfig.performanceMode
 */
enum class PerformanceMode {
    /**
     * No specific performance mode requested.
     *
     * The platform will use its default behavior, which typically balances
     * latency and power consumption.
     */
    NONE,

    /**
     * Minimize audio latency at the expense of increased power consumption.
     *
     * Use this for real-time audio applications such as musical instruments,
     * live effects processing, or any scenario where the delay between
     * input and output must be as small as possible.
     *
     * On Android, this maps to Oboe's `PerformanceMode::LowLatency`, which
     * selects the smallest buffer size supported by the device. On Apple
     * platforms, a shorter I/O buffer duration is requested from Core Audio.
     */
    LOW_LATENCY,

    /**
     * Minimize power consumption at the expense of increased audio latency.
     *
     * Use this for scenarios where latency is not critical, such as
     * background music playback, podcast streaming, or long-running
     * recording sessions where battery life matters more than
     * responsiveness.
     *
     * On Android, this maps to Oboe's `PerformanceMode::PowerSaving`. On
     * Apple platforms, a longer I/O buffer duration is requested.
     */
    POWER_SAVING
}
