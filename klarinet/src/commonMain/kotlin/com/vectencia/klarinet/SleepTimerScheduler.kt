package com.vectencia.klarinet

internal fun interface SleepTimerScheduler {
    fun schedule(delayMs: Long, action: () -> Unit): SleepTimerCancelable
}

internal fun interface SleepTimerCancelable {
    fun cancel()
}

internal expect fun sleepTimerNowMs(): Long

internal expect fun platformSleepTimerScheduler(): SleepTimerScheduler
