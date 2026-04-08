package com.vectencia.klarinet

/**
 * Describes the format and properties of an audio file.
 *
 * Instances are returned by [AudioFileReader.info] and provide all the
 * metadata needed to interpret the decoded audio data. This includes
 * both technical properties (sample rate, channels, duration) and
 * user-facing metadata ([tags]).
 *
 * ## Calculating total frames
 *
 * The total number of audio frames can be derived from the duration and
 * sample rate:
 *
 * ```kotlin
 * val totalFrames = (info.durationMs * info.sampleRate) / 1000L
 * ```
 *
 * @see AudioFileReader.info
 * @see AudioFileFormat
 * @see AudioFileTags
 */
data class AudioFileInfo(
    /**
     * The container/codec format of the file (e.g., WAV, MP3, AAC, M4A).
     *
     * @see AudioFileFormat
     */
    val format: AudioFileFormat,

    /**
     * The sample rate in Hz (e.g. 44100, 48000).
     *
     * This is the number of audio samples per second per channel.
     * Common values are 44100 (CD quality), 48000 (broadcast/video),
     * and 96000 (high-resolution audio).
     */
    val sampleRate: Int,

    /**
     * The number of audio channels.
     *
     * Common values are `1` (mono) and `2` (stereo). When reading
     * interleaved PCM data, each frame contains this many samples.
     */
    val channelCount: Int,

    /**
     * The total duration of the audio in milliseconds.
     *
     * For streams or files where the duration cannot be determined,
     * this value may be an estimate.
     */
    val durationMs: Long,

    /**
     * The encoded bit rate in bits per second.
     *
     * For uncompressed formats like WAV, this equals
     * `sampleRate * channelCount * bitsPerSample`. For compressed
     * formats (MP3, AAC), this reflects the encoder's target bit rate.
     */
    val bitRate: Int,

    /**
     * The metadata tags embedded in the file (title, artist, album, etc.).
     *
     * Tags are always present but individual fields within [AudioFileTags]
     * may be `null` if the file does not contain the corresponding
     * metadata.
     *
     * @see AudioFileTags
     */
    val tags: AudioFileTags,
)
