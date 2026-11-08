package com.spiritbox

import com.spiritbox.station.bandBandwidth
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PsicofoniaTest {
    @Test
    fun silenceReturnsNothing() {
        assertEquals(emptyList(), scanPosiblePsicofonias(FloatArray(48_000)))
    }

    @Test
    fun loudBurstInsideQuietReturnsOneSpan() {
        val (samples, start, end) = quietWithBurst()
        val spans = scanPosiblePsicofonias(samples)
        assertEquals(1, spans.size)
        val span = spans.single()
        val duration = span.endFrame - span.startFrame
        assertTrue(duration in 3_840..96_000, "duration $duration")
        assertTrue(span.startFrame >= start - 4_800, "start ${span.startFrame}")
        assertTrue(span.endFrame <= end + 4_800, "end ${span.endFrame}")
        assertTrue(span.startFrame < end && span.endFrame > start)
    }

    @Test
    fun burstMatchingTheClipReturnsNothing() {
        val (samples, start, end) = quietWithBurst()
        val clip = FloatArray(samples.size)
        for (i in start until end) clip[i] = samples[i]
        assertEquals(1, scanPosiblePsicofonias(samples).size)
        assertEquals(emptyList(), scanPosiblePsicofonias(samples, clip))
    }

    @Test
    fun higherThresholdDropsTheBurst() {
        val (samples, _, _) = quietWithBurst()
        assertEquals(1, scanPosiblePsicofonias(samples).size)
        assertEquals(emptyList(), scanPosiblePsicofonias(samples, threshold = 10_000f))
    }

    @Test
    fun controlMaximumDropsTheQuietBurstTheOldSliderKept() {
        val (samples, _, _) = quietWithBurst()
        assertEquals(1, scanPosiblePsicofonias(samples, threshold = 40f).size)
        assertEquals(1, scanPosiblePsicofonias(samples, threshold = 130f).size)
        assertEquals(emptyList(), scanPosiblePsicofonias(samples, threshold = MAX_DETECT_THRESHOLD))
        assertTrue(MAX_DETECT_THRESHOLD > 130f)
    }

    @Test
    fun louderBedRaisesTheFloorAndHidesTheSameBurst() {
        val (quiet, start, end) = quietWithBurst()
        val loud = quiet.copyOf()
        for (i in loud.indices) {
            if (i < start || i >= end) loud[i] *= 80f
        }
        assertTrue(noiseFloor(loud) > noiseFloor(quiet))
        assertEquals(1, scanPosiblePsicofonias(quiet).size)
        assertEquals(emptyList(), scanPosiblePsicofonias(loud))
    }

    @Test
    fun voiceBandEnergyPrefersTheSpeechRange() {
        val inside = voiceBandEnergy(tone(1_000))
        val edge = voiceBandEnergy(tone(3_000))
        val outside = voiceBandEnergy(tone(8_000))
        assertTrue(inside > outside, "inside $inside outside $outside")
        assertTrue(edge > outside, "edge $edge outside $outside")
        assertEquals(300.0, VOICE_LOW_HZ)
        assertEquals(3_400.0, VOICE_HIGH_HZ)
        assertEquals(1f, bandBandwidth(false))
        assertEquals(2.5f, bandBandwidth(true))
    }
}

private fun tone(hz: Int): FloatArray {
    val count = 12_000
    return FloatArray(count) { index ->
        (sin(2.0 * PI * hz * index / 48_000.0) * 0.5).toFloat()
    }
}

private fun quietWithBurst(): Triple<FloatArray, Int, Int> {
    val rate = 48_000
    val samples = FloatArray(rate * 3)
    var state = 0x1234567
    for (i in samples.indices) {
        state = state xor (state shl 13)
        state = state xor (state ushr 17)
        state = state xor (state shl 5)
        samples[i] = ((state and 0xFFFF) * (1f / 32_768f) - 1f) * 0.02f
    }
    val start = rate
    val len = rate / 5
    for (i in 0 until len) {
        samples[start + i] = (sin(2.0 * PI * 1_000.0 * i / rate) * 0.8).toFloat()
    }
    return Triple(samples, start, start + len)
}
