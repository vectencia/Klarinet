package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertEquals

class AtomicFloatJvmTest {
    @Test fun roundTripPreservesValue() {
        val af = AtomicFloat(0.0f); af.set(0.42f); assertEquals(0.42f, af.get())
    }
    @Test fun initialValueReturned() {
        assertEquals(1.5f, AtomicFloat(1.5f).get())
    }
}
