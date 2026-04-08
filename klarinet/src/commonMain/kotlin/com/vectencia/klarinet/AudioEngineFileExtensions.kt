package com.vectencia.klarinet

/**
 * Play an audio file through the engine.
 *
 * Opens an [AudioFileReader], creates an output [AudioStream] whose sample
 * rate and channel count match the file, and decodes audio data in the
 * stream callback. The reader is closed automatically when the stream
 * transitions to [StreamState.STOPPED] or [StreamState.CLOSED].
 *
 * Playback ends naturally when the reader reaches the end of the file
 * (the callback returns `0` frames). You can also stop playback early
 * by calling [AudioStream.stop] or [AudioStream.close].
 *
 * ## Usage
 *
 * ```kotlin
 * val engine = AudioEngine()
 * val stream = engine.playFile("/path/to/music.mp3")
 * stream.start()  // begins playback
 *
 * // Later, to stop early:
 * stream.stop()
 * stream.close()
 * ```
 *
 * ## With coroutines
 *
 * ```kotlin
 * val stream = engine.playFile("/path/to/music.mp3")
 * stream.start()
 * stream.awaitState(StreamState.STOPPED) // suspends until playback ends
 * stream.close()
 * ```
 *
 * @param filePath Absolute path to a local audio file, or an HTTP/HTTPS URL.
 * @param config Base stream configuration. The [AudioStreamConfig.sampleRate]
 *   and [AudioStreamConfig.channelCount] properties will be overridden
 *   to match the file. The [AudioStreamConfig.direction] is forced to
 *   [StreamDirection.OUTPUT].
 * @return An [AudioStream] in the [StreamState.OPEN] state. Call
 *   [AudioStream.start] to begin playback. The underlying
 *   [AudioFileReader] is closed automatically when the stream is
 *   stopped or closed.
 * @throws AudioFileException if the file cannot be opened or decoded.
 * @see AudioFileReader
 * @see AudioEngine.recordToFile
 */
fun AudioEngine.playFile(
    filePath: String,
    config: AudioStreamConfig = AudioStreamConfig(),
): AudioStream {
    val reader = AudioFileReader(filePath)
    val streamConfig = config.copy(
        sampleRate = reader.info.sampleRate,
        channelCount = reader.info.channelCount,
        direction = StreamDirection.OUTPUT,
    )
    return openStream(streamConfig, callback = object : AudioStreamCallback {
        override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
            if (reader.isAtEnd) return 0
            val decoded = reader.readFrames(numFrames)
            if (decoded.isEmpty()) return 0
            decoded.copyInto(buffer)
            return decoded.size / streamConfig.channelCount
        }

        override fun onStreamStateChanged(stream: AudioStream, state: StreamState) {
            if (state == StreamState.CLOSED || state == StreamState.STOPPED) {
                try { reader.close() } catch (_: Exception) {}
            }
        }
    })
}

/**
 * Record audio from the microphone to a file.
 *
 * Opens an [AudioFileWriter] and creates an input [AudioStream] that
 * writes captured audio to the file in the stream callback. The writer
 * is finalized and closed automatically when the stream transitions to
 * [StreamState.STOPPED] or [StreamState.CLOSED], ensuring the output
 * file is properly finalized (e.g., WAV headers are written).
 *
 * ## Usage
 *
 * ```kotlin
 * val engine = AudioEngine()
 *
 * // Record to a WAV file (default format)
 * val stream = engine.recordToFile("/path/to/recording.wav")
 * stream.start()  // begins recording
 *
 * // ... record for a while ...
 *
 * stream.stop()   // stops recording and finalizes the file
 * stream.close()
 * ```
 *
 * ## Recording to a compressed format
 *
 * ```kotlin
 * val stream = engine.recordToFile(
 *     filePath = "/path/to/recording.m4a",
 *     format = AudioFileFormat.M4A,
 *     config = AudioStreamConfig(sampleRate = 44100, channelCount = 1),
 * )
 * stream.start()
 * ```
 *
 * @param filePath Absolute path for the output file. Parent directories
 *   must already exist.
 * @param format Audio file format to use for encoding. Defaults to
 *   [AudioFileFormat.WAV].
 * @param config Stream configuration. The [AudioStreamConfig.direction]
 *   is forced to [StreamDirection.INPUT]. The [AudioStreamConfig.sampleRate]
 *   and [AudioStreamConfig.channelCount] from this config are used for
 *   both the stream and the output file.
 * @return An [AudioStream] in the [StreamState.OPEN] state. Call
 *   [AudioStream.start] to begin recording. The underlying
 *   [AudioFileWriter] is closed automatically when the stream is
 *   stopped or closed.
 * @throws AudioFileException if the output file cannot be created.
 * @throws UnsupportedFormatException if [format] is not supported on
 *   the current platform.
 * @see AudioFileWriter
 * @see AudioEngine.playFile
 */
fun AudioEngine.recordToFile(
    filePath: String,
    format: AudioFileFormat = AudioFileFormat.WAV,
    config: AudioStreamConfig = AudioStreamConfig(direction = StreamDirection.INPUT),
): AudioStream {
    val streamConfig = config.copy(direction = StreamDirection.INPUT)
    val writer = AudioFileWriter(
        filePath = filePath,
        format = format,
        sampleRate = streamConfig.sampleRate,
        channelCount = streamConfig.channelCount,
    )
    return openStream(streamConfig, callback = object : AudioStreamCallback {
        override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
            writer.writeFrames(buffer, numFrames)
            return numFrames
        }

        override fun onStreamStateChanged(stream: AudioStream, state: StreamState) {
            if (state == StreamState.STOPPED || state == StreamState.CLOSED) {
                try { writer.close() } catch (_: Exception) {}
            }
        }
    })
}
