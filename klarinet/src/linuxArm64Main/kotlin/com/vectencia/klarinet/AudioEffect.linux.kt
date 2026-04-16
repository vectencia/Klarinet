package com.vectencia.klarinet

actual class AudioEffect internal constructor(
    actual val type: AudioEffectType,
) {
    private val parameters = mutableMapOf<Int, Float>()
    private var _isEnabled = true

    actual var isEnabled: Boolean
        get() = _isEnabled
        set(value) { _isEnabled = value }

    actual fun setParameter(paramId: Int, value: Float) { parameters[paramId] = value }
    actual fun getParameter(paramId: Int): Float = parameters[paramId] ?: 0f
    actual fun release() { parameters.clear() }
}
