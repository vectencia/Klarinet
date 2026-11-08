package com.spiritbox

import com.spiritbox.PsicofoniaSpan
import com.spiritbox.applyVoiceBand
import com.spiritbox.audio.applyPlaybackGain
import com.spiritbox.audio.applyRecordGain
import com.spiritbox.playback.PlaybackCursor
import com.spiritbox.playback.advancePlayback
import com.spiritbox.playback.clampPlaybackRate
import com.spiritbox.playback.durationMs
import com.spiritbox.playback.elapsedMillis
import com.spiritbox.playback.formatElapsed
import com.spiritbox.playback.levelEnvelope
import com.spiritbox.playback.restartIndex
import com.spiritbox.playback.reverseWouldLeave
import com.spiritbox.playback.tickPlayback
import com.spiritbox.review.addManualMark
import com.spiritbox.review.combineMarks
import com.spiritbox.review.removeManualMark
import com.spiritbox.session.SessionMeta
import com.spiritbox.session.decodeSessionMeta
import com.spiritbox.session.encodeSessionMeta
import com.spiritbox.session.formatUtc
import com.spiritbox.session.listedSession
import com.spiritbox.station.bandBandwidth
import com.spiritbox.transcription.Hypothesis
import com.spiritbox.transcription.UnavailableTranscription
import com.spiritbox.transcription.presentTranscription
import com.spiritbox.transcription.transcriptionDisclaimer
import com.spiritbox.ui.PanelState
import com.spiritbox.ui.channelLuminance
import com.spiritbox.ui.darkSurfaceArgb
import com.spiritbox.ui.lightSurfaceArgb
import com.spiritbox.ui.panelState
import com.spiritbox.ui.surfaceLuminance
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReviewLogicTest {
    @Test
    fun playbackRateClampsAndAdvances() {
        assertEquals(0.25f, clampPlaybackRate(0.1f))
        assertEquals(1f, clampPlaybackRate(1f))
        assertEquals(2f, clampPlaybackRate(4f))

        val quarter = advancePlayback(PlaybackCursor(0.0, paused = false, reverse = false, rate = 0.25f))
        assertEquals(0.25, quarter.index)
        var slow = PlaybackCursor(0.0, paused = false, reverse = false, rate = 0.25f)
        repeat(4) { slow = advancePlayback(slow) }
        assertEquals(1.0, slow.index)

        val unity = advancePlayback(PlaybackCursor(3.0, paused = false, reverse = false, rate = 1f))
        assertEquals(4.0, unity.index)
        val fast = advancePlayback(PlaybackCursor(0.0, paused = false, reverse = false, rate = 2f))
        assertEquals(2.0, fast.index)
        val tooFast = advancePlayback(PlaybackCursor(0.0, paused = false, reverse = false, rate = 9f))
        assertEquals(2.0, tooFast.index)
        assertEquals(2f, tooFast.rate)
    }

    @Test
    fun pauseDoesNotAdvanceAndReverseAtDoubleSpeedWalksBackward() {
        val paused = PlaybackCursor(5.0, paused = true, reverse = false, rate = 2f)
        assertEquals(5.0, advancePlayback(paused).index)
        var cursor = PlaybackCursor(20.0, paused = false, reverse = true, rate = 2f)
        repeat(3) { cursor = advancePlayback(cursor) }
        assertEquals(14.0, cursor.index)
    }

    @Test
    fun reverseStartsAtTheLastSampleAndWalksBackwardAtDoubleSpeed() {
        val samples = FloatArray(11) { it.toFloat() }
        assertTrue(reverseWouldLeave(0.0, 2f, samples.size))
        assertFalse(reverseWouldLeave(10.0, 2f, samples.size))
        val start = restartIndex(reverse = true, length = samples.size)
        assertEquals(10.0, start)
        assertEquals(0.0, restartIndex(reverse = false, length = samples.size))
        val heard = ArrayList<Float>()
        var cursor = PlaybackCursor(start, paused = false, reverse = true, rate = 2f)
        var guard = 0
        while (guard < 20) {
            val tick = tickPlayback(cursor, samples)
            if (tick.ended) break
            cursor = cursor.copy(index = tick.index)
            if (tick.emit) heard += tick.sample
            guard++
        }
        assertEquals(listOf(10f, 8f, 6f, 4f, 2f, 0f), heard)
    }

    @Test
    fun playbackReentersFromEitherEndAndRestartsAfterTheTakeEnds() {
        val samples = FloatArray(11) { it.toFloat() }
        val fromPastEnd = tickPlayback(
            PlaybackCursor(12.0, paused = false, reverse = true, rate = 2f),
            samples,
        )
        assertFalse(fromPastEnd.ended)
        assertFalse(fromPastEnd.emit)
        assertEquals(10.0, fromPastEnd.index)
        val backward = tickPlayback(
            PlaybackCursor(fromPastEnd.index, paused = false, reverse = true, rate = 2f),
            samples,
        )
        assertTrue(backward.emit)
        assertEquals(10f, backward.sample)
        assertEquals(8.0, backward.index)

        val fromBeforeStart = tickPlayback(
            PlaybackCursor(-2.0, paused = false, reverse = false, rate = 2f),
            samples,
        )
        assertFalse(fromBeforeStart.ended)
        assertEquals(0.0, fromBeforeStart.index)
        val forward = tickPlayback(
            PlaybackCursor(fromBeforeStart.index, paused = false, reverse = false, rate = 2f),
            samples,
        )
        assertTrue(forward.emit)
        assertEquals(0f, forward.sample)

        var cursor = PlaybackCursor(0.0, paused = false, reverse = false, rate = 1f)
        val heard = ArrayList<Float>()
        var ended = false
        var guard = 0
        while (guard < 20) {
            val tick = tickPlayback(cursor, samples)
            if (tick.ended) {
                ended = true
                break
            }
            cursor = cursor.copy(index = tick.index)
            if (tick.emit) heard += tick.sample
            guard++
        }
        assertTrue(ended)
        assertEquals(11, heard.size)
        val again = tickPlayback(
            PlaybackCursor(restartIndex(reverse = false, length = samples.size), paused = false, reverse = false, rate = 1f),
            samples,
        )
        assertTrue(again.emit)
        assertEquals(0f, again.sample)
    }

    @Test
    fun playbackGainScalesSamples() {
        assertEquals(0.5f, applyPlaybackGain(0.25f, 2f))
        assertEquals(-0.8f, applyPlaybackGain(-0.4f, 2f))
        assertEquals(0.25f, applyPlaybackGain(0.25f, 1f))
    }

    @Test
    fun recordGainUnityLeavesSamplesAndALowerGainLowersThem() {
        val unity = floatArrayOf(0.5f, -0.25f)
        applyRecordGain(unity, unity.size, 1f)
        assertEquals(0.5f, unity[0])
        assertEquals(-0.25f, unity[1])
        val quieter = floatArrayOf(0.5f, -0.25f)
        applyRecordGain(quieter, quieter.size, 0.5f)
        assertEquals(0.25f, quieter[0])
        assertEquals(-0.125f, quieter[1])
    }

    @Test
    fun voiceBandPassLowersAnEightKilohertzToneMoreThanSpeech() {
        val speech = rms(applyVoiceBand(reviewTone(1_000)))
        val stillSpeech = rms(applyVoiceBand(reviewTone(3_000)))
        val hiss = rms(applyVoiceBand(reviewTone(8_000)))
        assertTrue(hiss < speech, "hiss $hiss speech $speech")
        assertTrue(stillSpeech > hiss * 2, "3 kHz $stillSpeech hiss $hiss")
        assertEquals(300.0, VOICE_LOW_HZ)
        assertEquals(3_400.0, VOICE_HIGH_HZ)
        assertTrue(VOICE_LOW_HZ != bandBandwidth(false).toDouble())
        assertTrue(VOICE_HIGH_HZ != bandBandwidth(true).toDouble())
    }

    @Test
    fun manualMarksAddRemoveAndStayDistinctFromAutomaticOnes() {
        val auto = listOf(PsicofoniaSpan(0, 480))
        val added = addManualMark(emptyList(), 1_000)
        assertEquals(1, added.size)
        val marks = combineMarks(auto, added)
        assertFalse(marks.first().manual)
        assertTrue(marks.last().manual)
        assertEquals(emptyList(), removeManualMark(added, added.single()))
        assertEquals(auto, combineMarks(auto, emptyList()).map { PsicofoniaSpan(it.startFrame, it.endFrame) })
    }

    @Test
    fun levelEnvelopeIsEmptyOnSilenceAndPresentOnALoudBlock() {
        assertEquals(0, levelEnvelope(FloatArray(480)).size)
        val loud = FloatArray(480) { 0.6f }
        val envelope = levelEnvelope(loud)
        assertTrue(envelope.isNotEmpty())
        assertTrue(envelope.any { it > 0f })
    }

    @Test
    fun elapsedTimeDoesNotRunBackward() {
        assertEquals(1_500L, elapsedMillis(1_000L, 2_500L))
        assertEquals(0L, elapsedMillis(5_000L, 1_000L))
        assertEquals("0:01", formatElapsed(1_500L))
        assertEquals("1:01", formatElapsed(61_000L))
    }

    @Test
    fun darkSurfaceIsDarkerAndTheAccentIsShared() {
        assertTrue(surfaceLuminance(dark = true) < surfaceLuminance(dark = false))
        val light = lightSurfaceArgb()
        val dark = darkSurfaceArgb()
        assertTrue(channelLuminance(dark) < channelLuminance(light))
    }

    @Test
    fun libraryStatesStayDistinct() {
        assertEquals(PanelState.Loading, panelState(loading = true, failed = true, itemCount = 4))
        assertEquals(PanelState.Error, panelState(loading = false, failed = true, itemCount = 0))
        assertEquals(PanelState.Empty, panelState(loading = false, failed = false, itemCount = 0))
        assertEquals(PanelState.Ready, panelState(loading = false, failed = false, itemCount = 2))
        assertEquals(4, PanelState.entries.size)
    }

    @Test
    fun transcriptionShowsConfidenceOrSaysItIsNotConnected() {
        val connected = presentTranscription(listOf(Hypothesis("tal vez agua", 0.42f)))
        assertFalse(connected.notConnected)
        assertEquals("tal vez agua", connected.lines.single().text)
        assertEquals(0.42f, connected.lines.single().confidence)
        val port = UnavailableTranscription()
        val empty = presentTranscription(port.transcribe(FloatArray(16), 0, 16))
        assertTrue(empty.notConnected)
        assertTrue(empty.lines.isEmpty())
        assertEquals("Transcription is not connected", empty.status)
        assertTrue(empty.lines.none { it.text.isNotBlank() })
        val warning = transcriptionDisclaimer()
        assertTrue(warning.contains("automatic", ignoreCase = true))
        assertTrue(warning.contains("not contact with the dead", ignoreCase = true))
        assertTrue(warning.contains("not a deciphered message", ignoreCase = true))
        assertEquals(warning, empty.warning)
        assertEquals(warning, connected.warning)
    }

    @Test
    fun sessionListShowsNameDateAndDurationFromTheTake() {
        val meta = SessionMeta(
            name = "Noche",
            recordedAtMillis = 0L,
            durationMs = 61_000L,
            manual = listOf(PsicofoniaSpan(10, 40)),
        )
        assertEquals(meta, decodeSessionMeta(encodeSessionMeta(meta)))
        val listed = listedSession("/recordings/session-1.wav", meta)
        assertEquals("Noche", listed.title)
        assertEquals("1970-01-01 00:00", listed.recorded)
        assertEquals("1:01", listed.duration)
        assertEquals("1970-01-02 00:00", formatUtc(86_400_000L))
        assertEquals(1_000L, durationMs(48_000))
    }
}

private fun reviewTone(hz: Int): FloatArray {
    val count = 12_000
    return FloatArray(count) { index ->
        (sin(2.0 * PI * hz * index / 48_000.0) * 0.5).toFloat()
    }
}

private fun rms(samples: FloatArray): Double {
    var sum = 0.0
    for (sample in samples) {
        val value = sample.toDouble()
        sum += value * value
    }
    return kotlin.math.sqrt(sum / samples.size)
}
