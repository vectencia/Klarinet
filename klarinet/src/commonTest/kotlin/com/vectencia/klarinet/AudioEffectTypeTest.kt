package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertEquals

class AudioEffectTypeTest {
    @Test
    fun allEffectTypesExist() {
        assertEquals(16, AudioEffectType.entries.size)
    }

    @Test
    fun effectTypeOrdinals() {
        assertEquals(0, AudioEffectType.GAIN.ordinal)
        assertEquals(11, AudioEffectType.REVERB.ordinal)
        assertEquals(15, AudioEffectType.TREMOLO.ordinal)
    }
}
