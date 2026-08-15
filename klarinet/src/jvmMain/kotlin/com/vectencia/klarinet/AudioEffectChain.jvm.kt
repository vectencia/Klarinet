package com.vectencia.klarinet

actual class AudioEffectChain internal constructor(
    internal var chainHandle: Long,
) : AutoCloseable {
    actual fun add(effect: AudioEffect) {
        requireActive(chainHandle != 0L, "AudioEffectChain")
        JniBridge.nativeChainAddEffect(chainHandle, effect.effectHandle)
    }

    actual fun remove(effect: AudioEffect) {
        requireActive(chainHandle != 0L, "AudioEffectChain")
        JniBridge.nativeChainRemoveEffect(chainHandle, effect.effectHandle)
    }

    actual fun applyBatch(changes: List<ParameterChange>) {
        requireActive(chainHandle != 0L, "AudioEffectChain")
        for (change in changes) {
            JniBridge.nativeChainEnqueueParam(
                chainHandle, change.effect.effectHandle, change.paramId, change.value,
            )
        }
    }

    actual fun clear() {
        requireActive(chainHandle != 0L, "AudioEffectChain")
        JniBridge.nativeChainClear(chainHandle)
    }

    actual val effectCount: Int
        get() {
            requireActive(chainHandle != 0L, "AudioEffectChain")
            return JniBridge.nativeChainGetEffectCount(chainHandle)
        }

    actual fun release() {
        if (chainHandle != 0L) {
            JniBridge.nativeDestroyEffectChain(chainHandle)
            chainHandle = 0L
        }
    }

    actual override fun close() = release()
}
