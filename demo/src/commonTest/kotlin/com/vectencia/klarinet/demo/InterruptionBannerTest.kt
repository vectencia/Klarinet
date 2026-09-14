package com.vectencia.klarinet.demo

import com.vectencia.klarinet.AudioInterruptionInfo
import com.vectencia.klarinet.AudioInterruptionType
import com.vectencia.klarinet.AudioRouteChangeInfo
import com.vectencia.klarinet.PermissionException
import com.vectencia.klarinet.StreamOperationException
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

    @Test
    fun routeBannerShowsReason() {
        assertNull(routeBanner(null))
        assertEquals(
            "Route: OldDeviceUnavailable",
            routeBanner(AudioRouteChangeInfo("OldDeviceUnavailable", "Headphones")),
        )
    }

    @Test
    fun demoErrorMessageMapsSdkExceptions() {
        assertEquals(
            "need mic",
            demoErrorMessage(PermissionException("need mic")),
        )
        assertEquals(
            "pause failed",
            demoErrorMessage(StreamOperationException("pause failed")),
        )
        assertEquals("boom", demoErrorMessage(IllegalStateException("boom")))
    }

    @Test
    fun formatDbHandlesSilence() {
        assertEquals("-inf dB", formatDb(Float.NEGATIVE_INFINITY))
        assertEquals("-6 dB", formatDb(-6.2f))
    }
}
