package com.vectencia.klarinet

/**
 * Supported audio file formats for reading and writing.
 *
 * Each entry is available on at least one platform. Unsupported
 * combinations throw [UnsupportedFormatException].
 *
 * **Read:** [WAV] and [MP3] on Android, iOS/macOS/tvOS, JVM, and native
 * desktop. [AAC] and [M4A] on Android and iOS/macOS/tvOS. JS reads from
 * an in-memory store after `decodeAudioFile` / `putWavBytes` /
 * `putAudioFile` (or a previous [AudioFileWriter]). watchOS has no
 * [AudioFileReader].
 *
 * **Write:** [WAV] on every platform (JS is in-memory). [AAC] and [M4A]
 * on Android and iOS/macOS/tvOS. [MP3] encode throws on every platform.
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
     * Read and write on Android, iOS/macOS/tvOS, JVM, native desktop,
     * and JS (write is in-memory). watchOS can write WAV but cannot read
     * files.
     */
    WAV,

    /**
     * MPEG Audio Layer III (lossy compressed).
     *
     * A widely compatible lossy format. Readable on Android, iOS/macOS/tvOS,
     * JVM, native desktop, and JS after decode. Not readable on watchOS.
     * Encoding always throws [UnsupportedFormatException].
     */
    MP3,

    /**
     * Advanced Audio Coding (lossy compressed).
     *
     * Provides better audio quality than MP3 at similar bit rates. Uses
     * platform-native AAC encoders/decoders (e.g., MediaCodec on Android,
     * AudioToolbox on Apple platforms).
     *
     * Read and write on Android and iOS/macOS/tvOS. JS can read after
     * decode. JVM, native desktop, and watchOS throw
     * [UnsupportedFormatException]. When writing, this produces a raw AAC
     * stream; for an MPEG-4 container use [M4A].
     */
    AAC,

    /**
     * MPEG-4 audio container, typically containing AAC-encoded data.
     *
     * AAC in an MPEG-4 (ISO 14496-14) container, with tags and seeking.
     * Read and write on Android and iOS/macOS/tvOS. JS can read after
     * decode. JVM, native desktop, and watchOS throw
     * [UnsupportedFormatException].
     */
    M4A,
}
