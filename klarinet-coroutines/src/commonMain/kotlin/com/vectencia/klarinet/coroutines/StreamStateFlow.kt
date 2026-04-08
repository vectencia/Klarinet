package com.vectencia.klarinet.coroutines

import com.vectencia.klarinet.AudioStream
import com.vectencia.klarinet.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Observes the stream's [StreamState] as a [StateFlow].
 *
 * Polls the stream state every 50ms and emits changes. The polling
 * coroutine self-terminates when the stream reaches [StreamState.CLOSED].
 */
val AudioStream.stateFlow: StateFlow<StreamState>
    get() {
        val flow = MutableStateFlow(state)
        CoroutineScope(Dispatchers.Default).launch {
            var last = state
            while (last != StreamState.CLOSED) {
                delay(50)
                val current = state
                if (current != last) {
                    flow.value = current
                    last = current
                }
            }
        }
        return flow
    }

/**
 * Suspends until the stream reaches the target [StreamState].
 */
suspend fun AudioStream.awaitState(target: StreamState) {
    if (state == target) return
    stateFlow.first { it == target }
}
