@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.vectencia.klarinet

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.set
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fseek
import platform.posix.fwrite

actual class AudioFileWriter actual constructor(
    filePath: String,
    format: AudioFileFormat,
    sampleRate: Int,
    channelCount: Int,
    tags: AudioFileTags?,
) {
    private val fileHandle = when (format) {
        AudioFileFormat.WAV -> fopen(filePath, "wb")
            ?: throw AudioFileException("Failed to create WAV file: $filePath")
        AudioFileFormat.AAC, AudioFileFormat.M4A -> throw UnsupportedFormatException(
            "AAC/M4A encoding is not available on watchOS."
        )
        AudioFileFormat.MP3 -> throw UnsupportedFormatException("MP3 encoding is not supported")
    }

    private val channelCount = channelCount
    private val sampleRate = sampleRate
    private var dataByteCount = 0L
    private val bitsPerSample = 16

    init { writeWavHeader() }

    actual fun writeFrames(data: FloatArray, numFrames: Int) {
        val totalSamples = numFrames * channelCount
        val totalBytes = totalSamples * (bitsPerSample / 8)
        val pcmBytes = ByteArray(totalBytes)
        for (i in 0 until totalSamples) {
            val clamped = data[i].coerceIn(-1.0f, 1.0f)
            val shortVal = (clamped * Short.MAX_VALUE).toInt().toShort()
            pcmBytes[i * 2] = (shortVal.toInt() and 0xFF).toByte()
            pcmBytes[i * 2 + 1] = ((shortVal.toInt() shr 8) and 0xFF).toByte()
        }
        writeBytes(pcmBytes)
        dataByteCount += totalBytes
    }

    actual fun close() {
        val riffChunkSize = (4 + 24 + 8 + dataByteCount).toInt()
        val patch = ByteArray(4)
        fseek(fileHandle, 4, SEEK_SET)
        writeInt32LE(patch, 0, riffChunkSize); writeBytes(patch)
        fseek(fileHandle, 40, SEEK_SET)
        writeInt32LE(patch, 0, dataByteCount.toInt()); writeBytes(patch)
        fclose(fileHandle)
    }

    private fun writeWavHeader() {
        val byteRate = sampleRate * channelCount * (bitsPerSample / 8)
        val blockAlign = channelCount * (bitsPerSample / 8)
        val header = ByteArray(44); var offset = 0
        "RIFF".encodeToByteArray().copyInto(header, offset); offset += 4
        writeInt32LE(header, offset, 0); offset += 4
        "WAVE".encodeToByteArray().copyInto(header, offset); offset += 4
        "fmt ".encodeToByteArray().copyInto(header, offset); offset += 4
        writeInt32LE(header, offset, 16); offset += 4
        writeInt16LE(header, offset, 1); offset += 2
        writeInt16LE(header, offset, channelCount); offset += 2
        writeInt32LE(header, offset, sampleRate); offset += 4
        writeInt32LE(header, offset, byteRate); offset += 4
        writeInt16LE(header, offset, blockAlign); offset += 2
        writeInt16LE(header, offset, bitsPerSample); offset += 2
        "data".encodeToByteArray().copyInto(header, offset); offset += 4
        writeInt32LE(header, offset, 0); offset += 4
        writeBytes(header)
    }

    private fun writeBytes(bytes: ByteArray) {
        memScoped {
            val buf = allocArray<ByteVar>(bytes.size)
            for (i in bytes.indices) { buf[i] = bytes[i] }
            fwrite(buf, 1u, bytes.size.convert(), fileHandle)
        }
    }

    private fun writeInt32LE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeInt16LE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }
}
