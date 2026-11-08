package com.spiritbox

import com.spiritbox.audio.decodePcm16Wav
import com.spiritbox.audio.mixNoiseAndClipFrame
import com.spiritbox.station.DEFAULT_DWELL_MS
import com.spiritbox.station.HopDirection
import com.spiritbox.station.amStations
import com.spiritbox.station.bandBandwidth
import com.spiritbox.station.fmStations
import com.spiritbox.station.nextHop
import com.spiritbox.station.stationIndex
import com.spiritbox.station.stationLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StationHopTest {
    @Test
    fun oneFrameIsTheAverageAndTheCursorWraps() {
        val clip = floatArrayOf(0.25f, -1f)
        mixNoiseAndClipFrame(0.75f, clip, 0) { sample, clipSample, nextCursor ->
            assertEquals(0.5f, sample)
            assertEquals(0.125f, clipSample)
            assertEquals(1, nextCursor)
            mixNoiseAndClipFrame(1f, clip, nextCursor) { wrapped, _, wrappedCursor ->
                assertEquals(0f, wrapped)
                assertEquals(0, wrappedCursor)
            }
        }
    }

    @Test
    fun forwardHitsFirstLastAndWrap() {
        assertEquals(150, DEFAULT_DWELL_MS)
        assertEquals(0, stationIndex(4, 150, HopDirection.FORWARD, 0, false))
        assertEquals(0, stationIndex(4, 150, HopDirection.FORWARD, 149, false))
        assertEquals(3, stationIndex(4, 150, HopDirection.FORWARD, 450, false))
        assertEquals(0, stationIndex(4, 150, HopDirection.FORWARD, 600, false))
    }

    @Test
    fun reverseHitsFirstLastAndWrap() {
        assertEquals(0, stationIndex(4, 150, HopDirection.REVERSE, 0, false))
        assertEquals(3, stationIndex(4, 150, HopDirection.REVERSE, 150, false))
        assertEquals(0, stationIndex(4, 150, HopDirection.REVERSE, 600, false))
    }

    @Test
    fun holdDoesNotAdvance() {
        val stayed = stationIndex(4, 150, HopDirection.FORWARD, 0, false)
        assertEquals(stayed, stationIndex(4, 150, HopDirection.FORWARD, 600, true))
        assertEquals(stayed, stationIndex(4, 150, HopDirection.REVERSE, 450, true))
    }

    @Test
    fun holdKeepsTheCurrentStation() {
        val stepped = stationIndex(6, 150, HopDirection.FORWARD, 450, false)
        assertEquals(4, nextHop(4, stepped, true))
        assertEquals(stepped, nextHop(4, stepped, false))
    }

    @Test
    fun dwellStaysInsideFiftyToThreeFifty() {
        assertEquals(1, stationIndex(4, 50, HopDirection.FORWARD, 50, false))
        assertEquals(0, stationIndex(4, 1, HopDirection.FORWARD, 49, false))
        assertEquals(1, stationIndex(4, 350, HopDirection.FORWARD, 350, false))
        assertEquals(1, stationIndex(4, 10_000, HopDirection.FORWARD, 350, false))
    }

    @Test
    fun fmBandEndsAtEightyEightAndOneOhEight() {
        assertEquals("88.0 MHz", stationLabel(0).frequency)
        assertEquals("KQZV", stationLabel(0).callsign)
        assertEquals("108.0 MHz", stationLabel(5).frequency)
        assertEquals("WQZX", stationLabel(5).callsign)
    }

    @Test
    fun amBandEndsAtFiveThirtyAndSeventeenHundred() {
        assertEquals("530 kHz", stationLabel(6).frequency)
        assertEquals("WDXQ", stationLabel(6).callsign)
        assertEquals("1700 kHz", stationLabel(11).frequency)
        assertEquals("KBHX", stationLabel(11).callsign)
    }

    @Test
    fun amAndFmAreSeparateListsWithDifferentStatic() {
        val all = List(12) { stationLabel(it) }
        val fm = fmStations(all)
        val am = amStations(all)
        assertEquals(6, fm.size)
        assertEquals(6, am.size)
        assertEquals("88.0 MHz", fm.first().frequency)
        assertEquals("530 kHz", am.first().frequency)
        assertEquals(1f, bandBandwidth(false))
        val amStatic = bandBandwidth(true)
        assertTrue(amStatic != bandBandwidth(false))
        assertTrue(amStatic in 0.1f..4f)
    }

    @Test
    fun pcm16WavSkipsJunkAndScalesSamples() {
        val pcm = decodePcm16Wav(tinyWav(shortArrayOf(0, 16384, -32768)))
        assertEquals(3, pcm.size)
        assertEquals(0f, pcm[0])
        assertEquals(0.5f, pcm[1])
        assertEquals(-1f, pcm[2])
        assertTrue(pcm.all { it in -1f..1f })
    }
}

private fun tinyWav(samples: ShortArray): ByteArray {
    val dataBytes = samples.size * 2
    val junkBody = 4
    val fmtBody = 16
    val size = 12 + (8 + junkBody) + (8 + fmtBody) + (8 + dataBytes)
    val bytes = ByteArray(size)
    var at = 0
    fun ascii(text: String) {
        text.forEach { bytes[at++] = it.code.toByte() }
    }
    fun i32(value: Int) {
        bytes[at++] = value.toByte()
        bytes[at++] = (value shr 8).toByte()
        bytes[at++] = (value shr 16).toByte()
        bytes[at++] = (value shr 24).toByte()
    }
    fun i16(value: Int) {
        bytes[at++] = value.toByte()
        bytes[at++] = (value shr 8).toByte()
    }
    ascii("RIFF")
    i32(size - 8)
    ascii("WAVE")
    ascii("JUNK")
    i32(junkBody)
    i32(0)
    ascii("fmt ")
    i32(fmtBody)
    i16(1)
    i16(1)
    i32(48_000)
    i32(96_000)
    i16(2)
    i16(16)
    ascii("data")
    i32(dataBytes)
    samples.forEach { sample -> i16(sample.toInt()) }
    return bytes
}
