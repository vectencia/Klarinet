@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
)

package com.vectencia.koboe

import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.AudioToolbox.ExtAudioFileDispose
import platform.AudioToolbox.ExtAudioFileGetProperty
import platform.AudioToolbox.ExtAudioFileOpenURL
import platform.AudioToolbox.ExtAudioFileRead
import platform.AudioToolbox.ExtAudioFileRef
import platform.AudioToolbox.ExtAudioFileRefVar
import platform.AudioToolbox.ExtAudioFileSeek
import platform.AudioToolbox.ExtAudioFileSetProperty
import platform.AudioToolbox.kExtAudioFileProperty_ClientDataFormat
import platform.AudioToolbox.kExtAudioFileProperty_FileDataFormat
import platform.AudioToolbox.kExtAudioFileProperty_FileLengthFrames
import platform.AVFoundation.*
import platform.CoreAudioTypes.AudioBufferList
import platform.CoreAudioTypes.AudioStreamBasicDescription
import platform.CoreAudioTypes.kAudioFormatFlagIsFloat
import platform.CoreAudioTypes.kAudioFormatFlagIsPacked
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.posix.memcpy

actual class AudioFileReader actual constructor(filePath: String) {

    private var extAudioFile: ExtAudioFileRef? = null
    private val _info: AudioFileInfo
    private val channelCount: Int
    private val sampleRate: Int
    private var _isAtEnd = false
    private var totalFrames: Long = 0L

    actual val info: AudioFileInfo get() = _info
    actual val isAtEnd: Boolean get() = _isAtEnd

    private val isUrl = filePath.startsWith("http://") || filePath.startsWith("https://")

    init {
        val fileUrl = if (isUrl) {
            NSURL.URLWithString(filePath)
                ?: throw AudioFileException("Invalid URL: $filePath")
        } else {
            if (!NSFileManager.defaultManager.fileExistsAtPath(filePath)) {
                throw AudioFileException("File not found: $filePath")
            }
            NSURL.fileURLWithPath(filePath)
        }

        // Detect format from extension (strip query params for URLs)
        val extension = filePath.substringBeforeLast('?').substringAfterLast('.', "").lowercase()
        val format = when (extension) {
            "wav" -> AudioFileFormat.WAV
            "mp3" -> AudioFileFormat.MP3
            "aac" -> AudioFileFormat.AAC
            "m4a" -> AudioFileFormat.M4A
            else -> throw AudioFileException("Unsupported file extension: $extension")
        }

        memScoped {
            // Open ExtAudioFile
            val extAudioFileRef = alloc<ExtAudioFileRefVar>()
            @Suppress("CAST_NEVER_SUCCEEDS")
            val openStatus = ExtAudioFileOpenURL(
                fileUrl as platform.CoreFoundation.CFURLRef,
                extAudioFileRef.ptr
            )
            if (openStatus != 0) {
                throw AudioFileException(
                    "Failed to open audio file: $filePath (OSStatus: $openStatus)"
                )
            }
            extAudioFile = extAudioFileRef.value

            // Get total frame count
            val frameLengthSize = alloc<UIntVar>()
            frameLengthSize.value = sizeOf<LongVar>().toUInt()
            val frameLengthVal = alloc<LongVar>()
            val frameLengthStatus = ExtAudioFileGetProperty(
                extAudioFile,
                kExtAudioFileProperty_FileLengthFrames,
                frameLengthSize.ptr,
                frameLengthVal.ptr
            )
            totalFrames = if (frameLengthStatus == 0) frameLengthVal.value else 0L

            // Get file's native format to extract sample rate and channel count
            val fileFormatSize = alloc<UIntVar>()
            fileFormatSize.value = sizeOf<AudioStreamBasicDescription>().toUInt()
            val fileFormat = alloc<AudioStreamBasicDescription>()
            ExtAudioFileGetProperty(
                extAudioFile,
                kExtAudioFileProperty_FileDataFormat,
                fileFormatSize.ptr,
                fileFormat.ptr
            )

            sampleRate = fileFormat.mSampleRate.toInt()
            channelCount = fileFormat.mChannelsPerFrame.toInt()

            // Set client format to Float32 interleaved PCM
            val clientFormat = alloc<AudioStreamBasicDescription>()
            clientFormat.mSampleRate = fileFormat.mSampleRate
            clientFormat.mFormatID = kAudioFormatLinearPCM
            clientFormat.mFormatFlags =
                kAudioFormatFlagIsFloat or kAudioFormatFlagIsPacked
            clientFormat.mBitsPerChannel = 32u
            clientFormat.mChannelsPerFrame = fileFormat.mChannelsPerFrame
            clientFormat.mBytesPerFrame =
                (4u * fileFormat.mChannelsPerFrame)
            clientFormat.mFramesPerPacket = 1u
            clientFormat.mBytesPerPacket = clientFormat.mBytesPerFrame

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
                    "Failed to set client data format (OSStatus: $setFormatStatus)"
                )
            }
        }

        // Calculate duration
        val durationMs = if (sampleRate > 0 && totalFrames > 0) {
            totalFrames * 1000L / sampleRate
        } else {
            // Fall back to AVURLAsset for duration
            val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(filePath), options = null)
            (CMTimeGetSeconds(asset.duration) * 1000.0).toLong()
        }

        // Estimate bit rate from file size and duration
        val bitRate = if (durationMs > 0) {
            val fileSize = NSFileManager.defaultManager.attributesOfItemAtPath(
                filePath, error = null
            )?.get("NSFileSize") as? NSNumber
            val bytes = fileSize?.longValue ?: 0L
            ((bytes * 8 * 1000) / durationMs).toInt()
        } else {
            0
        }

        // Read tags using AVURLAsset metadata
        val tags = readTags(filePath)

        _info = AudioFileInfo(
            format = format,
            sampleRate = sampleRate,
            channelCount = channelCount,
            durationMs = durationMs,
            bitRate = bitRate,
            tags = tags,
        )
    }

    private fun readTags(filePath: String): AudioFileTags {
        val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(filePath), options = null)
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var albumArt: ByteArray? = null
        var genre: String? = null
        var year: Int? = null
        var trackNumber: Int? = null

        val metadataItems = asset.metadata
        for (item in metadataItems) {
            val metadataItem = item as? AVMetadataItem ?: continue
            val commonKey = metadataItem.commonKey

            when (commonKey) {
                AVMetadataCommonKeyTitle -> {
                    title = metadataItem.stringValue
                }
                AVMetadataCommonKeyArtist -> {
                    artist = metadataItem.stringValue
                }
                AVMetadataCommonKeyAlbumName -> {
                    album = metadataItem.stringValue
                }
                AVMetadataCommonKeyArtwork -> {
                    val data = metadataItem.dataValue
                    if (data != null) {
                        albumArt = data.toByteArray()
                    }
                }
            }

            // Check for genre, year, track number in key value
            val key = metadataItem.key?.toString() ?: continue
            if (key.contains("genre", ignoreCase = true) ||
                key.contains("TCON", ignoreCase = true)
            ) {
                genre = metadataItem.stringValue
            }
            if (key.contains("year", ignoreCase = true) ||
                key.contains("date", ignoreCase = true) ||
                key.contains("TDRC", ignoreCase = true)
            ) {
                val dateStr = metadataItem.stringValue
                year = dateStr?.take(4)?.toIntOrNull()
            }
            if (key.contains("track", ignoreCase = true) ||
                key.contains("TRCK", ignoreCase = true)
            ) {
                val trackStr = metadataItem.stringValue
                trackNumber = trackStr?.split("/")?.firstOrNull()?.trim()?.toIntOrNull()
            }
        }

        return AudioFileTags(
            title = title,
            artist = artist,
            album = album,
            trackNumber = trackNumber,
            genre = genre,
            year = year,
            albumArt = albumArt,
        )
    }

    actual fun readAll(): FloatArray {
        val allSamples = mutableListOf<FloatArray>()
        while (!_isAtEnd) {
            val frames = readFrames(4096)
            if (frames.isEmpty()) break
            allSamples.add(frames)
        }
        if (allSamples.isEmpty()) return FloatArray(0)

        val totalSize = allSamples.sumOf { it.size }
        val result = FloatArray(totalSize)
        var offset = 0
        for (chunk in allSamples) {
            chunk.copyInto(result, offset)
            offset += chunk.size
        }
        return result
    }

    actual fun readFrames(maxFrames: Int): FloatArray {
        if (_isAtEnd) return FloatArray(0)
        val audioFile = extAudioFile ?: return FloatArray(0)

        val totalSamples = maxFrames * channelCount
        val result: FloatArray

        memScoped {
            val buffer = allocArray<FloatVar>(totalSamples)

            val audioBuffer = alloc<AudioBufferList>()
            audioBuffer.mNumberBuffers = 1u
            audioBuffer.mBuffers.pointed.mNumberChannels = channelCount.toUInt()
            audioBuffer.mBuffers.pointed.mDataByteSize =
                (totalSamples * sizeOf<FloatVar>()).toUInt()
            audioBuffer.mBuffers.pointed.mData = buffer

            val framesRead = alloc<UIntVar>()
            framesRead.value = maxFrames.toUInt()

            val status = ExtAudioFileRead(audioFile, framesRead.ptr, audioBuffer.ptr)

            if (status != 0) {
                throw AudioFileException("Failed to read audio frames (OSStatus: $status)")
            }

            val actualFramesRead = framesRead.value.toInt()
            if (actualFramesRead == 0) {
                _isAtEnd = true
                return FloatArray(0)
            }

            val actualSamples = actualFramesRead * channelCount
            result = FloatArray(actualSamples)
            for (i in 0 until actualSamples) {
                result[i] = buffer[i]
            }
        }

        return result
    }

    actual fun seekTo(framePosition: Long) {
        val audioFile = extAudioFile
            ?: throw AudioFileException("Audio file is not open")
        val status = ExtAudioFileSeek(audioFile, framePosition)
        if (status != 0) {
            throw AudioFileException(
                "Failed to seek to frame $framePosition (OSStatus: $status)"
            )
        }
        _isAtEnd = false
    }

    actual fun close() {
        extAudioFile?.let { ref ->
            ExtAudioFileDispose(ref)
        }
        extAudioFile = null
    }
}

/**
 * Extension to convert NSData to ByteArray.
 */
private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    if (length == 0) return ByteArray(0)
    val result = ByteArray(length)
    result.usePinned { pinned ->
        memcpy(pinned.addressOf(0), this@toByteArray.bytes, this@toByteArray.length)
    }
    return result
}
