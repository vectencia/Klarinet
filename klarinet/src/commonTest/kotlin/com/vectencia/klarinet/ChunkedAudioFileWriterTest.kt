package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertEquals

class ChunkedAudioFileWriterTest {

    @Test
    fun generateChunkPathSequential() {
        val paths = (0 until 5).map { index ->
            ChunkedAudioFileWriter.generateChunkPath("/audio", "sleep", AudioFileFormat.WAV, index)
        }
        assertEquals("/audio/sleep_000.wav", paths[0])
        assertEquals("/audio/sleep_001.wav", paths[1])
        assertEquals("/audio/sleep_004.wav", paths[4])
    }

    @Test
    fun generateChunkPathAac() {
        val path = ChunkedAudioFileWriter.generateChunkPath("/audio", "rec", AudioFileFormat.AAC, 0)
        assertEquals("/audio/rec_000.m4a", path)
    }

    @Test
    fun generateChunkPathM4a() {
        val path = ChunkedAudioFileWriter.generateChunkPath("/audio", "rec", AudioFileFormat.M4A, 2)
        assertEquals("/audio/rec_002.m4a", path)
    }

    @Test
    fun maxFramesPerChunkCalculation() {
        val maxFrames = ChunkedAudioFileWriter.calculateMaxFramesPerChunk(
            maxChunkDurationMs = 30 * 60 * 1000L,
            sampleRate = 44100,
        )
        assertEquals(30L * 60 * 44100, maxFrames)
    }

    @Test
    fun maxFramesPerChunkShortDuration() {
        val maxFrames = ChunkedAudioFileWriter.calculateMaxFramesPerChunk(
            maxChunkDurationMs = 1000L,
            sampleRate = 44100,
        )
        assertEquals(44100L, maxFrames)
    }
}
