package com.vectencia.klarinet

import java.util.Timer
import java.util.TimerTask

private val timer = Timer("klarinet-sleep-timer", true)

internal actual fun sleepTimerNowMs(): Long = System.currentTimeMillis()

internal actual fun platformSleepTimerScheduler(): SleepTimerScheduler {
    return SleepTimerScheduler { delayMs, action ->
        val task = object : TimerTask() {
            override fun run() = action()
        }
        timer.schedule(task, delayMs.coerceAtLeast(0L))
        SleepTimerCancelable { task.cancel() }
    }
}
