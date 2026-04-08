package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertEquals

class AudioEffectParamsTest {
    @Test
    fun gainParamIds() {
        assertEquals(0, GainParams.GAIN_DB)
    }

    @Test
    fun compressorParamIds() {
        assertEquals(0, CompressorParams.THRESHOLD)
        assertEquals(1, CompressorParams.RATIO)
        assertEquals(4, CompressorParams.MAKEUP_GAIN)
    }

    @Test
    fun eqParamLayout() {
        assertEquals(4, EQParams.PARAMS_PER_BAND)
        // Band 2, frequency = 2*4 + 0 = 8
        val band2Freq = 2 * EQParams.PARAMS_PER_BAND + EQParams.BAND_FREQUENCY
        assertEquals(8, band2Freq)
    }
}
