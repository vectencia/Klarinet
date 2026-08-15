@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.vectencia.klarinet

import klarinet_dsp.KlarinetEffectHandle
import klarinet_dsp.klarinet_create_effect
import klarinet_dsp.klarinet_effect_destroy
import klarinet_dsp.klarinet_effect_get_parameter
import klarinet_dsp.klarinet_effect_is_enabled
import klarinet_dsp.klarinet_effect_set_enabled
import klarinet_dsp.klarinet_effect_set_parameter
import kotlinx.cinterop.COpaquePointer

actual class AudioEffect internal constructor(
    actual val type: AudioEffectType,
) : AutoCloseable {
    internal var handle: COpaquePointer? = klarinet_create_effect(type.ordinal)
        ?: throw StreamCreationException("Failed to create audio effect")

    actual var isEnabled: Boolean
        get() {
            val h = handle ?: throw ResourceReleasedException("AudioEffect has been released")
            return klarinet_effect_is_enabled(h) != 0
        }
        set(value) {
            val h = handle ?: throw ResourceReleasedException("AudioEffect has been released")
            klarinet_effect_set_enabled(h, if (value) 1 else 0)
        }

    actual fun setParameter(paramId: Int, value: Float) {
        val h = handle ?: throw ResourceReleasedException("AudioEffect has been released")
        klarinet_effect_set_parameter(h, paramId, value)
    }

    actual fun getParameter(paramId: Int): Float {
        val h = handle ?: throw ResourceReleasedException("AudioEffect has been released")
        return klarinet_effect_get_parameter(h, paramId)
    }

    actual fun release() {
        handle?.let { klarinet_effect_destroy(it) }
        handle = null
    }

    actual override fun close() = release()
}

internal val AudioEffect.nativeHandle: KlarinetEffectHandle
    get() = handle ?: throw ResourceReleasedException("AudioEffect has been released")
