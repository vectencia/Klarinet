package com.vectencia.klarinet

import java.util.concurrent.atomic.AtomicInteger

internal actual class AtomicFloat actual constructor(initialValue: Float) {
    private val bits = AtomicInteger(initialValue.toRawBits())
    actual fun get(): Float = Float.fromBits(bits.get())
    actual fun set(value: Float) { bits.set(value.toRawBits()) }
}
