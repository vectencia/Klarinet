package com.vectencia.klarinet

actual class AudioEffect internal constructor(
    actual val type: AudioEffectType,
    internal val engineHandle: Long,
    internal var effectHandle: Long,
) {
    actual var isEnabled: Boolean
        get() = JniBridge.nativeIsEffectEnabled(engineHandle, effectHandle)
        set(value) = JniBridge.nativeSetEffectEnabled(engineHandle, effectHandle, value)

    actual fun setParameter(paramId: Int, value: Float) {
        JniBridge.nativeSetEffectParameter(engineHandle, effectHandle, paramId, value)
    }

    actual fun getParameter(paramId: Int): Float {
        return JniBridge.nativeGetEffectParameter(engineHandle, effectHandle, paramId)
    }

    actual fun release() {
        if (effectHandle != 0L) {
            JniBridge.nativeDestroyEffect(engineHandle, effectHandle)
            effectHandle = 0L
        }
    }
}
