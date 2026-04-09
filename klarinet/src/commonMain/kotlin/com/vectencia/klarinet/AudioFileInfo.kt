package com.vectencia.klarinet

/**
 * Describes the properties of an audio file.
 *
 * Instances are returned by [AudioFileReader.info] and provide all the
 * metadata needed to interpret the decoded audio data.
 */
data class AudioFileInfo(
    /** The container/codec format of the file. */
    val format: AudioFileFormat,

    /** The sample rate in Hz (e.g. 44100, 48000). */
    val sampleRate: Int,

    /** The number of audio channels (1 = mono, 2 = stereo). */
    val channelCount: Int,

    /** The total duration of the audio in milliseconds. */
    val durationMs: Long,

    /** The bit rate in bits per second. */
    val bitRate: Int,

    /** The metadata tags embedded in the file. */
    val tags: AudioFileTags,
)
