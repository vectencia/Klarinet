package com.vectencia.klarinet

/**
 * Supported audio file formats for reading and writing.
 *
 * Each entry represents a container/codec combination that Klarinet can
 * handle on at least one platform. Not every format is available on
 * every platform; attempting to use an unsupported format will throw
 * [UnsupportedFormatException].
 *
 * | Format | Compression | Read | Write |
 * |--------|-------------|------|-------|
 * | [WAV]  | None (PCM)  | All  | All   |
 * | [MP3]  | Lossy       | All  | Platform-dependent |
 * | [AAC]  | Lossy       | All  | Platform-dependent |
 * | [M4A]  | Lossy       | All  | Platform-dependent |
 *
 * @see AudioFileReader
 * @see AudioFileWriter
 */
enum class AudioFileFormat {
    /**
     * Waveform Audio File Format (uncompressed PCM).
     *
     * Produces lossless audio with no compression artifacts. Files are
     * significantly larger than compressed formats -- approximately
     * 10 MB per minute for 16-bit stereo at 44100 Hz.
     *
     * Supported for both reading and writing on all platforms.
     */
    WAV,

    /**
     * MPEG Audio Layer III (lossy compressed).
     *
     * A widely compatible lossy format. Read support is available on all
     * platforms. Write support depends on the availability of a platform
     * MP3 encoder.
     */
    MP3,

    /**
     * Advanced Audio Coding (lossy compressed).
     *
     * Provides better audio quality than MP3 at similar bit rates. Uses
     * platform-native AAC encoders/decoders (e.g., MediaCodec on Android,
     * AudioToolbox on Apple platforms).
     *
     * When writing, this produces a raw AAC stream. For an AAC stream
     * inside an MPEG-4 container, use [M4A] instead.
     */
    AAC,

    /**
     * MPEG-4 audio container, typically containing AAC-encoded data.
     *
     * This format wraps AAC audio in an MPEG-4 (ISO 14496-14) container,
     * which allows embedding metadata tags and provides better seeking
     * support than raw [AAC]. This is the format used by Apple Music and
     * iTunes downloads.
     */
    M4A,
}
