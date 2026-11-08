package com.spiritbox

import com.spiritbox.audio.NoiseFill
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoiseFillTest {
    @Test
    fun fillStaysInRangeAndIsNotSilent() {
        val fill = NoiseFill()
        val frames = 480
        val buffer = FloatArray(frames)
        assertEquals(frames, fill.fill(buffer, frames))
        assertTrue(buffer.all { it in -1f..1f })
        assertTrue(buffer.any { it != 0f })
        assertTrue(buffer.any { it != buffer[0] })
        val first = buffer.copyOf()
        fill.fill(buffer, frames)
        assertTrue(!buffer.contentEquals(first))
    }
}
