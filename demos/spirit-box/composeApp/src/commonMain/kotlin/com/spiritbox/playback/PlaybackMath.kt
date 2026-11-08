package com.spiritbox.playback

import com.spiritbox.audio.SAMPLE_RATE
import kotlin.math.abs
import kotlin.math.roundToInt

internal const val MIN_PLAYBACK_RATE = 0.25f
internal const val MAX_PLAYBACK_RATE = 2f

internal data class PlaybackCursor(
    val index: Double,
    val paused: Boolean,
    val reverse: Boolean,
    val rate: Float,
)

internal fun clampPlaybackRate(rate: Float): Float =
    rate.coerceIn(MIN_PLAYBACK_RATE, MAX_PLAYBACK_RATE)

internal fun advancePlayback(cursor: PlaybackCursor): PlaybackCursor {
    val rate = clampPlaybackRate(cursor.rate)
    if (cursor.paused) return cursor.copy(rate = rate)
    val delta = rate.toDouble() * if (cursor.reverse) -1.0 else 1.0
    return cursor.copy(index = cursor.index + delta, rate = rate)
}

internal fun restartIndex(reverse: Boolean, length: Int): Double {
    if (!reverse || length <= 0) return 0.0
    return (length - 1).toDouble()
}

internal fun reverseWouldLeave(index: Double, rate: Float, length: Int): Boolean {
    if (length <= 0) return true
    val next = advancePlayback(
        PlaybackCursor(index = index, paused = false, reverse = true, rate = rate),
    ).index
    val last = (length - 1).toDouble()
    return next !in 0.0..last
}

internal data class PlaybackTick(
    val index: Double,
    val frame: Int,
    val sample: Float,
    val ended: Boolean,
    val emit: Boolean,
)

internal fun tickPlayback(cursor: PlaybackCursor, samples: FloatArray): PlaybackTick {
    val at = cursor.index
    val last = if (samples.isEmpty()) -1.0 else samples.lastIndex.toDouble()
    val inRange = at in 0.0..last
    if (!cursor.paused && !inRange) {
        val next = advancePlayback(cursor)
        val back = next.index in 0.0..last
        if (!back) return PlaybackTick(at, at.toInt(), 0f, ended = true, emit = false)
        return PlaybackTick(next.index, next.index.toInt(), 0f, ended = false, emit = false)
    }
    val sample = if (cursor.paused || !inRange) 0f else samples[at.toInt()]
    val frame = at.toInt()
    val next = advancePlayback(cursor)
    return PlaybackTick(next.index, frame, sample, ended = false, emit = true)
}

internal fun elapsedMillis(startedAtMillis: Long, nowMillis: Long): Long =
    (nowMillis - startedAtMillis).coerceAtLeast(0L)

internal fun formatElapsed(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val secondText = if (seconds < 10) "0$seconds" else "$seconds"
    return "$minutes:$secondText"
}

internal fun formatRate(rate: Float): String {
    val hundredths = (clampPlaybackRate(rate) * 100f).roundToInt()
    val whole = hundredths / 100
    val frac = hundredths % 100
    return if (frac == 0) "$whole×" else "$whole.${frac / 10}${frac % 10}×"
}

internal fun durationMs(frames: Long, sampleRate: Int = SAMPLE_RATE): Long {
    if (frames <= 0L || sampleRate <= 0) return 0L
    return frames * 1000L / sampleRate
}

internal fun levelEnvelope(
    samples: FloatArray,
    count: Int = samples.size,
    buckets: Int = 24,
): FloatArray = peakBuckets(samples, count, buckets, emptyIfSilent = true)

internal fun sessionWaveform(samples: FloatArray, buckets: Int = 80): FloatArray =
    peakBuckets(samples, samples.size, buckets, emptyIfSilent = false)

private fun peakBuckets(
    samples: FloatArray,
    count: Int,
    buckets: Int,
    emptyIfSilent: Boolean,
): FloatArray {
    val n = count.coerceIn(0, samples.size)
    if (n == 0 || buckets <= 0) return FloatArray(0)
    val out = FloatArray(buckets)
    var peak = 0f
    for (bucket in 0 until buckets) {
        val start = bucket * n / buckets
        val end = (bucket + 1) * n / buckets
        var local = 0f
        for (i in start until end) {
            val mag = abs(samples[i])
            if (mag > local) local = mag
        }
        out[bucket] = local
        if (local > peak) peak = local
    }
    if (emptyIfSilent && peak == 0f) return FloatArray(0)
    return out
}
