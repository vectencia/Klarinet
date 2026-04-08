package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AudioStreamConfigTest {

    @Test
    fun defaultValues() {
        val config = AudioStreamConfig()
        assertEquals(48000, config.sampleRate)
        assertEquals(1, config.channelCount)
        assertEquals(AudioFormat.PCM_FLOAT, config.audioFormat)
        assertEquals(0, config.bufferCapacityInFrames)
        assertEquals(PerformanceMode.LOW_LATENCY, config.performanceMode)
        assertEquals(SharingMode.SHARED, config.sharingMode)
        assertEquals(StreamDirection.OUTPUT, config.direction)
    }

    @Test
    fun customValues() {
        val config = AudioStreamConfig(
            sampleRate = 44100,
            channelCount = 2,
            audioFormat = AudioFormat.PCM_I16,
            bufferCapacityInFrames = 512,
            performanceMode = PerformanceMode.POWER_SAVING,
            sharingMode = SharingMode.EXCLUSIVE,
            direction = StreamDirection.INPUT,
        )
        assertEquals(44100, config.sampleRate)
        assertEquals(2, config.channelCount)
        assertEquals(AudioFormat.PCM_I16, config.audioFormat)
        assertEquals(512, config.bufferCapacityInFrames)
        assertEquals(PerformanceMode.POWER_SAVING, config.performanceMode)
        assertEquals(SharingMode.EXCLUSIVE, config.sharingMode)
        assertEquals(StreamDirection.INPUT, config.direction)
    }

    @Test
    fun allAudioFormats() {
        for (format in AudioFormat.entries) {
            val config = AudioStreamConfig(audioFormat = format)
            assertEquals(format, config.audioFormat)
        }
    }

    @Test
    fun allPerformanceModes() {
        for (mode in PerformanceMode.entries) {
            val config = AudioStreamConfig(performanceMode = mode)
            assertEquals(mode, config.performanceMode)
        }
    }

    @Test
    fun allSharingModes() {
        for (mode in SharingMode.entries) {
            val config = AudioStreamConfig(sharingMode = mode)
            assertEquals(mode, config.sharingMode)
        }
    }

    @Test
    fun allDirections() {
        for (dir in StreamDirection.entries) {
            val config = AudioStreamConfig(direction = dir)
            assertEquals(dir, config.direction)
        }
    }

    @Test
    fun copyPreservesFields() {
        val original = AudioStreamConfig(
            sampleRate = 44100,
            channelCount = 2,
            audioFormat = AudioFormat.PCM_I24,
            bufferCapacityInFrames = 256,
            performanceMode = PerformanceMode.NONE,
            sharingMode = SharingMode.EXCLUSIVE,
            direction = StreamDirection.INPUT,
        )
        val copy = original.copy(sampleRate = 96000)
        assertEquals(96000, copy.sampleRate)
        assertEquals(original.channelCount, copy.channelCount)
        assertEquals(original.audioFormat, copy.audioFormat)
        assertEquals(original.bufferCapacityInFrames, copy.bufferCapacityInFrames)
        assertEquals(original.performanceMode, copy.performanceMode)
        assertEquals(original.sharingMode, copy.sharingMode)
        assertEquals(original.direction, copy.direction)
    }

    @Test
    fun equality() {
        val a = AudioStreamConfig()
        val b = AudioStreamConfig()
        val c = AudioStreamConfig(sampleRate = 44100)
        assertEquals(a, b)
        assertNotEquals(a, c)
    }
}
