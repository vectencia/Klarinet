package com.vectencia.klarinet.demo

import com.vectencia.klarinet.AudioInterruptionInfo
import com.vectencia.klarinet.AudioInterruptionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InterruptionBannerTest {
    @Test
    fun beganShowsInterrupted() {
        val info = AudioInterruptionInfo(AudioInterruptionType.BEGAN, shouldResume = false)
        assertEquals("Interrupted", interruptionBanner(info))
    }

    @Test
    fun endedAndNullHideBanner() {
        val ended = AudioInterruptionInfo(AudioInterruptionType.ENDED, shouldResume = true)
        assertNull(interruptionBanner(ended))
        assertNull(interruptionBanner(null))
    }
}
