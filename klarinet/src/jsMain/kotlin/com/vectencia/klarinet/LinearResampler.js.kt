package com.vectencia.klarinet

import kotlin.math.min

internal actual class LinearResampler actual constructor(
    sourceRate: Int,
    channels: Int,
) {
    private val channels: Int = channels
    private val handle: Int

    init {
        require(sourceRate > 0) { "sourceRate must be > 0" }
        require(channels > 0) { "channels must be > 0" }
        handle = wasmResamplerCreate(sourceRate, channels)
        require(handle != 0) { "sourceRate must be > 0" }
        registerWasmHandle(this, WASM_KIND_RESAMPLER, handle)
    }

    actual fun hasBufferedSource(): Boolean = wasmResamplerHasBuffered(handle) != 0

    actual fun render(
        destRate: Int,
        dest: FloatArray,
        destFrames: Int,
        readFrames: (maxFrames: Int) -> FloatArray,
    ): Int {
        require(destRate > 0) { "destRate must be > 0" }
        val maxOut = min(destFrames, dest.size / channels)
        if (maxOut <= 0) return 0
        return wasmResampleLinear(handle, destRate, dest, maxOut, channels, readFrames)
    }
}
