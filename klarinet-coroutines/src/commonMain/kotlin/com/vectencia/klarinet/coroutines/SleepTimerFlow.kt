package com.vectencia.klarinet.coroutines

import com.vectencia.klarinet.SleepTimer
import com.vectencia.klarinet.SleepTimerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * Observes the timer's [SleepTimerState] as a cold [Flow].
 *
 * Emits the current state immediately, then polls [SleepTimer.state]
 * every [intervalMs] milliseconds and emits on change. Completes when
 * the timer reaches [SleepTimerState.COMPLETED]. Cancel and close leave
 * the timer [SleepTimerState.IDLE]; this flow keeps polling so a later
 * [SleepTimer.schedule] is still visible. Cancel the collector to stop.
 *
 * @param intervalMs Polling interval in milliseconds. Default is 50 ms.
 * @see awaitState
 * @see remainingMsFlow
 */
fun SleepTimer.stateFlow(intervalMs: Long = 50L): Flow<SleepTimerState> = flow {
    var last = state
    emit(last)
    while (last != SleepTimerState.COMPLETED) {
        delay(intervalMs)
        val current = state
        if (current != last) {
            emit(current)
            last = current
        }
    }
}

/**
 * Emits [SleepTimer.remainingMs] on a regular interval.
 *
 * Completes when the timer reaches [SleepTimerState.COMPLETED], after a
 * final `0` emission if the last polled value was not already zero.
 * Cancel the collector to stop earlier.
 *
 * @param intervalMs Emission interval in milliseconds. Default is 50 ms.
 * @see stateFlow
 */
fun SleepTimer.remainingMsFlow(intervalMs: Long = 50L): Flow<Long> = flow {
    while (true) {
        val remaining = remainingMs
        emit(remaining)
        if (state == SleepTimerState.COMPLETED) break
        delay(intervalMs)
    }
}

/**
 * Suspends until the timer reaches [target].
 *
 * Returns immediately when [SleepTimer.state] is already [target].
 *
 * @param target The [SleepTimerState] to wait for.
 * @see stateFlow
 */
suspend fun SleepTimer.awaitState(target: SleepTimerState) {
    if (state == target) return
    stateFlow().first { it == target }
}
