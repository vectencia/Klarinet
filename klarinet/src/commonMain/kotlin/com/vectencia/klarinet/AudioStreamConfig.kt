package com.vectencia.klarinet

/**
 * Configuration for creating an [AudioStream] via [AudioEngine.openStream].
 *
 * All properties have sensible defaults, so you only need to specify the ones
 * you want to override. Unspecified values will be resolved by the platform.
 *
 * ## Example
 *
 * ```kotlin
 * // Stereo output stream at 48 kHz with low latency
 * val config = AudioStreamConfig(
 *     sampleRate = 48000,
 *     channelCount = 2,
 *     performanceMode = PerformanceMode.LOW_LATENCY,
 * )
 *
 * // Mono input stream for recording
 * val micConfig = AudioStreamConfig(
 *     direction = StreamDirection.INPUT,
 *     channelCount = 1,
 * )
 * ```
 *
 * ## Platform Behavior
 *
 * - **Android**: The platform may adjust the requested [sampleRate] and
 *   [bufferCapacityInFrames] to match the device's native values when
 *   [sharingMode] is [SharingMode.SHARED].
 * - **Apple**: Core Audio will perform sample-rate conversion automatically
 *   if the requested rate differs from the hardware rate.
 *
 * @property sampleRate Sample rate in Hz. Common values: 44100, 48000.
 *   Must be a positive integer. A value of 0 lets the platform choose.
 *   Default: 48000.
 * @property channelCount Number of audio channels. 1 = mono, 2 = stereo.
 *   Values above 2 are supported only if the hardware allows it.
 *   Must be a positive integer. Default: 1.
 * @property audioFormat Sample format used for the audio data buffer.
 *   Determines bit depth and data type of samples passed to
 *   [AudioStreamCallback.onAudioReady]. Default: [AudioFormat.PCM_FLOAT].
 * @property bufferCapacityInFrames Total buffer capacity in frames.
 *   A value of 0 lets the platform choose an optimal size.
 *   Larger values increase latency but reduce the risk of underruns.
 *   Must be non-negative. Default: 0 (platform default).
 * @property performanceMode Performance vs. power trade-off hint.
 *   [PerformanceMode.LOW_LATENCY] minimizes latency at the expense of power,
 *   while [PerformanceMode.POWER_SAVING] does the opposite.
 *   Default: [PerformanceMode.LOW_LATENCY].
 * @property sharingMode Whether this stream should share the audio device
 *   with other applications. [SharingMode.EXCLUSIVE] may provide lower
 *   latency but can fail if another app already holds the device.
 *   Default: [SharingMode.SHARED].
 * @property direction Whether this is a playback ([StreamDirection.OUTPUT])
 *   or recording ([StreamDirection.INPUT]) stream. This determines whether
 *   [AudioStream.write] or [AudioStream.read] should be used, and which
 *   callback path is taken in [AudioStreamCallback.onAudioReady].
 *   Default: [StreamDirection.OUTPUT].
 */
data class AudioStreamConfig(
    val sampleRate: Int = 48000,
    val channelCount: Int = 1,
    val audioFormat: AudioFormat = AudioFormat.PCM_FLOAT,
    val bufferCapacityInFrames: Int = 0,
    val performanceMode: PerformanceMode = PerformanceMode.LOW_LATENCY,
    val sharingMode: SharingMode = SharingMode.SHARED,
    val direction: StreamDirection = StreamDirection.OUTPUT,
)
