package com.vectencia.klarinet

class ChunkedAudioFileWriter(
    private val outputDirectory: String,
    private val filePrefix: String,
    private val format: AudioFileFormat,
    private val sampleRate: Int,
    private val channelCount: Int,
    private val maxChunkDurationMs: Long = 30 * 60 * 1000L,
    private val tags: AudioFileTags? = null,
) {
    private val maxFramesPerChunk = calculateMaxFramesPerChunk(maxChunkDurationMs, sampleRate)
    private var currentWriter: AudioFileWriter? = null
    private var framesInCurrentChunk: Long = 0
    private var chunkIndex: Int = 0
    private val _chunkPaths = mutableListOf<String>()

    val chunkPaths: List<String> get() = _chunkPaths.toList()
    val currentChunkPath: String? get() = _chunkPaths.lastOrNull()

    fun writeFrames(data: FloatArray, numFrames: Int) {
        var framesRemaining = numFrames
        var offset = 0

        while (framesRemaining > 0) {
            if (currentWriter == null) {
                openNextChunk()
            }

            val framesUntilRotation = maxFramesPerChunk - framesInCurrentChunk
            val framesToWrite = minOf(framesRemaining.toLong(), framesUntilRotation).toInt()

            if (framesToWrite > 0) {
                val slice = if (offset == 0 && framesToWrite == numFrames) {
                    data
                } else {
                    data.copyOfRange(
                        offset * channelCount,
                        (offset + framesToWrite) * channelCount,
                    )
                }
                currentWriter!!.writeFrames(slice, framesToWrite)
                framesInCurrentChunk += framesToWrite
                offset += framesToWrite
                framesRemaining -= framesToWrite
            }

            if (framesInCurrentChunk >= maxFramesPerChunk) {
                closeCurrentChunk()
            }
        }
    }

    fun close() {
        closeCurrentChunk()
    }

    private fun openNextChunk() {
        val path = generateChunkPath(outputDirectory, filePrefix, format, chunkIndex)
        currentWriter = AudioFileWriter(path, format, sampleRate, channelCount, tags)
        _chunkPaths.add(path)
        framesInCurrentChunk = 0
        chunkIndex++
    }

    private fun closeCurrentChunk() {
        currentWriter?.close()
        currentWriter = null
    }

    companion object {
        fun generateChunkPath(
            directory: String,
            prefix: String,
            format: AudioFileFormat,
            index: Int,
        ): String {
            val extension = when (format) {
                AudioFileFormat.WAV -> "wav"
                AudioFileFormat.AAC, AudioFileFormat.M4A -> "m4a"
                AudioFileFormat.MP3 -> "mp3"
            }
            return "$directory/${prefix}_${index.toString().padStart(3, '0')}.$extension"
        }

        fun calculateMaxFramesPerChunk(maxChunkDurationMs: Long, sampleRate: Int): Long {
            return maxChunkDurationMs * sampleRate / 1000
        }
    }
}
