package com.vectencia.klarinet

internal expect class AtomicFloat(initialValue: Float) {
    fun get(): Float
    fun set(value: Float)
}
