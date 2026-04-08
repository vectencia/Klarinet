package com.vectencia.klarinet

actual class AudioEffectChain internal constructor(
    internal val engineHandle: Long,
    internal var chainHandle: Long,
) {
    actual fun add(effect: AudioEffect) {
        JniBridge.nativeChainAddEffect(engineHandle, chainHandle, effect.effectHandle)
    }

    actual fun remove(effect: AudioEffect) {
        JniBridge.nativeChainRemoveEffect(engineHandle, chainHandle, effect.effectHandle)
    }

    actual fun applyBatch(changes: List<ParameterChange>) {
        for (change in changes) {
            JniBridge.nativeChainEnqueueParam(
                engineHandle, chainHandle, change.effect.effectHandle,
                change.paramId, change.value,
            )
        }
    }

    actual fun clear() {
        JniBridge.nativeChainClear(engineHandle, chainHandle)
    }

    actual val effectCount: Int
        get() = JniBridge.nativeChainGetEffectCount(engineHandle, chainHandle)

    actual fun release() {
        if (chainHandle != 0L) {
            JniBridge.nativeDestroyEffectChain(engineHandle, chainHandle)
            chainHandle = 0L
        }
    }
}
