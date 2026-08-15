package com.vectencia.klarinet

actual class AudioEffect internal constructor(
    actual val type: AudioEffectType,
    internal val engineHandle: Long,
    internal var effectHandle: Long,
) : AutoCloseable {
    actual var isEnabled: Boolean
        get() {
            requireActive(effectHandle != 0L, "AudioEffect")
            return JniBridge.nativeIsEffectEnabled(engineHandle, effectHandle)
        }
        set(value) {
            requireActive(effectHandle != 0L, "AudioEffect")
            JniBridge.nativeSetEffectEnabled(engineHandle, effectHandle, value)
        }

    actual fun setParameter(paramId: Int, value: Float) {
        requireActive(effectHandle != 0L, "AudioEffect")
        JniBridge.nativeSetEffectParameter(engineHandle, effectHandle, paramId, value)
    }

    actual fun getParameter(paramId: Int): Float {
        requireActive(effectHandle != 0L, "AudioEffect")
        return JniBridge.nativeGetEffectParameter(engineHandle, effectHandle, paramId)
    }

    actual fun release() {
        if (effectHandle != 0L) {
            JniBridge.nativeDestroyEffect(engineHandle, effectHandle)
            effectHandle = 0L
        }
    }

    actual override fun close() = release()
}
