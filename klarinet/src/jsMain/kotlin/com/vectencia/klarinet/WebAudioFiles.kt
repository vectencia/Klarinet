package com.vectencia.klarinet

import kotlin.js.Promise

/**
 * Stores decoded PCM so [AudioFileReader] can open [path] synchronously.
 *
 * Browser file I/O is asynchronous; Klarinet's reader API is not. Decode a
 * URL with [decodeAudioFile], import a WAV with [putWavBytes], or write with
 * [AudioFileWriter] before constructing a reader.
 */
fun putAudioFile(
    path: String,
    samples: FloatArray,
    sampleRate: Int,
    channelCount: Int,
    format: AudioFileFormat = AudioFileFormat.WAV,
    tags: AudioFileTags = AudioFileTags(),
    durationMs: Long = if (sampleRate > 0) {
        (samples.size / channelCount.coerceAtLeast(1)) * 1000L / sampleRate
    } else {
        0L
    },
) {
    WebAudioStore.put(
        path,
        StoredAudio(
            samples = samples,
            info = AudioFileInfo(
                format = format,
                sampleRate = sampleRate,
                channelCount = channelCount,
                durationMs = durationMs,
                bitRate = sampleRate * channelCount * 32,
                tags = tags,
            ),
        ),
    )
}

/** Parses a WAV byte array (16-bit PCM or 32-bit float) into the in-memory store. */
fun putWavBytes(path: String, bytes: ByteArray, tags: AudioFileTags = AudioFileTags()) {
    val decoded = decodeWav(bytes)
    putAudioFile(
        path = path,
        samples = decoded.samples,
        sampleRate = decoded.sampleRate,
        channelCount = decoded.channelCount,
        format = AudioFileFormat.WAV,
        tags = tags,
    )
}

/** Encodes a previously stored file as a 16-bit PCM WAV. */
fun audioFileWavBytes(path: String): ByteArray {
    val stored = WebAudioStore.get(path)
        ?: throw AudioFileException("No stored audio at $path")
    return encodeWav16(stored.samples, stored.info.sampleRate, stored.info.channelCount)
}

/**
 * Decodes [bytes] with Web Audio `decodeAudioData` and stores the PCM so
 * [AudioFileReader] can open [path] synchronously.
 *
 * Use this for `<input type="file">` picks. WAV bytes can also go through
 * [putWavBytes] without creating an `AudioContext`.
 */
fun decodeAudioBytes(path: String, bytes: ByteArray): Promise<AudioFileReader> {
    val ctx = newAudioContext()
    return Promise { resolve, reject ->
        ctx.decodeAudioData(byteArrayToArrayBuffer(bytes)).then(
            { audio ->
                try {
                    putAudioFile(
                        path = path,
                        samples = interleave(audio),
                        sampleRate = audio.sampleRate.toInt(),
                        channelCount = audio.numberOfChannels,
                        format = formatFromPath(path),
                    )
                    try {
                        ctx.close()
                    } catch (_: Throwable) {
                    }
                    resolve(AudioFileReader(path))
                } catch (error: Throwable) {
                    reject(error)
                }
            },
            { error -> reject(error) },
        )
    }
}

/**
 * Fetches [url], decodes it with Web Audio `decodeAudioData`, and stores the
 * PCM so [AudioFileReader] can open the same URL synchronously.
 *
 * Works for any format the browser can decode (WAV, MP3, AAC, M4A, …).
 */
fun decodeAudioFile(url: String): Promise<AudioFileReader> {
    return Promise { resolve, reject ->
        fetch(url).then(
            { response ->
                response.arrayBuffer().then(
                    { buffer ->
                        decodeAudioBytes(url, arrayBufferToByteArray(buffer)).then(
                            { reader -> resolve(reader) },
                            { error -> reject(error) },
                        )
                    },
                    { error -> reject(error) },
                )
            },
            { error -> reject(error) },
        )
    }
}

internal fun interleave(buffer: AudioBuffer): FloatArray {
    val channels = buffer.numberOfChannels.coerceAtLeast(1)
    val frames = buffer.length
    val out = FloatArray(frames * channels)
    for (ch in 0 until channels) {
        val data = buffer.getChannelData(ch)
        for (frame in 0 until frames) {
            out[frame * channels + ch] = float32Get(data, frame)
        }
    }
    return out
}
