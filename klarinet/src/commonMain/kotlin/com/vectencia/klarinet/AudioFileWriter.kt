package com.vectencia.klarinet

/**
 * Encodes and writes audio data to a file on disk.
 *
 * The writer accepts PCM float samples normalized to the range
 * [-1.0, 1.0] and encodes them into the specified [AudioFileFormat].
 * Samples must be interleaved when writing multi-channel audio.
 *
 * ## Usage
 *
 * ```kotlin
 * val writer = AudioFileWriter(
 *     filePath = "/path/to/output.wav",
 *     format = AudioFileFormat.WAV,
 *     sampleRate = 44100,
 *     channelCount = 2,
 * )
 * writer.writeFrames(samples, frameCount)
 * writer.close()
 * ```
 *
 * Always call [close] when finished to flush any buffered data and
 * release native resources.
 *
 * @constructor Creates a new audio file at [filePath].
 * @param filePath Absolute path for the output file.
 * @param format The audio container/codec format to use.
 * @param sampleRate The sample rate in Hz (e.g. 44100, 48000).
 * @param channelCount The number of audio channels (1 = mono, 2 = stereo).
 * @param tags Optional metadata tags to embed in the file.
 * @throws AudioFileException if the file cannot be created.
 * @throws UnsupportedFormatException if the format is not supported on
 *   the current platform.
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
     * @param data Interleaved PCM float samples to write.
     * @param numFrames The number of frames to write. Each frame contains
     *   one sample per channel, so `data` must contain at least
     *   `numFrames * channelCount` elements.
     * @throws AudioFileException if a write or encoding error occurs.
     */
    fun writeFrames(data: FloatArray, numFrames: Int)

    /**
     * Closes the writer, flushing any buffered data and releasing resources.
     *
     * After calling this method, no further writes may be performed.
     *
     * @throws AudioFileException if flushing buffered data fails.
     */
    fun close()
}
