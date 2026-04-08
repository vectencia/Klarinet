package com.vectencia.koboe

/**
 * Reads and decodes audio data from a file on disk.
 *
 * The reader decodes compressed formats (MP3, AAC, M4A) into PCM float
 * samples normalized to the range [-1.0, 1.0]. Samples are interleaved
 * when the file contains multiple channels.
 *
 * ## Usage
 *
 * ```kotlin
 * // From a local file
 * val reader = AudioFileReader("/path/to/audio.wav")
 *
 * // From a URL
 * val reader = AudioFileReader("https://example.com/audio.m4a")
 *
 * val info = reader.info
 * val samples = reader.readAll()
 * reader.close()
 * ```
 *
 * Always call [close] when finished to release native resources.
 *
 * @constructor Opens the audio file at [filePath] for reading.
 * @param filePath Absolute path to a local file, or an HTTP/HTTPS URL.
 * @throws AudioFileException if the file cannot be opened or the format
 *   is not recognized.
 */
expect class AudioFileReader(filePath: String) {

    /**
     * Metadata and format information for the opened file.
     *
     * Available immediately after construction.
     */
    val info: AudioFileInfo

    /**
     * Whether the reader has reached the end of the audio data.
     *
     * Returns `true` after [readFrames] has consumed all available frames,
     * or after [readAll] has been called.
     */
    val isAtEnd: Boolean

    /**
     * Reads and decodes all remaining audio frames.
     *
     * @return A [FloatArray] of interleaved PCM samples.
     * @throws AudioFileException if a decoding error occurs.
     */
    fun readAll(): FloatArray

    /**
     * Reads up to [maxFrames] audio frames from the current position.
     *
     * A frame consists of one sample per channel, so the returned array
     * length is at most `maxFrames * info.channelCount`.
     *
     * @param maxFrames The maximum number of frames to read.
     * @return A [FloatArray] of interleaved PCM samples. May be shorter
     *   than requested if the end of the file is reached.
     * @throws AudioFileException if a decoding error occurs.
     */
    fun readFrames(maxFrames: Int): FloatArray

    /**
     * Seeks to the specified frame position in the audio data.
     *
     * @param framePosition The zero-based frame index to seek to.
     * @throws AudioFileException if the seek operation fails or the
     *   format does not support seeking.
     */
    fun seekTo(framePosition: Long)

    /**
     * Closes the reader and releases all associated resources.
     *
     * After calling this method, no further reads may be performed.
     */
    fun close()
}
