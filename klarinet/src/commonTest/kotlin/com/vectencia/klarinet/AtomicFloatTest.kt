package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertEquals

class AtomicFloatTest {
    @Test
    fun roundTripPreservesValue() {
        val af = AtomicFloat(0.0f)
        af.set(0.5f)
        assertEquals(0.5f, af.get())
    }

    @Test
    fun initialValueReturned() {
        assertEquals(1.5f, AtomicFloat(1.5f).get())
    }
}
