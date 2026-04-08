package com.vectencia.koboe

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

actual class AudioFileWriter actual constructor(
    filePath: String,
    format: AudioFileFormat,
    sampleRate: Int,
    channelCount: Int,
    tags: AudioFileTags?,
) {
    private val delegate: AudioFileWriterDelegate

    init {
        delegate = when (format) {
            AudioFileFormat.WAV -> WavFileWriterDelegate(filePath, sampleRate, channelCount, tags)
            AudioFileFormat.AAC, AudioFileFormat.M4A -> AacFileWriterDelegate(
                filePath, sampleRate, channelCount
            )
            AudioFileFormat.MP3 -> throw UnsupportedFormatException(
                "MP3 encoding is not supported"
            )
        }
    }

    actual fun writeFrames(data: FloatArray, numFrames: Int) {
        delegate.writeFrames(data, numFrames)
    }

    actual fun close() {
        delegate.close()
    }
}

/**
 * Internal interface for format-specific writer implementations.
 */
private interface AudioFileWriterDelegate {
    fun writeFrames(data: FloatArray, numFrames: Int)
    fun close()
}

// ---------------------------------------------------------------------------
// WAV writer — manual RIFF header + raw PCM
// ---------------------------------------------------------------------------

private class WavFileWriterDelegate(
    filePath: String,
    private val sampleRate: Int,
    private val channelCount: Int,
    private val tags: AudioFileTags?,
) : AudioFileWriterDelegate {

    private val raf = RandomAccessFile(File(filePath), "rw")
    private var dataByteCount = 0L
    private val bitsPerSample = 16

    init {
        writeWavHeader()
    }

    /**
     * Writes a placeholder WAV header. Sizes are patched in [close].
     *
     * Layout (44 bytes):
     *   RIFF header  : 12 bytes (ChunkID, ChunkSize, Format)
     *   fmt  sub-chunk: 24 bytes (SubchunkID, SubchunkSize, AudioFormat, ...)
     *   data sub-chunk header: 8 bytes (SubchunkID, SubchunkSize)
     */
    private fun writeWavHeader() {
        val byteRate = sampleRate * channelCount * (bitsPerSample / 8)
        val blockAlign = channelCount * (bitsPerSample / 8)

        val buf = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(0) // placeholder for RIFF chunk size
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))

        // fmt sub-chunk
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16) // SubchunkSize for PCM
        buf.putShort(1) // AudioFormat = PCM
        buf.putShort(channelCount.toShort())
        buf.putInt(sampleRate)
        buf.putInt(byteRate)
        buf.putShort(blockAlign.toShort())
        buf.putShort(bitsPerSample.toShort())

        // data sub-chunk header
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(0) // placeholder for data chunk size

        raf.write(buf.array())
    }

    override fun writeFrames(data: FloatArray, numFrames: Int) {
        val totalSamples = numFrames * channelCount
        val bytesPerSample = bitsPerSample / 8
        val buf = ByteBuffer.allocate(totalSamples * bytesPerSample)
            .order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until totalSamples) {
            val clamped = data[i].coerceIn(-1.0f, 1.0f)
            val shortVal = (clamped * Short.MAX_VALUE).toInt().toShort()
            buf.putShort(shortVal)
        }

        raf.write(buf.array())
        dataByteCount += totalSamples * bytesPerSample
    }

    override fun close() {
        // Write INFO LIST chunk for tags if any tag is present
        var infoChunkSize = 0L
        if (tags != null) {
            infoChunkSize = writeInfoChunk(tags)
        }

        // Total file size minus the 8-byte RIFF header
        val riffChunkSize = (4 + 24 + 8 + dataByteCount + infoChunkSize).toInt()

        // Patch RIFF chunk size (offset 4)
        raf.seek(4)
        val riffBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        riffBuf.putInt(riffChunkSize)
        raf.write(riffBuf.array())

        // Patch data sub-chunk size (offset 40)
        raf.seek(40)
        val dataBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        dataBuf.putInt(dataByteCount.toInt())
        raf.write(dataBuf.array())

        raf.close()
    }

    /**
     * Writes a LIST INFO chunk after the PCM data.
     * Returns the total bytes written (including the LIST header).
     */
    private fun writeInfoChunk(tags: AudioFileTags): Long {
        val entries = mutableListOf<Pair<String, String>>()
        tags.title?.let { entries.add("INAM" to it) }
        tags.artist?.let { entries.add("IART" to it) }
        tags.genre?.let { entries.add("IGNR" to it) }

        if (entries.isEmpty()) return 0L

        // Position at end of data
        raf.seek(44 + dataByteCount)

        // Calculate INFO payload size
        var infoPayloadSize = 4 // "INFO" identifier
        for ((id, value) in entries) {
            val strBytes = value.toByteArray(Charsets.US_ASCII)
            val strLen = strBytes.size + 1 // null terminator
            val paddedLen = if (strLen % 2 != 0) strLen + 1 else strLen
            infoPayloadSize += 4 + 4 + paddedLen // id(4) + size(4) + padded string
        }

        val buf = ByteBuffer.allocate(8 + infoPayloadSize).order(ByteOrder.LITTLE_ENDIAN)

        // LIST header
        buf.put("LIST".toByteArray(Charsets.US_ASCII))
        buf.putInt(infoPayloadSize)
        buf.put("INFO".toByteArray(Charsets.US_ASCII))

        for ((id, value) in entries) {
            val strBytes = value.toByteArray(Charsets.US_ASCII)
            val strLen = strBytes.size + 1 // include null terminator
            val paddedLen = if (strLen % 2 != 0) strLen + 1 else strLen

            buf.put(id.toByteArray(Charsets.US_ASCII))
            buf.putInt(strLen)
            buf.put(strBytes)
            buf.put(0) // null terminator
            if (paddedLen > strLen) {
                buf.put(0) // padding byte
            }
        }

        raf.write(buf.array())
        return (8 + infoPayloadSize).toLong()
    }
}

