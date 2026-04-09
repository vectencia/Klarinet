package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class DataClassTest {

    @Test
    fun audioDeviceInfoCreation() {
        val device = AudioDeviceInfo(
            id = 1,
            name = "Built-in Speaker",
            isInput = false,
            isOutput = true,
            sampleRates = listOf(44100, 48000),
            channelCounts = listOf(1, 2),
        )
        assertEquals(1, device.id)
        assertEquals("Built-in Speaker", device.name)
        assertFalse(device.isInput)
        assertTrue(device.isOutput)
        assertEquals(listOf(44100, 48000), device.sampleRates)
        assertEquals(listOf(1, 2), device.channelCounts)
    }

    @Test
    fun audioDeviceInfoCopy() {
        val original = AudioDeviceInfo(
            id = 1,
            name = "Mic",
            isInput = true,
            isOutput = false,
            sampleRates = listOf(48000),
            channelCounts = listOf(1),
        )
        val copy = original.copy(name = "External Mic")
        assertEquals("External Mic", copy.name)
        assertEquals(original.id, copy.id)
    }

    @Test
    fun audioDeviceInfoEquality() {
        val a = AudioDeviceInfo(1, "Mic", true, false, listOf(48000), listOf(1))
        val b = AudioDeviceInfo(1, "Mic", true, false, listOf(48000), listOf(1))
        val c = AudioDeviceInfo(2, "Mic", true, false, listOf(48000), listOf(1))
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun latencyInfoCreation() {
        val info = LatencyInfo(inputLatencyMs = 5.0, outputLatencyMs = 10.0)
        assertEquals(5.0, info.inputLatencyMs)
        assertEquals(10.0, info.outputLatencyMs)
    }

    @Test
    fun latencyInfoEquality() {
        val a = LatencyInfo(5.0, 10.0)
        val b = LatencyInfo(5.0, 10.0)
        assertEquals(a, b)
    }

    @Test
    fun audioRouteChangeInfoCreation() {
        val info = AudioRouteChangeInfo(reason = "NewDeviceAvailable", previousRoute = "Speaker")
        assertEquals("NewDeviceAvailable", info.reason)
        assertEquals("Speaker", info.previousRoute)
    }

    @Test
    fun audioRouteChangeInfoEquality() {
        val a = AudioRouteChangeInfo("reason", "route")
        val b = AudioRouteChangeInfo("reason", "route")
        assertEquals(a, b)
    }
}
