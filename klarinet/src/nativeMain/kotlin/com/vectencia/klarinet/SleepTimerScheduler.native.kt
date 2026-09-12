@file:OptIn(ObsoleteWorkersApi::class, ExperimentalAtomicApi::class)

package com.vectencia.klarinet

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.Worker
import kotlin.time.TimeSource

private val epoch = TimeSource.Monotonic.markNow()
private val worker = Worker.start(name = "klarinet-sleep-timer")

internal actual fun sleepTimerNowMs(): Long = epoch.elapsedNow().inWholeMilliseconds

internal actual fun platformSleepTimerScheduler(): SleepTimerScheduler {
    return SleepTimerScheduler { delayMs, action ->
        val cancelled = AtomicBoolean(false)
        val delayUs = delayMs.coerceAtLeast(0L) * 1_000L
        worker.executeAfter(delayUs) {
            if (!cancelled.load()) {
                action()
            }
        }
        SleepTimerCancelable { cancelled.store(true) }
    }
}
