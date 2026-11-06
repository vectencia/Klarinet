@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package com.vectencia.klarinet

import klarinet_dsp.klarinet_resample_linear
import klarinet_dsp.klarinet_resampler_create
import klarinet_dsp.klarinet_resampler_destroy
import klarinet_dsp.klarinet_resampler_has_buffered
import kotlin.math.min
import kotlin.native.ref.createCleaner
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.usePinned

private class ResamplerPull(
    val channels: Int,
    val readFrames: (Int) -> FloatArray,
) {
    var thrown: Throwable? = null
}

private val resamplerRead = staticCFunction { user: COpaquePointer?, dst: CPointer<FloatVar>?, maxFrames: Int ->
    val pull = user?.asStableRef<ResamplerPull>()?.get() ?: return@staticCFunction 0
    if (dst == null || maxFrames <= 0 || pull.channels <= 0) return@staticCFunction 0
    try {
        val samples = pull.readFrames(maxFrames)
        if (samples.isEmpty()) return@staticCFunction 0
        val frames = min(samples.size / pull.channels, maxFrames)
        if (frames <= 0) return@staticCFunction 0
        val count = frames * pull.channels
        for (index in 0 until count) {
            dst[index] = samples[index]
        }
        frames
    } catch (thrown: Throwable) {
        pull.thrown = thrown
        -1
    }
}

internal actual class LinearResampler actual constructor(
    sourceRate: Int,
    channels: Int,
) {
    private val channels: Int = channels
    private val handle: COpaquePointer
    private val cleaner: kotlin.native.ref.Cleaner

    init {
        require(sourceRate > 0) { "sourceRate must be > 0" }
        require(channels > 0) { "channels must be > 0" }
        handle = klarinet_resampler_create(sourceRate, channels)
            ?: throw IllegalArgumentException("sourceRate must be > 0")
        cleaner = createCleaner(handle, ::klarinet_resampler_destroy)
    }

    actual fun hasBufferedSource(): Boolean = klarinet_resampler_has_buffered(handle) != 0

    actual fun render(
        destRate: Int,
        dest: FloatArray,
        destFrames: Int,
        readFrames: (maxFrames: Int) -> FloatArray,
    ): Int {
        require(destRate > 0) { "destRate must be > 0" }
        val maxOut = min(destFrames, dest.size / channels)
        if (maxOut <= 0) return 0
        val pull = ResamplerPull(channels, readFrames)
        val stable = StableRef.create(pull)
        try {
            val frames = dest.usePinned { pinned ->
                klarinet_resample_linear(
                    handle,
                    destRate,
                    pinned.addressOf(0),
                    maxOut,
                    resamplerRead,
                    stable.asCPointer(),
                )
            }
            pull.thrown?.let { throw it }
            return if (frames < 0) 0 else frames
        } finally {
            stable.dispose()
        }
    }
}
