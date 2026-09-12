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

    @Test
    fun androidFocusGainResumes() {
        val info = AudioFocusInterruptions.fromFocusChange(AudioFocusInterruptions.AUDIOFOCUS_GAIN)
        assertEquals(AudioInterruptionType.ENDED, info!!.type)
        assertTrue(info.shouldResume)
    }

    @Test
    fun androidFocusLossBeginsWithoutResume() {
        for (
            change in listOf(
                AudioFocusInterruptions.AUDIOFOCUS_LOSS,
                AudioFocusInterruptions.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioFocusInterruptions.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            )
        ) {
            val info = AudioFocusInterruptions.fromFocusChange(change)!!
            assertEquals(AudioInterruptionType.BEGAN, info.type)
            assertFalse(info.shouldResume)
        }
    }

    @Test
    fun androidUnknownFocusChangeIsIgnored() {
        assertEquals(null, AudioFocusInterruptions.fromFocusChange(0))
        assertEquals(null, AudioFocusInterruptions.fromFocusChange(99))
    }

    @Test
    fun iosBeganIgnoresShouldResumeOption() {
        val info = audioInterruptionFromSession(typeBegan = true, optionShouldResume = true)
        assertEquals(AudioInterruptionType.BEGAN, info.type)
        assertFalse(info.shouldResume)
    }

    @Test
    fun iosEndedRespectsShouldResumeOption() {
        val resume = audioInterruptionFromSession(typeBegan = false, optionShouldResume = true)
        assertEquals(AudioInterruptionType.ENDED, resume.type)
        assertTrue(resume.shouldResume)
        val stay = audioInterruptionFromSession(typeBegan = false, optionShouldResume = false)
        assertEquals(AudioInterruptionType.ENDED, stay.type)
        assertFalse(stay.shouldResume)
    }
}
