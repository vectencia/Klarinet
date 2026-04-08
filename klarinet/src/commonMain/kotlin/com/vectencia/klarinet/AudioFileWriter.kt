package com.vectencia.klarinet

/**
 * Encodes and writes audio data to a file on disk.
 *
 * The writer accepts PCM float samples normalized to the range
 * `[-1.0, 1.0]` and encodes them into the specified [AudioFileFormat].
 * Samples must be interleaved when writing multi-channel audio
 * (left, right, left, right, ...).
 *
 * ## Supported output formats
 *
 * - [AudioFileFormat.WAV] -- Uncompressed PCM. Supported on all platforms.
 *   Produces the largest files but avoids encoding latency.
 * - [AudioFileFormat.MP3] -- MPEG Layer III (lossy). Platform-dependent
 *   encoder availability.
 * - [AudioFileFormat.AAC] -- Advanced Audio Coding (lossy). Available via
 *   platform-native encoders.
 * - [AudioFileFormat.M4A] -- AAC in an MPEG-4 container. Available via
 *   platform-native encoders.
 *
 * ## Threading
 *
 * A single [AudioFileWriter] instance is **not** thread-safe.
 * Do not call [writeFrames] concurrently from multiple threads.
 *
 * ## Usage
 *
 * ```kotlin
 * // Write a WAV file with metadata
 * val writer = AudioFileWriter(
 *     filePath = "/path/to/output.wav",
 *     format = AudioFileFormat.WAV,
 *     sampleRate = 44100,
 *     channelCount = 2,
 *     tags = AudioFileTags(title = "My Recording", artist = "Klarinet"),
 * )
 * writer.writeFrames(samples, frameCount)
 * writer.close() // must close to finalize the file header
 * ```
 *
 * Always call [close] when finished to flush any buffered data and
 * release native resources. Failing to close the writer may result in
 * a corrupt or incomplete output file.
 *
 * @constructor Creates a new audio file at [filePath].
 * @param filePath Absolute path for the output file. Parent directories
 *   must already exist.
 * @param format The audio container/codec format to use.
 * @param sampleRate The sample rate in Hz (e.g. 44100, 48000).
 * @param channelCount The number of audio channels (1 = mono, 2 = stereo).
 * @param tags Optional metadata tags to embed in the file. Pass `null`
 *   (the default) to omit metadata.
 * @throws AudioFileException if the file cannot be created.
 * @throws UnsupportedFormatException if the format is not supported on
 *   the current platform.
 * @see AudioFileFormat
 * @see AudioFileTags
 */
expect class AudioFileWriter(
    filePath: String,
    format: AudioFileFormat,
    sampleRate: Int,
    channelCount: Int,
    tags: AudioFileTags? = null,
) {

    /**
     * Writes audio frames to the file.
     *
     * This method may be called multiple times to append audio data
     * incrementally. The data is buffered internally and flushed to
     * disk as needed by the encoder.
     *
     * @param data Interleaved PCM float samples to write, normalized
     *   to the range `[-1.0, 1.0]`. Values outside this range may be
     *   clipped by the encoder.
     * @param numFrames The number of frames to write. Each frame contains
     *   one sample per channel, so [data] must contain at least
     *   `numFrames * channelCount` elements.
     * @throws AudioFileException if a write or encoding error occurs.
     */
    fun writeFrames(data: FloatArray, numFrames: Int)

    /**
     * Closes the writer, flushing any buffered data and releasing resources.
     *
     * For formats that require a finalized header (e.g., WAV), this method
     * writes the final header with the correct data size. Failing to call
     * [close] may result in a corrupt output file.
     *
     * After calling this method, no further writes may be performed.
     * Calling [close] on an already-closed writer is a no-op.
     *
     * @throws AudioFileException if flushing buffered data fails.
     */
    fun close()
}
