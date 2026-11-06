package com.vectencia.klarinet

import klarinet_dsp.klarinet_render_sine
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value

/**
 * Fills one callback buffer with a sine and returns the advanced phase.
 *
 * The samples come from `klarinet_render_sine`. Swift calls this once per callback.
 */
@OptIn(ExperimentalForeignApi::class)
fun fillCallbackSine(
    buffer: FloatArray,
    frames: Int,
    phase: Float,
    frequencyHz: Float,
    sampleRate: Int,
    amplitude: Float,
): Float {
    if (frames <= 0 || sampleRate <= 0 || buffer.isEmpty()) return phase
    val count = frames.coerceAtMost(buffer.size)
    return memScoped {
        val phaseSlot = alloc<FloatVar>()
        phaseSlot.value = phase
        buffer.usePinned { pinned ->
            klarinet_render_sine(
                pinned.addressOf(0),
                count,
                phaseSlot.ptr,
                frequencyHz,
                sampleRate,
                amplitude,
            )
        }
        phaseSlot.value
    }
}
