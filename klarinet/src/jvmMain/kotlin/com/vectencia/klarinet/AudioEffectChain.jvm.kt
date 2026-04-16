package com.vectencia.klarinet

actual class AudioEffectChain internal constructor() {
    private val effects = mutableListOf<AudioEffect>()

    actual fun add(effect: AudioEffect) { effects.add(effect) }
    actual fun remove(effect: AudioEffect) { effects.remove(effect) }

    actual fun applyBatch(changes: List<ParameterChange>) {
        for (change in changes) {
            change.effect.setParameter(change.paramId, change.value)
        }
    }

    actual fun clear() { effects.clear() }
    actual val effectCount: Int get() = effects.size
    actual fun release() { effects.clear() }
}
