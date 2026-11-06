package com.vectencia.klarinet

import kotlin.math.min

internal actual class LinearResampler actual constructor(
    sourceRate: Int,
    channels: Int,
) {
    private val channels: Int = channels
    private val handle: Long

    init {
        require(sourceRate > 0) { "sourceRate must be > 0" }
        require(channels > 0) { "channels must be > 0" }
        handle = JniBridge.nativeResamplerCreate(sourceRate, channels)
        require(handle != 0L) { "sourceRate must be > 0" }
        NativeHandleCleaner.register(this, handle, JniBridge::nativeResamplerDestroy)
    }

    actual fun hasBufferedSource(): Boolean = JniBridge.nativeResamplerHasBuffered(handle) != 0

    actual fun render(
        destRate: Int,
        dest: FloatArray,
        destFrames: Int,
        readFrames: (maxFrames: Int) -> FloatArray,
    ): Int {
        require(destRate > 0) { "destRate must be > 0" }
        val maxOut = min(destFrames, dest.size / channels)
        if (maxOut <= 0) return 0
        return JniBridge.nativeResampleLinear(
            handle = handle,
            destRate = destRate,
            dest = dest,
            destFrames = maxOut,
            channels = channels,
            read = ResamplerRead(readFrames),
        )
    }
}
