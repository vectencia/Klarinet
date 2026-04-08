package com.vectencia.klarinet

/**
 * Reads and decodes audio data from a file on disk or a remote URL.
 *
 * The reader decodes compressed formats (MP3, AAC, M4A) into PCM float
 * samples normalized to the range `[-1.0, 1.0]`. Samples are interleaved
 * when the file contains multiple channels (left, right, left, right, ...).
 *
 * ## Supported formats
 *
 * - [AudioFileFormat.WAV] -- Uncompressed PCM. Supported on all platforms.
 * - [AudioFileFormat.MP3] -- MPEG Layer III. Supported on all platforms.
 * - [AudioFileFormat.AAC] / [AudioFileFormat.M4A] -- AAC in an MPEG-4
 *   container. Supported on all platforms via platform-native decoders.
 *
 * ## Threading
 *
 * A single [AudioFileReader] instance is **not** thread-safe. Do not call
 * [readAll], [readFrames], or [seekTo] concurrently from multiple threads.
 * If you need to read the same file from multiple threads, create a
 * separate reader per thread.
 *
 * ## Usage
 *
 * ```kotlin
 * // Read an entire file at once
 * val reader = AudioFileReader("/path/to/audio.wav")
 * val info = reader.info
 * println("Duration: ${info.durationMs} ms, Channels: ${info.channelCount}")
 * val samples = reader.readAll()
 * reader.close()
 *
 * // Stream in chunks (useful for large files)
 * val reader2 = AudioFileReader("https://example.com/audio.m4a")
 * while (!reader2.isAtEnd) {
 *     val chunk = reader2.readFrames(4096)
 *     // process chunk ...
 * }
 * reader2.close()
 * ```
 *
 * Always call [close] when finished to release native resources.
 *
 * @constructor Opens the audio file at [filePath] for reading.
 * @param filePath Absolute path to a local file, or an HTTP/HTTPS URL.
 * @throws AudioFileException if the file cannot be opened or the format
 *   is not recognized.
 * @see AudioFileInfo
 * @see AudioFileFormat
 */
expect class AudioFileReader(filePath: String) {

    /**
     * Metadata and format information for the opened file.
     *
     * Available immediately after construction. Contains the file's
     * sample rate, channel count, duration, bit rate, format, and
     * any embedded metadata tags (title, artist, album, etc.).
     *
     * @see AudioFileInfo
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
     * Reads and decodes all remaining audio frames into memory.
     *
     * This loads the entire remaining audio content at once, which may
     * consume significant memory for large files. For streaming use cases,
     * prefer [readFrames] in a loop or the coroutine-based
     * [com.vectencia.klarinet.coroutines.asFlow] extension.
     *
     * After this call, [isAtEnd] will return `true`.
     *
     * @return A [FloatArray] of interleaved PCM samples normalized to
     *   `[-1.0, 1.0]`. The array length equals
     *   `totalFrames * info.channelCount`.
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
     * After seeking, subsequent calls to [readFrames] or [readAll] will
     * decode from the new position. Seeking to position `0` resets the
     * reader to the beginning of the file and clears the [isAtEnd] flag.
     *
     * @param framePosition The zero-based frame index to seek to. Must be
     *   non-negative. Values beyond the end of the file will cause
     *   [isAtEnd] to return `true` on the next read.
     * @throws AudioFileException if the seek operation fails or the
     *   format does not support seeking.
     */
    fun seekTo(framePosition: Long)

    /**
     * Closes the reader and releases all associated native resources.
     *
     * After calling this method, no further reads or seeks may be
     * performed. Calling [close] on an already-closed reader is a no-op.
     */
    fun close()
}
