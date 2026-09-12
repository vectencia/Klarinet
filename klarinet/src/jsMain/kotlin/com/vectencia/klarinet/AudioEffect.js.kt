package com.vectencia.klarinet

actual class AudioEffect internal constructor(
    actual val type: AudioEffectType,
) : AutoCloseable {
    internal val params: MutableMap<Int, Float> = defaultEffectParams(type)
    internal var graph: WebEffectGraph? = null
    private var active = true
    private var enabledField = true

    actual var isEnabled: Boolean
        get() {
            requireActive(active, "AudioEffect")
            return enabledField
        }
        set(value) {
            requireActive(active, "AudioEffect")
            enabledField = value
            graph?.apply(params, value)
        }

    actual fun setParameter(paramId: Int, value: Float) {
        requireActive(active, "AudioEffect")
        params[paramId] = value
        graph?.apply(params, enabledField)
    }

    actual fun getParameter(paramId: Int): Float {
        requireActive(active, "AudioEffect")
        return params[paramId] ?: 0f
    }

    internal fun attach(context: AudioContext): WebEffectGraph {
        graph?.dispose()
        val created = WebEffectGraph.create(context, type)
        created.apply(params, enabledField)
        graph = created
        return created
    }

    actual fun release() {
        if (!active) return
        graph?.dispose()
        graph = null
        active = false
    }

    actual override fun close() = release()
}
