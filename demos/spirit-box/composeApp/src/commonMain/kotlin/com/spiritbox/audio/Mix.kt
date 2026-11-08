package com.spiritbox.audio


internal inline fun mixNoiseAndClipFrame(
    noise: Float,
    clip: FloatArray,
    cursor: Int,
    use: (sample: Float, clipSample: Float, nextCursor: Int) -> Unit,
) {
    val clipSample = clip[cursor]
    val next = cursor + 1
    use(
        (noise + clipSample) * 0.5f,
        clipSample * 0.5f,
        if (next == clip.size) 0 else next,
    )
}

// shortcut: headphones assumed so mic feedback is untreated, add echo control before speaker use
internal fun mixSession(
    radio: FloatArray,
    mic: FloatArray,
    frames: Int,
    micRatio: Float,
    radioOffset: Int = 0,
): FloatArray {
    val radioGain = 1f - micRatio
    for (i in 0 until frames) {
        val at = radioOffset + i
        radio[at] = radio[at] * radioGain + mic[i] * micRatio
    }
    return radio
}
