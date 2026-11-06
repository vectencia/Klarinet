package com.vectencia.klarinet

/**
 * Converts interleaved PCM from [sourceRate] to a destination rate using
 * linear interpolation. Used by [AudioEngine.playFile] when the stream's
 * negotiated rate differs from the file (JS AudioContext is typically 48 kHz).
 */
internal expect class LinearResampler(
    sourceRate: Int,
    channels: Int,
) {
    fun hasBufferedSource(): Boolean

    fun render(
        destRate: Int,
        dest: FloatArray,
        destFrames: Int,
        readFrames: (maxFrames: Int) -> FloatArray,
    ): Int
}
