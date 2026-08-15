@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.vectencia.klarinet

import klarinet_dsp.klarinet_chain_add
import klarinet_dsp.klarinet_chain_clear
import klarinet_dsp.klarinet_chain_create
import klarinet_dsp.klarinet_chain_destroy
import klarinet_dsp.klarinet_chain_enqueue_param
import klarinet_dsp.klarinet_chain_get_effect_count
import klarinet_dsp.klarinet_chain_prepare
import klarinet_dsp.klarinet_chain_process
import klarinet_dsp.klarinet_chain_remove
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned

actual class AudioEffectChain internal constructor() : AutoCloseable {
    internal var handle: COpaquePointer? = klarinet_chain_create()
        ?: throw StreamCreationException("Failed to create effect chain")

    actual fun add(effect: AudioEffect) {
        val h = handle ?: throw ResourceReleasedException("AudioEffectChain has been released")
        klarinet_chain_add(h, effect.nativeHandle)
    }

    actual fun remove(effect: AudioEffect) {
        val h = handle ?: throw ResourceReleasedException("AudioEffectChain has been released")
        klarinet_chain_remove(h, effect.nativeHandle)
    }

    actual fun applyBatch(changes: List<ParameterChange>) {
        val h = handle ?: throw ResourceReleasedException("AudioEffectChain has been released")
        for (change in changes) {
            klarinet_chain_enqueue_param(h, change.effect.nativeHandle, change.paramId, change.value)
        }
    }

    actual fun clear() {
        val h = handle ?: throw ResourceReleasedException("AudioEffectChain has been released")
        klarinet_chain_clear(h)
    }

    actual val effectCount: Int
        get() {
            val h = handle ?: throw ResourceReleasedException("AudioEffectChain has been released")
            return klarinet_chain_get_effect_count(h)
        }

    actual fun release() {
        handle?.let { klarinet_chain_destroy(it) }
        handle = null
    }

    actual override fun close() = release()
}

internal fun AudioEffectChain.prepare(sampleRate: Int, channelCount: Int) {
    val h = handle ?: throw ResourceReleasedException("AudioEffectChain has been released")
    klarinet_chain_prepare(h, sampleRate, channelCount)
}

internal fun AudioEffectChain.process(buffer: FloatArray, numFrames: Int, channelCount: Int) {
    val h = handle ?: return
    buffer.usePinned { pinned ->
        klarinet_chain_process(h, pinned.addressOf(0), numFrames, channelCount)
    }
}
