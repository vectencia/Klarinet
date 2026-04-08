package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnumTest {

    @Test
    fun audioFormatEntriesExist() {
        val entries = AudioFormat.entries
        assertEquals(4, entries.size)
        assertTrue(entries.contains(AudioFormat.PCM_FLOAT))
        assertTrue(entries.contains(AudioFormat.PCM_I16))
        assertTrue(entries.contains(AudioFormat.PCM_I24))
        assertTrue(entries.contains(AudioFormat.PCM_I32))
    }

    @Test
    fun performanceModeEntriesExist() {
        val entries = PerformanceMode.entries
        assertEquals(3, entries.size)
        assertTrue(entries.contains(PerformanceMode.NONE))
        assertTrue(entries.contains(PerformanceMode.LOW_LATENCY))
        assertTrue(entries.contains(PerformanceMode.POWER_SAVING))
    }

    @Test
    fun sharingModeEntriesExist() {
        val entries = SharingMode.entries
        assertEquals(2, entries.size)
        assertTrue(entries.contains(SharingMode.SHARED))
        assertTrue(entries.contains(SharingMode.EXCLUSIVE))
    }

    @Test
    fun streamDirectionEntriesExist() {
        val entries = StreamDirection.entries
        assertEquals(2, entries.size)
        assertTrue(entries.contains(StreamDirection.OUTPUT))
        assertTrue(entries.contains(StreamDirection.INPUT))
    }

    @Test
    fun streamStateEntriesExist() {
        val entries = StreamState.entries
        assertEquals(10, entries.size)
        assertTrue(entries.contains(StreamState.UNINITIALIZED))
        assertTrue(entries.contains(StreamState.OPEN))
        assertTrue(entries.contains(StreamState.STARTING))
        assertTrue(entries.contains(StreamState.STARTED))
        assertTrue(entries.contains(StreamState.PAUSING))
        assertTrue(entries.contains(StreamState.PAUSED))
        assertTrue(entries.contains(StreamState.STOPPING))
        assertTrue(entries.contains(StreamState.STOPPED))
        assertTrue(entries.contains(StreamState.CLOSING))
        assertTrue(entries.contains(StreamState.CLOSED))
    }
}
