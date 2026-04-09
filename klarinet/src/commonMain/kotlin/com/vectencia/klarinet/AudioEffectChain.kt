package com.vectencia.klarinet

expect class AudioEffectChain {
    fun add(effect: AudioEffect)
    fun remove(effect: AudioEffect)
    fun applyBatch(changes: List<ParameterChange>)
    fun clear()
    val effectCount: Int
    fun release()
}
