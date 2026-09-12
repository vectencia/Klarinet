package com.vectencia.klarinet

actual class AudioEffectChain internal constructor() : AutoCloseable {
    internal val effects = mutableListOf<AudioEffect>()
    internal var attachedStream: AudioStream? = null
    private var active = true

    actual fun add(effect: AudioEffect) {
        requireActive(active, "AudioEffectChain")
        if (!effects.contains(effect)) {
            effects.add(effect)
            attachedStream?.rebuildGraph()
        }
    }

    actual fun remove(effect: AudioEffect) {
        requireActive(active, "AudioEffectChain")
        if (effects.remove(effect)) {
            attachedStream?.rebuildGraph()
        }
    }

    actual fun applyBatch(changes: List<ParameterChange>) {
        requireActive(active, "AudioEffectChain")
        for (change in changes) {
            change.effect.setParameter(change.paramId, change.value)
        }
    }

    actual fun clear() {
        requireActive(active, "AudioEffectChain")
        effects.clear()
        attachedStream?.rebuildGraph()
    }

    actual val effectCount: Int
        get() {
            requireActive(active, "AudioEffectChain")
            return effects.size
        }

    actual fun release() {
        if (!active) return
        attachedStream?.clearEffectChainIf(this)
        attachedStream = null
        effects.clear()
        active = false
    }

    actual override fun close() = release()
}
