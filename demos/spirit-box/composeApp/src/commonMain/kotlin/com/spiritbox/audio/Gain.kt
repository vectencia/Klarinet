package com.spiritbox.audio

internal fun applyRecordGain(samples: FloatArray, count: Int, gain: Float) {
    if (gain == 1f) return
    val n = count.coerceIn(0, samples.size)
    for (i in 0 until n) samples[i] *= gain
}

internal fun applyPlaybackGain(sample: Float, gain: Float): Float = sample * gain