// ---------------------------------------------------------------------------
// AAC / M4A writer — MediaCodec encoder + MediaMuxer
// ---------------------------------------------------------------------------

private class AacFileWriterDelegate(
    filePath: String,
    private val sampleRate: Int,
    private val channelCount: Int,
) : AudioFileWriterDelegate {

    private val codec: MediaCodec
    private val muxer: MediaMuxer
    private var muxerStarted = false
    private var trackIndex = -1
    private val bufferInfo = MediaCodec.BufferInfo()
    private val timeoutUs = 10_000L
    private var presentationTimeUs = 0L
    private val bitsPerSample = 16

    init {
        val bitRate = 128_000
        val mediaFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            sampleRate,
            channelCount,
        ).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC,
            )
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        muxer = MediaMuxer(filePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    override fun writeFrames(data: FloatArray, numFrames: Int) {
        val totalSamples = numFrames * channelCount
        // Convert float PCM to 16-bit PCM bytes
        val pcmBytes = ByteBuffer.allocate(totalSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until totalSamples) {
            val clamped = data[i].coerceIn(-1.0f, 1.0f)
            val shortVal = (clamped * Short.MAX_VALUE).toInt().toShort()
            pcmBytes.putShort(shortVal)
        }
        pcmBytes.flip()

        var offset = 0
        val totalBytes = totalSamples * 2

        while (offset < totalBytes) {
            // Feed input
            val inputIndex = codec.dequeueInputBuffer(timeoutUs)
            if (inputIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputIndex)
                if (inputBuffer != null) {
                    inputBuffer.clear()
                    val remaining = totalBytes - offset
                    val bytesToCopy = minOf(remaining, inputBuffer.remaining())
                    pcmBytes.position(offset)
                    pcmBytes.limit(offset + bytesToCopy)
                    inputBuffer.put(pcmBytes)
                    codec.queueInputBuffer(
                        inputIndex, 0, bytesToCopy, presentationTimeUs, 0
                    )
                    // Advance presentation time
                    val samplesWritten = bytesToCopy / 2
                    val framesWritten = samplesWritten / channelCount
                    presentationTimeUs += framesWritten * 1_000_000L / sampleRate
                    offset += bytesToCopy
                }
            }

            // Drain output
            drainEncoder(false)
        }
    }

    override fun close() {
        // Signal end of stream
        val inputIndex = codec.dequeueInputBuffer(timeoutUs)
        if (inputIndex >= 0) {
            codec.queueInputBuffer(
                inputIndex, 0, 0, presentationTimeUs,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )
        }

        // Drain remaining output
        drainEncoder(true)

        try {
            codec.stop()
            codec.release()
        } catch (_: Exception) {
        }

        try {
            if (muxerStarted) {
                muxer.stop()
            }
            muxer.release()
        } catch (_: Exception) {
        }
    }

    private fun drainEncoder(endOfStream: Boolean) {
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                outputIndex >= 0 -> {
                    val outputBuffer = codec.getOutputBuffer(outputIndex) ?: continue

                    if (bufferInfo.size > 0 && muxerStarted) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                    }

                    val isEos =
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (isEos) return
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = codec.outputFormat
                    trackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                }
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                    // If draining EOS but nothing available, break to avoid infinite loop
                    return
                }
                else -> return
            }
        }
    }
}
