package com.vectencia.klarinet

actual class AudioEffect internal constructor(
    actual val type: AudioEffectType,
) : AutoCloseable {
    private val parameters = mutableMapOf<Int, Float>()
    private var _isEnabled = true
    private var released = false

    actual var isEnabled: Boolean
        get() {
            requireActive(!released, "AudioEffect")
            return _isEnabled
        }
        set(value) {
            requireActive(!released, "AudioEffect")
            _isEnabled = value
        }

    actual fun setParameter(paramId: Int, value: Float) {
        requireActive(!released, "AudioEffect")
        parameters[paramId] = value
    }

    actual fun getParameter(paramId: Int): Float {
        requireActive(!released, "AudioEffect")
        return parameters[paramId] ?: 0f
    }

    actual fun release() {
        released = true
        parameters.clear()
    }

    actual override fun close() = release()
}
