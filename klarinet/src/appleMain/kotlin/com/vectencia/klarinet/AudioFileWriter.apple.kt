@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.vectencia.klarinet

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value
import platform.AudioToolbox.ExtAudioFileCreateWithURL
import platform.AudioToolbox.ExtAudioFileDispose
import platform.AudioToolbox.ExtAudioFileRef
import platform.AudioToolbox.ExtAudioFileRefVar
import platform.AudioToolbox.ExtAudioFileSetProperty
import platform.AudioToolbox.ExtAudioFileWrite
import platform.AudioToolbox.kAudioFileFlags_EraseFile
import platform.AudioToolbox.kAudioFileM4AType
import platform.AudioToolbox.kAudioFileCAFType
import platform.AudioToolbox.kExtAudioFileProperty_ClientDataFormat
import platform.CoreAudioTypes.AudioBufferList
import platform.CoreAudioTypes.AudioStreamBasicDescription
import platform.CoreAudioTypes.kAudioFormatFlagIsFloat
import platform.CoreAudioTypes.kAudioFormatFlagIsPacked
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSURL
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
    private val delegate: AppleAudioFileWriterDelegate

    init {
        delegate = when (format) {
            AudioFileFormat.WAV -> WavFileWriterDelegate(filePath, sampleRate, channelCount, tags)
            AudioFileFormat.AAC, AudioFileFormat.M4A ->
                AacFileWriterDelegate(filePath, format, sampleRate, channelCount)
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
private interface AppleAudioFileWriterDelegate {
    fun writeFrames(data: FloatArray, numFrames: Int)
    fun close()
}

// ---------------------------------------------------------------------------
// WAV writer -- manual RIFF header + raw PCM (same approach as Android)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalForeignApi::class)
private class WavFileWriterDelegate(
    private val filePath: String,
    private val sampleRate: Int,
    private val channelCount: Int,
    private val tags: AudioFileTags?,
) : AppleAudioFileWriterDelegate {

    private val fileHandle = fopen(filePath, "wb")
        ?: throw AudioFileException("Failed to create WAV file: $filePath")
    private var dataByteCount = 0L
    private val bitsPerSample = 16

    init {
        writeWavHeader()
    }

    /**
     * Writes a placeholder WAV header (44 bytes). Sizes are patched in [close].
     */
    private fun writeWavHeader() {
        val byteRate = sampleRate * channelCount * (bitsPerSample / 8)
        val blockAlign = channelCount * (bitsPerSample / 8)

        val header = ByteArray(44)
        var offset = 0

        // RIFF header
        "RIFF".encodeToByteArray().copyInto(header, offset); offset += 4
        // placeholder RIFF chunk size (patched in close)
        writeInt32LE(header, offset, 0); offset += 4
        "WAVE".encodeToByteArray().copyInto(header, offset); offset += 4

        // fmt sub-chunk
        "fmt ".encodeToByteArray().copyInto(header, offset); offset += 4
        writeInt32LE(header, offset, 16); offset += 4 // SubchunkSize for PCM
        writeInt16LE(header, offset, 1); offset += 2 // AudioFormat = PCM
        writeInt16LE(header, offset, channelCount); offset += 2
        writeInt32LE(header, offset, sampleRate); offset += 4
        writeInt32LE(header, offset, byteRate); offset += 4
        writeInt16LE(header, offset, blockAlign); offset += 2
        writeInt16LE(header, offset, bitsPerSample); offset += 2

        // data sub-chunk header
        "data".encodeToByteArray().copyInto(header, offset); offset += 4
        writeInt32LE(header, offset, 0); offset += 4 // placeholder data chunk size

        writeBytes(header)
    }

    override fun writeFrames(data: FloatArray, numFrames: Int) {
        val totalSamples = numFrames * channelCount
        val bytesPerSample = bitsPerSample / 8
        val totalBytes = totalSamples * bytesPerSample
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

    override fun close() {
        // Patch RIFF chunk size (offset 4) and data chunk size (offset 40)
        val riffChunkSize = (4 + 24 + 8 + dataByteCount).toInt()

        val patch = ByteArray(4)

        // Patch RIFF chunk size
        fseek(fileHandle, 4, SEEK_SET)
        writeInt32LE(patch, 0, riffChunkSize)
        writeBytes(patch)

        // Patch data chunk size
        fseek(fileHandle, 40, SEEK_SET)
        writeInt32LE(patch, 0, dataByteCount.toInt())
        writeBytes(patch)

        fclose(fileHandle)
    }

    private fun writeBytes(bytes: ByteArray) {
        memScoped {
            val buf = allocArray<ByteVar>(bytes.size)
            for (i in bytes.indices) {
                buf[i] = bytes[i]
            }
            fwrite(buf, 1u, bytes.size.toULong(), fileHandle)
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

// ---------------------------------------------------------------------------
// AAC / M4A writer -- uses ExtAudioFile API
// ---------------------------------------------------------------------------

@OptIn(ExperimentalForeignApi::class)
private class AacFileWriterDelegate(
    filePath: String,
    format: AudioFileFormat,
    private val sampleRate: Int,
    private val channelCount: Int,
) : AppleAudioFileWriterDelegate {

    private var extAudioFile: ExtAudioFileRef? = null

    init {
        val fileUrl = NSURL.fileURLWithPath(filePath)

        memScoped {
            // Output format: AAC
            val outputFormat = alloc<AudioStreamBasicDescription>()
            outputFormat.mSampleRate = sampleRate.toDouble()
            outputFormat.mFormatID = kAudioFormatMPEG4AAC
            outputFormat.mChannelsPerFrame = channelCount.toUInt()
            // Let the encoder fill in the rest
            outputFormat.mBytesPerPacket = 0u
            outputFormat.mBytesPerFrame = 0u
            outputFormat.mFramesPerPacket = 1024u
            outputFormat.mBitsPerChannel = 0u
            outputFormat.mFormatFlags = 0u

            val fileType = when (format) {
                AudioFileFormat.M4A -> kAudioFileM4AType
                AudioFileFormat.AAC -> kAudioFileCAFType
                else -> kAudioFileM4AType
            }

            val extAudioFileRef = alloc<ExtAudioFileRefVar>()

            @Suppress("CAST_NEVER_SUCCEEDS")
            val createStatus = ExtAudioFileCreateWithURL(
                fileUrl as platform.CoreFoundation.CFURLRef,
                fileType,
                outputFormat.ptr,
                null, // channel layout
                kAudioFileFlags_EraseFile,
                extAudioFileRef.ptr
            )

            if (createStatus != 0) {
                throw AudioFileException(
                    "Failed to create audio file: $filePath (OSStatus: $createStatus)"
                )
            }

            extAudioFile = extAudioFileRef.value

            // Set client format to Float32 interleaved PCM
            val clientFormat = alloc<AudioStreamBasicDescription>()
            clientFormat.mSampleRate = sampleRate.toDouble()
            clientFormat.mFormatID = kAudioFormatLinearPCM
            clientFormat.mFormatFlags =
                kAudioFormatFlagIsFloat or kAudioFormatFlagIsPacked
            clientFormat.mBitsPerChannel = 32u
            clientFormat.mChannelsPerFrame = channelCount.toUInt()
            clientFormat.mBytesPerFrame = (4 * channelCount).toUInt()
            clientFormat.mFramesPerPacket = 1u
            clientFormat.mBytesPerPacket = (4 * channelCount).toUInt()

            val setFormatStatus = ExtAudioFileSetProperty(
                extAudioFile,
                kExtAudioFileProperty_ClientDataFormat,
                sizeOf<AudioStreamBasicDescription>().toUInt(),
                clientFormat.ptr
            )

            if (setFormatStatus != 0) {
                ExtAudioFileDispose(extAudioFile)
                extAudioFile = null
                throw AudioFileException(
                    "Failed to set client data format for writing (OSStatus: $setFormatStatus)"
                )
            }
        }
    }

    override fun writeFrames(data: FloatArray, numFrames: Int) {
        val audioFile = extAudioFile
            ?: throw AudioFileException("Audio file is not open for writing")

        val totalSamples = numFrames * channelCount

        memScoped {
            val buffer = allocArray<FloatVar>(totalSamples)
            for (i in 0 until totalSamples) {
                buffer[i] = data[i]
            }

            val audioBufferList = alloc<AudioBufferList>()
            audioBufferList.mNumberBuffers = 1u
            audioBufferList.mBuffers.pointed.mNumberChannels = channelCount.toUInt()
            audioBufferList.mBuffers.pointed.mDataByteSize =
                (totalSamples * sizeOf<FloatVar>()).toUInt()
            audioBufferList.mBuffers.pointed.mData = buffer

            val status = ExtAudioFileWrite(
                audioFile,
                numFrames.toUInt(),
                audioBufferList.ptr
            )

            if (status != 0) {
                throw AudioFileException(
                    "Failed to write audio frames (OSStatus: $status)"
                )
            }
        }
    }

    override fun close() {
        extAudioFile?.let { ref ->
            ExtAudioFileDispose(ref)
        }
        extAudioFile = null
    }
}
