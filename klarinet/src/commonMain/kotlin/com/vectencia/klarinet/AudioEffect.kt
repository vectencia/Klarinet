package com.vectencia.klarinet

expect class AudioEffect {
    val type: AudioEffectType
    var isEnabled: Boolean
    fun setParameter(paramId: Int, value: Float)
    fun getParameter(paramId: Int): Float
    fun release()
}
