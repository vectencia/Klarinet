package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AudioStreamConfigValidationTest {

    @Test
    fun zeroBufferSizeMeansPlatformDefault() {
        val config = AudioStreamConfig(bufferCapacityInFrames = 0)
        assertEquals(0, config.bufferCapacityInFrames)
    }

    @Test
    fun stereoConfig() {
        val config = AudioStreamConfig(channelCount = 2)
        assertEquals(2, config.channelCount)
    }

    @Test
    fun allStreamDirectionValuesWorkInConfig() {
        for (direction in StreamDirection.entries) {
            val config = AudioStreamConfig(direction = direction)
            assertEquals(direction, config.direction)
        }
    }

    @Test
    fun toStringContainsUsefulInfo() {
        val config = AudioStreamConfig(
            sampleRate = 44100,
            channelCount = 2,
            audioFormat = AudioFormat.PCM_I16,
            bufferCapacityInFrames = 512,
        )
        val str = config.toString()
        assertTrue(str.contains("44100"), "toString should contain sampleRate")
        assertTrue(str.contains("2"), "toString should contain channelCount")
        assertTrue(str.contains("PCM_I16"), "toString should contain audioFormat")
        assertTrue(str.contains("512"), "toString should contain bufferCapacityInFrames")
    }

    @Test
    fun destructuringWorks() {
        val config = AudioStreamConfig(
            sampleRate = 96000,
            channelCount = 2,
            audioFormat = AudioFormat.PCM_I24,
            bufferCapacityInFrames = 1024,
            performanceMode = PerformanceMode.POWER_SAVING,
            sharingMode = SharingMode.EXCLUSIVE,
            direction = StreamDirection.INPUT,
        )
        val (
            sampleRate,
            channelCount,
            audioFormat,
            bufferCapacity,
            performanceMode,
            sharingMode,
            direction,
        ) = config

        assertEquals(96000, sampleRate)
        assertEquals(2, channelCount)
        assertEquals(AudioFormat.PCM_I24, audioFormat)
        assertEquals(1024, bufferCapacity)
        assertEquals(PerformanceMode.POWER_SAVING, performanceMode)
        assertEquals(SharingMode.EXCLUSIVE, sharingMode)
        assertEquals(StreamDirection.INPUT, direction)
    }

    @Test
    fun multipleConfigsCanCoexist() {
        val outputConfig = AudioStreamConfig(
            sampleRate = 48000,
            channelCount = 2,
            direction = StreamDirection.OUTPUT,
        )
        val inputConfig = AudioStreamConfig(
            sampleRate = 44100,
            channelCount = 1,
            direction = StreamDirection.INPUT,
        )
        assertNotEquals(outputConfig, inputConfig)
        assertEquals(StreamDirection.OUTPUT, outputConfig.direction)
        assertEquals(StreamDirection.INPUT, inputConfig.direction)
        assertEquals(48000, outputConfig.sampleRate)
        assertEquals(44100, inputConfig.sampleRate)
    }

    @Test
    fun configWithHighSampleRate() {
        val config = AudioStreamConfig(sampleRate = 192000)
        assertEquals(192000, config.sampleRate)
    }

    @Test
    fun configWithLargeBufferSize() {
        val config = AudioStreamConfig(bufferCapacityInFrames = 8192)
        assertEquals(8192, config.bufferCapacityInFrames)
    }

    @Test
    fun hashCodeConsistentWithEquals() {
        val a = AudioStreamConfig(sampleRate = 44100, channelCount = 2)
        val b = AudioStreamConfig(sampleRate = 44100, channelCount = 2)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun configsInCollections() {
        val configs = setOf(
            AudioStreamConfig(sampleRate = 44100),
            AudioStreamConfig(sampleRate = 48000),
            AudioStreamConfig(sampleRate = 44100), // duplicate
        )
        assertEquals(2, configs.size)
    }
}
