package com.vectencia.koboe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AudioFileInfoTest {

    private fun sampleInfo() = AudioFileInfo(
        format = AudioFileFormat.WAV,
        sampleRate = 44100,
        channelCount = 2,
        durationMs = 180_000L,
        bitRate = 1_411_200,
        tags = AudioFileTags(title = "Test", artist = "Artist"),
    )

    @Test
    fun allProperties() {
        val info = sampleInfo()
        assertEquals(AudioFileFormat.WAV, info.format)
        assertEquals(44100, info.sampleRate)
        assertEquals(2, info.channelCount)
        assertEquals(180_000L, info.durationMs)
        assertEquals(1_411_200, info.bitRate)
        assertEquals("Test", info.tags.title)
        assertEquals("Artist", info.tags.artist)
    }

    @Test
    fun equality() {
        val a = sampleInfo()
        val b = sampleInfo()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun inequalityOnDifferentFormat() {
        val a = sampleInfo()
        val b = a.copy(format = AudioFileFormat.MP3)
        assertNotEquals(a, b)
    }

    @Test
    fun copy() {
        val original = sampleInfo()
        val copy = original.copy(sampleRate = 48000, channelCount = 1)
        assertEquals(48000, copy.sampleRate)
        assertEquals(1, copy.channelCount)
        assertEquals(original.format, copy.format)
        assertEquals(original.durationMs, copy.durationMs)
        assertEquals(original.bitRate, copy.bitRate)
        assertEquals(original.tags, copy.tags)
    }
}
