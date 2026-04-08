package com.vectencia.koboe

/**
 * Supported audio file formats for reading and writing.
 *
 * Each entry represents a container/codec combination that Koboe can
 * handle on at least one platform. Not every format is available on
 * every platform; attempting to use an unsupported format will throw
 * [UnsupportedFormatException].
 */
enum class AudioFileFormat {
    /** Waveform Audio File Format (uncompressed PCM). */
    WAV,

    /** MPEG Audio Layer III (lossy compressed). */
    MP3,

    /** Advanced Audio Coding (lossy compressed). */
    AAC,

    /** MPEG-4 audio container, typically containing AAC data. */
    M4A,
}
