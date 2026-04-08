package com.vectencia.klarinet

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

actual class AudioFileReader actual constructor(filePath: String) {

    private val extractor = MediaExtractor()
    private val codec: MediaCodec
    private val _info: AudioFileInfo
    private val channelCount: Int
    private var _isAtEnd = false
    private var codecStarted = false

    actual val info: AudioFileInfo get() = _info
    actual val isAtEnd: Boolean get() = _isAtEnd

    private val isUrl = filePath.startsWith("http://") || filePath.startsWith("https://")

    init {
        if (!isUrl) {
            val file = File(filePath)
            if (!file.exists()) {
                throw AudioFileException("File not found: $filePath")
            }
        }

        // Detect format from extension (works for both paths and URLs)
        val extension = filePath.substringBeforeLast('?').substringAfterLast('.').lowercase()
        val format = when (extension) {
            "wav" -> AudioFileFormat.WAV
            "mp3" -> AudioFileFormat.MP3
            "aac" -> AudioFileFormat.AAC
            "m4a" -> AudioFileFormat.M4A
            else -> throw AudioFileException("Unsupported file extension: $extension")
        }

        // Use MediaMetadataRetriever for metadata
        val retriever = MediaMetadataRetriever()
        try {
            if (isUrl) {
                retriever.setDataSource(filePath, HashMap<String, String>())
            } else {
                retriever.setDataSource(filePath)
            }

            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L

            val bitRate = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_BITRATE
            )?.toIntOrNull() ?: 0

            // Parse tags
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            val yearStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
            val year = yearStr?.toIntOrNull()
            val trackStr = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER
            )
            // Track number may be in "3/12" format — parse first number
            val trackNumber = trackStr?.split("/")?.firstOrNull()?.trim()?.toIntOrNull()
            val albumArt = retriever.embeddedPicture

            val tags = AudioFileTags(
                title = title,
                artist = artist,
                album = album,
                trackNumber = trackNumber,
                genre = genre,
                year = year,
                albumArt = albumArt,
            )

            // Set up MediaExtractor
            if (isUrl) {
                extractor.setDataSource(filePath, HashMap<String, String>())
            } else {
                extractor.setDataSource(filePath)
            }

            // Find the audio track
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = trackFormat
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                throw AudioFileException("No audio track found in file: $filePath")
            }

            extractor.selectTrack(audioTrackIndex)

            // Get channel count and sample rate from the audio track format
            channelCount = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                1
            }

            val sampleRate: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_SAMPLERATE
                )?.toIntOrNull()
                    ?: if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    } else {
                        44100
                    }
            } else {
                if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                } else {
                    44100
                }
            }

            _info = AudioFileInfo(
                format = format,
                sampleRate = sampleRate,
                channelCount = channelCount,
                durationMs = durationMs,
                bitRate = bitRate,
                tags = tags,
            )

            // Create and configure MediaCodec decoder
            val mime = audioFormat.getString(MediaFormat.KEY_MIME)
                ?: throw AudioFileException("No MIME type for audio track")
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()
            codecStarted = true
        } catch (e: AudioFileException) {
            throw e
        } catch (e: Exception) {
            throw AudioFileException("Failed to open audio file: $filePath", e)
        } finally {
            retriever.release()
        }
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

        val maxSamples = maxFrames * channelCount
        val outputSamples = mutableListOf<Float>()
        val bufferInfo = MediaCodec.BufferInfo()
        val timeoutUs = 10_000L
        var inputEos = false

        while (outputSamples.size < maxSamples) {
            // Feed input
            if (!inputEos) {
                val inputIndex = codec.dequeueInputBuffer(timeoutUs)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            // End of stream
                            codec.queueInputBuffer(
                                inputIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputEos = true
                        } else {
                            val presentationTimeUs = extractor.sampleTime
                            codec.queueInputBuffer(
                                inputIndex, 0, sampleSize, presentationTimeUs, 0
                            )
                            extractor.advance()
                        }
                    }
                }
            }

            // Drain output
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                outputIndex >= 0 -> {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        codec.releaseOutputBuffer(outputIndex, false)
                        _isAtEnd = true
                        break
                    }

                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

                        val shortBuffer = outputBuffer.asShortBuffer()
                        val samplesAvailable = shortBuffer.remaining()
                        val samplesToRead = minOf(
                            samplesAvailable,
                            maxSamples - outputSamples.size
                        )

                        for (i in 0 until samplesToRead) {
                            outputSamples.add(shortBuffer.get().toFloat() / Short.MAX_VALUE)
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // Format changed, continue decoding
                }
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (inputEos) {
                        // No more input and no output available — we're done
                        _isAtEnd = true
                        break
                    }
                }
            }
        }

        return outputSamples.toFloatArray()
    }

    actual fun seekTo(framePosition: Long) {
        val sampleRate = _info.sampleRate
        val timeUs = if (sampleRate > 0) {
            framePosition * 1_000_000L / sampleRate
        } else {
            0L
        }
        extractor.seekTo(timeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        codec.flush()
        _isAtEnd = false
    }

    actual fun close() {
        try {
            if (codecStarted) {
                codec.stop()
            }
            codec.release()
        } catch (_: Exception) {
            // Ignore errors during cleanup
        }
        try {
            extractor.release()
        } catch (_: Exception) {
            // Ignore errors during cleanup
        }
    }
}
