package com.vectencia.klarinet.coroutines

import com.vectencia.klarinet.AudioStream
import com.vectencia.klarinet.StreamState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Emits the peak audio level at a regular polling interval.
 *
 * Creates a [Flow] that reads the [AudioStream.peakLevel] property
 * (amplitude in the range `0.0` to `1.0`) at the configured [intervalMs]
 * and emits each reading. The flow runs on
 * [Dispatchers.Default][kotlinx.coroutines.Dispatchers.Default] and
 * completes automatically when the stream leaves the
 * [StreamState.STARTED] or [StreamState.STARTING] state, emitting a
 * final `0f` value before completing.
 *
 * ## Performance
 *
 * The default 50 ms interval produces approximately 20 emissions per
 * second, which is a good balance between responsiveness and CPU usage
 * for driving UI level meters. Decreasing [intervalMs] increases CPU
 * usage; values below 16 ms are not recommended.
 *
 * ## Usage
 *
 * ```kotlin
 * val stream = engine.openStream(inputConfig, callback)
 * stream.start()
 *
 * // Collect levels for a VU meter
 * scope.launch {
 *     stream.levelFlow(intervalMs = 50L).collect { level ->
 *         meterView.setLevel(level)
 *     }
 * }
 * ```
 *
 * ## Cancellation
 *
 * The flow is cooperative with structured concurrency. Cancelling the
 * collecting coroutine stops polling immediately.
 *
 * @param intervalMs Emission interval in milliseconds. Default is 50 ms
 *   (approximately 20 updates per second).
 * @return A [Flow] of peak level values in the range `[0.0, 1.0]`.
 * @see AudioStream.peakLevel
 */
fun AudioStream.levelFlow(intervalMs: Long = 50L): Flow<Float> = flow {
    while (state == StreamState.STARTED || state == StreamState.STARTING) {
        emit(peakLevel)
        delay(intervalMs)
    }
    emit(0f)
}.flowOn(Dispatchers.Default)
