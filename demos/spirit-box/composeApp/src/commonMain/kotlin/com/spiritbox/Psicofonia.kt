package com.spiritbox

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal data class PsicofoniaSpan(val startFrame: Int, val endFrame: Int)

private const val RATE = 48_000
private const val FRAME = 480
private const val MIN_SPAN = 3_840
private const val MAX_SPAN = 96_000

internal const val VOICE_LOW_HZ = 300.0
internal const val VOICE_HIGH_HZ = 3_400.0

// Slider top. The quiet-bed 1 kHz burst still marks at 40 and 130; 200 clears it.
internal const val MAX_DETECT_THRESHOLD = 200f

// clip is the station audio mixed into this recording, aligned to the same samples.
// threshold is how many times the median floor a frame must clear. 4 keeps the old marks.
internal fun scanPosiblePsicofonias(
    samples: FloatArray,
    clip: FloatArray? = null,
    threshold: Float = 4f,
): List<PsicofoniaSpan> {
    if (samples.isEmpty()) return emptyList()
    val filtered = bandLimit(samples)
    val levels = frameLevels(filtered)
    if (levels.isEmpty()) return emptyList()
    val floor = median(levels)
    val spans = spansAboveFloor(levels, floor, threshold)
    if (clip == null || spans.isEmpty()) return spans
    val aligned = FloatArray(samples.size) { i -> if (i < clip.size) clip[i] else 0f }
    val filteredClip = bandLimit(aligned)
    return spans.filterNot { span -> clipExplains(filtered, filteredClip, span) }
}

internal fun noiseFloor(samples: FloatArray): Float {
    if (samples.isEmpty()) return 0f
    val levels = frameLevels(bandLimit(samples))
    if (levels.isEmpty()) return 0f
    return median(levels)
}

internal fun applyVoiceBand(samples: FloatArray): FloatArray = bandLimit(samples)

internal fun voiceBandEnergy(samples: FloatArray): Double {
    val filtered = applyVoiceBand(samples)
    var sum = 0.0
    for (sample in filtered) {
        val value = sample.toDouble()
        sum += value * value
    }
    return sum
}

private fun frameLevels(filtered: FloatArray): FloatArray {
    val frameCount = filtered.size / FRAME
    if (frameCount == 0) return FloatArray(0)
    return FloatArray(frameCount) { frame ->
        rms(filtered, frame * FRAME, (frame + 1) * FRAME)
    }
}

private fun spansAboveFloor(levels: FloatArray, floor: Float, threshold: Float): List<PsicofoniaSpan> {
    val spans = ArrayList<PsicofoniaSpan>()
    var run = -1
    for (frame in 0..levels.size) {
        val hot = frame < levels.size && levels[frame] >= threshold * floor && levels[frame] > floor
        if (hot && run < 0) run = frame
        if (!hot && run >= 0) {
            val start = run * FRAME
            val end = frame * FRAME
            val duration = end - start
            if (duration in MIN_SPAN..MAX_SPAN) spans.add(PsicofoniaSpan(start, end))
            run = -1
        }
    }
    return spans
}

private fun clipExplains(session: FloatArray, clip: FloatArray, span: PsicofoniaSpan): Boolean {
    val end = minOf(span.endFrame, session.size, clip.size)
    if (end <= span.startFrame) return false
    var dot = 0.0
    var sessionEnergy = 0.0
    var clipEnergy = 0.0
    for (i in span.startFrame until end) {
        val left = session[i].toDouble()
        val right = clip[i].toDouble()
        dot += left * right
        sessionEnergy += left * left
        clipEnergy += right * right
    }
    if (sessionEnergy <= 1e-12 || clipEnergy <= 1e-12) return false
    val correlation = dot / sqrt(sessionEnergy * clipEnergy)
    return correlation >= 0.8 && clipEnergy >= sessionEnergy * 0.25
}

private fun bandLimit(samples: FloatArray): FloatArray {
    val high = biquad(VOICE_LOW_HZ, lowPass = false)
    val low = biquad(VOICE_HIGH_HZ, lowPass = true)
    val out = FloatArray(samples.size)
    for (i in samples.indices) out[i] = low.process(high.process(samples[i]))
    return out
}

private fun rms(samples: FloatArray, start: Int, end: Int): Float {
    var sum = 0.0
    for (i in start until end) {
        val sample = samples[i].toDouble()
        sum += sample * sample
    }
    val count = end - start
    if (count <= 0) return 0f
    return sqrt(sum / count).toFloat()
}

private fun median(values: FloatArray): Float {
    if (values.isEmpty()) return 0f
    val sorted = values.copyOf()
    sorted.sort()
    return sorted[(sorted.size - 1) / 2]
}

private fun biquad(cutoffHz: Double, lowPass: Boolean): Biquad {
    val w0 = 2.0 * PI * cutoffHz / RATE
    val cosine = cos(w0)
    val alpha = sin(w0) / (2.0 * 0.7071067811865476)
    val b0: Double
    val b1: Double
    val b2: Double
    if (lowPass) {
        b0 = (1.0 - cosine) / 2.0
        b1 = 1.0 - cosine
        b2 = b0
    } else {
        b0 = (1.0 + cosine) / 2.0
        b1 = -(1.0 + cosine)
        b2 = b0
    }
    val a0 = 1.0 + alpha
    return Biquad(
        (b0 / a0).toFloat(),
        (b1 / a0).toFloat(),
        (b2 / a0).toFloat(),
        ((-2.0 * cosine) / a0).toFloat(),
        ((1.0 - alpha) / a0).toFloat(),
    )
}

private class Biquad(
    private val b0: Float,
    private val b1: Float,
    private val b2: Float,
    private val a1: Float,
    private val a2: Float,
) {
    private var z1 = 0f
    private var z2 = 0f

    fun process(x: Float): Float {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }
}
