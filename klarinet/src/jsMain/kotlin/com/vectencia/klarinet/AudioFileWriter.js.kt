package com.vectencia.klarinet

actual class AudioFileWriter actual constructor(
    filePath: String,
    format: AudioFileFormat,
    sampleRate: Int,
    channelCount: Int,
    tags: AudioFileTags?,
) {
    private val path = filePath
    private val sampleRateHz = sampleRate
    private val channels = channelCount
    private val fileTags = tags ?: AudioFileTags()
    private val chunks = mutableListOf<FloatArray>()
    private var totalSamples = 0
    private var closed = false

    init {
        if (format != AudioFileFormat.WAV) {
            throw UnsupportedFormatException("JS AudioFileWriter only supports WAV (requested $format)")
        }
        if (sampleRate <= 0 || channelCount <= 0) {
            throw AudioFileException("Invalid writer format: sampleRate=$sampleRate channelCount=$channelCount")
        }
    }

    actual fun writeFrames(data: FloatArray, numFrames: Int) {
        if (closed) throw AudioFileException("AudioFileWriter has been closed")
        val samples = numFrames * channels
        if (samples <= 0) return
        if (data.size < samples) {
            throw AudioFileException("writeFrames expected $samples samples, got ${data.size}")
        }
        chunks.add(data.copyOf(samples))
        totalSamples += samples
    }

    actual fun close() {
        if (closed) return
        closed = true
        val samples = FloatArray(totalSamples)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(samples, offset)
            offset += chunk.size
        }
        chunks.clear()
        val frames = if (channels > 0) totalSamples / channels else 0
        val durationMs = if (sampleRateHz > 0) frames * 1000L / sampleRateHz else 0L
        putAudioFile(
            path = path,
            samples = samples,
            sampleRate = sampleRateHz,
            channelCount = channels,
            format = AudioFileFormat.WAV,
            tags = fileTags,
            durationMs = durationMs,
        )
    }
}
