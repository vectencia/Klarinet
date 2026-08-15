package com.vectencia.klarinet.coroutines

import com.vectencia.klarinet.AudioStream
import com.vectencia.klarinet.StreamState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * Observes the stream's [StreamState] as a cold [Flow].
 *
 * The flow emits the current state immediately, then polls
 * [AudioStream.state] every [intervalMs] milliseconds and emits again
 * when the state changes. Collection stops when the stream reaches
 * [StreamState.CLOSED] or the collector is cancelled.
 *
 * This is a **cold** flow: nothing is polled until it is collected, and
 * each collector has its own polling loop bound to that collector's
 * coroutine. Capture the flow in a variable if several collectors should
 * share the same instance, or use `stateIn` to convert it to a [kotlinx.coroutines.flow.StateFlow].
 *
 * ## Usage
 *
 * ```kotlin
 * val stream = engine.openStream(config, callback)
 * scope.launch {
 *     stream.stateFlow().collect { streamState ->
 *         println("Stream state: $streamState")
 *     }
 * }
 * stream.start()
 * ```
 *
 * @param intervalMs Polling interval in milliseconds. Default is 50 ms.
 * @see awaitState
 */
fun AudioStream.stateFlow(intervalMs: Long = 50L): Flow<StreamState> = flow {
    var last = state
    emit(last)
    while (last != StreamState.CLOSED) {
        delay(intervalMs)
        val current = state
        if (current != last) {
            emit(current)
            last = current
        }
    }
}

/**
 * Suspends until the stream reaches the target [StreamState].
 *
 * If the stream is already in the [target] state, this function returns
 * immediately without suspending. Otherwise, it observes the stream via
 * [stateFlow] and suspends until the target state is emitted.
 *
 * @param target The [StreamState] to wait for.
 * @see stateFlow
 */
suspend fun AudioStream.awaitState(target: StreamState) {
    if (state == target) return
    stateFlow().first { it == target }
}
