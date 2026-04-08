package com.vectencia.klarinet

import kotlin.concurrent.AtomicInt

internal actual class AtomicFloat actual constructor(initialValue: Float) {
    private val bits = AtomicInt(initialValue.toRawBits())
    actual fun get(): Float = Float.fromBits(bits.value)
    actual fun set(value: Float) { bits.value = value.toRawBits() }
}
