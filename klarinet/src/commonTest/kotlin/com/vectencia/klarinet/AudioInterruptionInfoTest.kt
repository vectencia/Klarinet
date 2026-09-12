package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioInterruptionInfoTest {

    @Test
    fun beganNeverSuggestsResume() {
        val info = AudioInterruptionInfo(AudioInterruptionType.BEGAN, shouldResume = false)
        assertEquals(AudioInterruptionType.BEGAN, info.type)
        assertFalse(info.shouldResume)
    }

    @Test
    fun endedCanSuggestResume() {
        val info = AudioInterruptionInfo(AudioInterruptionType.ENDED, shouldResume = true)
        assertEquals(AudioInterruptionType.ENDED, info.type)
        assertTrue(info.shouldResume)
    }
}
