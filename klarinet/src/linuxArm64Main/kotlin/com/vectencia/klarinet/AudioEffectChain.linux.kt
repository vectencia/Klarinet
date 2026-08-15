package com.vectencia.klarinet

actual class AudioEffectChain internal constructor() : AutoCloseable {
    private val effects = mutableListOf<AudioEffect>()
    private var released = false

    actual fun add(effect: AudioEffect) {
        requireActive(!released, "AudioEffectChain")
        effects.add(effect)
    }

    actual fun remove(effect: AudioEffect) {
        requireActive(!released, "AudioEffectChain")
        effects.remove(effect)
    }

    actual fun applyBatch(changes: List<ParameterChange>) {
        requireActive(!released, "AudioEffectChain")
        for (change in changes) {
            change.effect.setParameter(change.paramId, change.value)
        }
    }

    actual fun clear() {
        requireActive(!released, "AudioEffectChain")
        effects.clear()
    }

    actual val effectCount: Int
        get() {
            requireActive(!released, "AudioEffectChain")
            return effects.size
        }

    actual fun release() {
        released = true
        effects.clear()
    }

    actual override fun close() = release()
}
