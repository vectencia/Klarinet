package com.vectencia.klarinet

/**
 * Controls whether an audio stream shares the audio device with other
 * applications or requests exclusive access.
 *
 * ## Platform Behavior
 *
 * - **Android**: In [SHARED] mode, the stream goes through the Android audio
 *   mixer, which may resample to the device's native rate. In [EXCLUSIVE]
 *   mode (AAudio only, Android 8.0+), the stream bypasses the mixer for
 *   lower latency, but will fail if another app already holds the device.
 * - **Apple**: Core Audio always mixes streams, so [EXCLUSIVE] is treated
 *   as a best-effort hint and may behave identically to [SHARED].
 *
 * @see AudioStreamConfig.sharingMode
 */
enum class SharingMode {
    /**
     * Share the audio device with other applications.
     *
     * This is the default and most compatible mode. The platform's audio
     * mixer combines this stream with others, which may add a small amount
     * of latency due to mixing and potential sample-rate conversion.
     */
    SHARED,

    /**
     * Request exclusive access to the audio device.
     *
     * When granted, the stream bypasses the system mixer and communicates
     * directly with the hardware, achieving the lowest possible latency.
     * However, the request may be denied if another application already
     * holds exclusive access, in which case stream creation will fall back
     * to [SHARED] mode or throw a [StreamCreationException].
     */
    EXCLUSIVE
}
