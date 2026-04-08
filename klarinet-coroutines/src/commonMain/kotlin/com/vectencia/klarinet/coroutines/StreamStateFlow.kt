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
 * Observes the stream's [StreamState] as a Kotlin [StateFlow].
 *
 * Creates a [StateFlow] that reflects the current state of this
 * [AudioStream]. The implementation polls [AudioStream.state] every
 * 50 ms on [Dispatchers.Default][kotlinx.coroutines.Dispatchers.Default]
 * and emits a new value whenever the state changes. The polling coroutine
 * self-terminates when the stream reaches [StreamState.CLOSED].
 *
 * **Note:** Each access to this property creates a new [StateFlow] and
 * launches a new polling coroutine. If you need to observe state from
 * multiple collectors, capture the returned flow in a variable and
 * share it.
 *
 * ## Performance
 *
 * The 50 ms polling interval means state changes are detected with up
 * to 50 ms of latency. This is suitable for UI updates but should not
 * be relied upon for latency-critical state transitions.
 *
 * ## Usage
 *
 * ```kotlin
 * val stream = engine.openStream(config, callback)
 * val state = stream.stateFlow
 *
 * // Collect in a coroutine
 * scope.launch {
 *     state.collect { streamState ->
 *         println("Stream state: $streamState")
 *     }
 * }
 *
 * stream.start()
 * ```
 *
 * @see awaitState
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
 *
 * If the stream is already in the [target] state, this function returns
 * immediately without suspending. Otherwise, it observes the stream via
 * [stateFlow] and suspends until the target state is emitted.
 *
 * This is particularly useful for waiting until playback or recording
 * completes naturally.
 *
 * ## Usage
 *
 * ```kotlin
 * val stream = engine.playFile("/path/to/audio.mp3")
 * stream.start()
 *
 * // Suspend until playback finishes
 * stream.awaitState(StreamState.STOPPED)
 * stream.close()
 * ```
 *
 * ## Cancellation
 *
 * This function is cancellable. If the calling coroutine is cancelled,
 * the suspension is interrupted and a [kotlinx.coroutines.CancellationException]
 * is thrown.
 *
 * @param target The [StreamState] to wait for.
 * @see stateFlow
 */
suspend fun AudioStream.awaitState(target: StreamState) {
    if (state == target) return
    stateFlow.first { it == target }
}
