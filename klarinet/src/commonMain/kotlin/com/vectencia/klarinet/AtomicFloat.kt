package com.vectencia.klarinet

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class AtomicFloat(initialValue: Float) {
    private val bits = AtomicInt(initialValue.toRawBits())

    fun get(): Float = Float.fromBits(bits.load())

    fun set(value: Float) {
        bits.store(value.toRawBits())
    }
}
