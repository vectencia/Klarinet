package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AndroidStreamFormatTest {

    @Test
    fun pcmFloatIsAccepted() {
        requirePcmFloat(AudioFormat.PCM_FLOAT)
    }

    @Test
    fun integerFormatsThrowUnsupportedFormatException() {
        for (format in listOf(AudioFormat.PCM_I16, AudioFormat.PCM_I24, AudioFormat.PCM_I32)) {
            val error = assertFailsWith<UnsupportedFormatException> {
                requirePcmFloat(format)
            }
            assertTrue(error.message!!.contains(format.toString()))
        }
    }
}
