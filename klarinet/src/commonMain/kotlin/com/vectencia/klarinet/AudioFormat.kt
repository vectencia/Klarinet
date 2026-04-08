package com.vectencia.klarinet

/**
 * Supported audio sample formats.
 *
 * Determines the bit depth and data type of audio samples exchanged between
 * the application and the audio hardware. The chosen format affects both
 * audio quality and CPU usage.
 *
 * For most real-time use cases, [PCM_FLOAT] is recommended because it
 * provides the widest dynamic range and avoids clipping artifacts during
 * intermediate processing. Integer formats ([PCM_I16], [PCM_I24], [PCM_I32])
 * are useful when interoperating with file formats or hardware that
 * expects fixed-point data.
 *
 * @see AudioStreamConfig.audioFormat
 */
enum class AudioFormat {
    /**
     * 32-bit IEEE 754 floating-point samples.
     *
     * Sample values are normalized to the range **[-1.0, 1.0]**.
     * This is the default and recommended format for real-time audio
     * processing, as it simplifies DSP math and avoids integer overflow.
     */
    PCM_FLOAT,

    /**
     * 16-bit signed integer samples (little-endian).
     *
     * Sample values range from **-32768 to 32767**. This is the most common
     * format for CD-quality audio and telephony. Use this when memory
     * bandwidth is a concern or when interfacing with 16-bit audio files.
     */
    PCM_I16,

    /**
     * 24-bit signed integer samples, packed into 3 bytes per sample.
     *
     * Sample values range from **-8388608 to 8388607**. Provides higher
     * dynamic range than [PCM_I16] (approximately 144 dB vs. 96 dB) and is
     * commonly used in professional audio recording and high-resolution
     * audio files.
     */
    PCM_I24,

    /**
     * 32-bit signed integer samples.
     *
     * Sample values range from **-2147483648 to 2147483647**. Offers the
     * highest dynamic range among integer formats. Useful for high-precision
     * fixed-point processing or when interoperating with 32-bit integer
     * audio pipelines.
     */
    PCM_I32
}
