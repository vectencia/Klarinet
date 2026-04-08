package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LatencyInfoTest {

    @Test
    fun zeroLatencyValues() {
        val info = LatencyInfo(inputLatencyMs = 0.0, outputLatencyMs = 0.0)
        assertEquals(0.0, info.inputLatencyMs)
        assertEquals(0.0, info.outputLatencyMs)
    }

    @Test
    fun positiveLatencyValues() {
        val info = LatencyInfo(inputLatencyMs = 3.5, outputLatencyMs = 7.2)
        assertEquals(3.5, info.inputLatencyMs)
        assertEquals(7.2, info.outputLatencyMs)
    }

    @Test
    fun equality() {
        val a = LatencyInfo(inputLatencyMs = 5.0, outputLatencyMs = 10.0)
        val b = LatencyInfo(inputLatencyMs = 5.0, outputLatencyMs = 10.0)
        val c = LatencyInfo(inputLatencyMs = 5.0, outputLatencyMs = 11.0)
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun copy() {
        val original = LatencyInfo(inputLatencyMs = 2.0, outputLatencyMs = 4.0)
        val copy = original.copy(outputLatencyMs = 8.0)
        assertEquals(2.0, copy.inputLatencyMs)
        assertEquals(8.0, copy.outputLatencyMs)
        assertNotEquals(original, copy)
    }

    @Test
    fun hashCodeConsistentWithEquals() {
        val a = LatencyInfo(inputLatencyMs = 1.5, outputLatencyMs = 3.0)
        val b = LatencyInfo(inputLatencyMs = 1.5, outputLatencyMs = 3.0)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun toStringContainsValues() {
        val info = LatencyInfo(inputLatencyMs = 2.5, outputLatencyMs = 5.0)
        val str = info.toString()
        assertEquals(true, str.contains("2.5"))
        assertEquals(true, str.contains("5.0"))
    }

    @Test
    fun destructuringWorks() {
        val info = LatencyInfo(inputLatencyMs = 1.0, outputLatencyMs = 2.0)
        val (input, output) = info
        assertEquals(1.0, input)
        assertEquals(2.0, output)
    }
}
