package com.vectencia.klarinet.coroutines

import com.vectencia.klarinet.StreamState
import kotlin.test.Test
import kotlin.test.assertEquals

class StreamStateFlowTest {
    @Test
    fun streamStateEnumHasExpectedValues() {
        assertEquals(StreamState.OPEN, StreamState.entries[1])
        assertEquals(StreamState.STARTED, StreamState.entries[3])
        assertEquals(StreamState.CLOSED, StreamState.entries[9])
    }
}
