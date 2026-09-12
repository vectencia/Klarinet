package com.vectencia.klarinet

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsAudioFileTest {

    @Test
    fun wavWriterRoundTrip() {
        val path = "memory:roundtrip.wav"
        val samples = floatArrayOf(0f, 0.5f, -0.5f, 1f, -1f, 0.25f)
        AudioFileWriter(
            filePath = path,
            format = AudioFileFormat.WAV,
            sampleRate = 44100,
            channelCount = 2,
            tags = AudioFileTags(title = "Test"),
        ).also { writer ->
            writer.writeFrames(samples, 3)
            writer.close()
        }

        val reader = AudioFileReader(path)
        assertEquals(44100, reader.info.sampleRate)
        assertEquals(2, reader.info.channelCount)
        assertEquals(AudioFileFormat.WAV, reader.info.format)
        assertEquals("Test", reader.info.tags.title)
        assertEquals(3L * 1000 / 44100, reader.info.durationMs)
        val decoded = reader.readAll()
        assertEquals(samples.size, decoded.size)
        samples.forEachIndexed { index, value ->
            assertEquals(value, decoded[index], 0.0001f)
        }
        assertTrue(reader.isAtEnd)
        reader.close()
    }

    @Test
    fun wavBytesRoundTrip() {
        val path = "memory:bytes.wav"
        val samples = floatArrayOf(0.25f, -0.25f, 0.5f, -0.5f)
        putAudioFile(path, samples, 48000, 1)
        val bytes = audioFileWavBytes(path)
        putWavBytes("memory:imported.wav", bytes)
        val reader = AudioFileReader("memory:imported.wav")
        val decoded = reader.readAll()
        assertEquals(samples.size, decoded.size)
        samples.forEachIndexed { index, value ->
            assertTrue(abs(value - decoded[index]) < 0.0001f)
        }
        reader.close()
    }

    @Test
    fun readerSeekAndChunks() {
        val path = "memory:seek.wav"
        putAudioFile(path, floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f), 8000, 2)
        val reader = AudioFileReader(path)
        val first = reader.readFrames(1)
        assertEquals(2, first.size)
        assertEquals(0f, first[0])
        assertEquals(1f, first[1])
        reader.seekTo(2)
        val last = reader.readFrames(8)
        assertEquals(2, last.size)
        assertEquals(4f, last[0])
        assertEquals(5f, last[1])
        assertTrue(reader.isAtEnd)
        reader.seekTo(0)
        assertFalse(reader.isAtEnd)
        reader.close()
    }

    @Test
    fun missingFileThrows() {
        val error = assertFailsWith<AudioFileException> {
            AudioFileReader("memory:missing.wav")
        }
        assertTrue(error.message!!.contains("decodeAudioFile"))
    }

    @Test
    fun compressedWriteUnsupported() {
        assertFailsWith<UnsupportedFormatException> {
            AudioFileWriter("memory:out.mp3", AudioFileFormat.MP3, 44100, 1)
        }
    }

    @Test
    fun formatFromPathDetectsExtensions() {
        assertEquals(AudioFileFormat.WAV, formatFromPath("a.wav"))
        assertEquals(AudioFileFormat.MP3, formatFromPath("a.MP3"))
        assertEquals(AudioFileFormat.AAC, formatFromPath("clip.aac"))
        assertEquals(AudioFileFormat.M4A, formatFromPath("song.m4a"))
    }
}
