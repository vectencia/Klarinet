package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AudioDeviceInfoTest {

    @Test
    fun createInputDevice() {
        val device = AudioDeviceInfo(
            id = 1,
            name = "Built-in Microphone",
            isInput = true,
            isOutput = false,
            sampleRates = listOf(44100, 48000),
            channelCounts = listOf(1),
        )
        assertEquals(1, device.id)
        assertEquals("Built-in Microphone", device.name)
        assertTrue(device.isInput)
        assertFalse(device.isOutput)
        assertEquals(listOf(44100, 48000), device.sampleRates)
        assertEquals(listOf(1), device.channelCounts)
    }

    @Test
    fun createOutputDevice() {
        val device = AudioDeviceInfo(
            id = 2,
            name = "Built-in Speaker",
            isInput = false,
            isOutput = true,
            sampleRates = listOf(48000),
            channelCounts = listOf(2),
        )
        assertEquals(2, device.id)
        assertEquals("Built-in Speaker", device.name)
        assertFalse(device.isInput)
        assertTrue(device.isOutput)
        assertEquals(listOf(48000), device.sampleRates)
        assertEquals(listOf(2), device.channelCounts)
    }

    @Test
    fun deviceWithMultipleSampleRates() {
        val device = AudioDeviceInfo(
            id = 3,
            name = "USB Audio Interface",
            isInput = true,
            isOutput = true,
            sampleRates = listOf(22050, 44100, 48000, 88200, 96000, 192000),
            channelCounts = listOf(2),
        )
        assertEquals(6, device.sampleRates.size)
        assertTrue(device.sampleRates.contains(22050))
        assertTrue(device.sampleRates.contains(192000))
    }

    @Test
    fun deviceWithMultipleChannelCounts() {
        val device = AudioDeviceInfo(
            id = 4,
            name = "Multichannel Interface",
            isInput = false,
            isOutput = true,
            sampleRates = listOf(48000),
            channelCounts = listOf(1, 2, 4, 6, 8),
        )
        assertEquals(5, device.channelCounts.size)
        assertTrue(device.channelCounts.contains(1))
        assertTrue(device.channelCounts.contains(8))
    }

    @Test
    fun emptyListsAreValid() {
        val device = AudioDeviceInfo(
            id = 5,
            name = "Virtual Device",
            isInput = false,
            isOutput = false,
            sampleRates = emptyList(),
            channelCounts = emptyList(),
        )
        assertTrue(device.sampleRates.isEmpty())
        assertTrue(device.channelCounts.isEmpty())
    }

    @Test
    fun equalityByAllFields() {
        val a = AudioDeviceInfo(1, "Mic", true, false, listOf(48000), listOf(1))
        val b = AudioDeviceInfo(1, "Mic", true, false, listOf(48000), listOf(1))
        val c = AudioDeviceInfo(1, "Mic", true, false, listOf(44100), listOf(1))
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun hashCodeConsistentWithEquals() {
        val a = AudioDeviceInfo(1, "Mic", true, false, listOf(48000), listOf(1))
        val b = AudioDeviceInfo(1, "Mic", true, false, listOf(48000), listOf(1))
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun copyPreservesOtherFields() {
        val original = AudioDeviceInfo(
            id = 10,
            name = "Original",
            isInput = true,
            isOutput = false,
            sampleRates = listOf(48000),
            channelCounts = listOf(1, 2),
        )
        val copy = original.copy(name = "Renamed")
        assertEquals("Renamed", copy.name)
        assertEquals(original.id, copy.id)
        assertEquals(original.isInput, copy.isInput)
        assertEquals(original.sampleRates, copy.sampleRates)
        assertEquals(original.channelCounts, copy.channelCounts)
    }

    @Test
    fun toStringContainsDeviceName() {
        val device = AudioDeviceInfo(1, "My Speaker", false, true, listOf(48000), listOf(2))
        val str = device.toString()
        assertTrue(str.contains("My Speaker"))
    }

    @Test
    fun destructuringWorks() {
        val device = AudioDeviceInfo(
            id = 7,
            name = "Test",
            isInput = true,
            isOutput = true,
            sampleRates = listOf(44100),
            channelCounts = listOf(2),
        )
        val (id, name, isInput, isOutput, sampleRates, channelCounts) = device
        assertEquals(7, id)
        assertEquals("Test", name)
        assertTrue(isInput)
        assertTrue(isOutput)
        assertEquals(listOf(44100), sampleRates)
        assertEquals(listOf(2), channelCounts)
    }
}
