package com.vectencia.klarinet

actual class AudioEffectChain internal constructor(
    internal val engineHandle: Long,
    internal var chainHandle: Long,
) : AutoCloseable {
    actual fun add(effect: AudioEffect) {
        requireActive(chainHandle != 0L, "AudioEffectChain")
        JniBridge.nativeChainAddEffect(engineHandle, chainHandle, effect.effectHandle)
    }

    actual fun remove(effect: AudioEffect) {
        requireActive(chainHandle != 0L, "AudioEffectChain")
        JniBridge.nativeChainRemoveEffect(engineHandle, chainHandle, effect.effectHandle)
    }

    actual fun applyBatch(changes: List<ParameterChange>) {
        requireActive(chainHandle != 0L, "AudioEffectChain")
        for (change in changes) {
            JniBridge.nativeChainEnqueueParam(
                engineHandle, chainHandle, change.effect.effectHandle,
                change.paramId, change.value,
            )
        }
    }

    actual fun clear() {
        requireActive(chainHandle != 0L, "AudioEffectChain")
        JniBridge.nativeChainClear(engineHandle, chainHandle)
    }

    actual val effectCount: Int
        get() {
            requireActive(chainHandle != 0L, "AudioEffectChain")
            return JniBridge.nativeChainGetEffectCount(engineHandle, chainHandle)
        }

    actual fun release() {
        if (chainHandle != 0L) {
            JniBridge.nativeDestroyEffectChain(engineHandle, chainHandle)
            chainHandle = 0L
        }
    }

    actual override fun close() = release()
}
