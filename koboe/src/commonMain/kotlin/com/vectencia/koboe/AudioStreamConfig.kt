package com.vectencia.koboe

/**
 * Configuration for creating an audio stream.
 *
 * @property sampleRate Sample rate in Hz. Common values: 44100, 48000. Default: 48000.
 * @property channelCount Number of audio channels. 1 = mono, 2 = stereo. Default: 1.
 * @property audioFormat Sample format. Default: [AudioFormat.PCM_FLOAT].
 * @property bufferCapacityInFrames Buffer size in frames. 0 = platform default.
 * @property performanceMode Performance vs. power trade-off. Default: [PerformanceMode.LOW_LATENCY].
 * @property sharingMode Whether to share the device with other apps. Default: [SharingMode.SHARED].
 * @property direction Whether this is a playback or recording stream. Default: [StreamDirection.OUTPUT].
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
