package com.vectencia.klarinet

/**
 * Play an audio file through the engine.
 *
 * Opens an [AudioFileReader], creates an output [AudioStream] matching the file's
 * sample rate and channel count, and decodes audio in the callback.
 *
 * @param filePath Path to the audio file.
 * @param config Base stream config. Sample rate and channel count will be overridden
 *   to match the file.
 * @return An [AudioStream] in the OPEN state. Call [AudioStream.start] to begin playback.
 *   The reader is closed automatically when the stream is closed.
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
 * Record audio to a file.
 *
 * Opens an [AudioFileWriter] and creates an input [AudioStream] that writes
 * captured audio to the file in the callback.
 *
 * @param filePath Path for the output file.
 * @param format Audio file format. Defaults to WAV.
 * @param config Stream config. Direction will be overridden to INPUT.
 * @return An [AudioStream] in the OPEN state. Call [AudioStream.start] to begin recording.
 *   The writer is finalized and closed when the stream is stopped or closed.
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
