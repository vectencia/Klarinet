@file:OptIn(ExperimentalWasmJsInterop::class)

package com.vectencia.klarinet

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.unsafeCast

internal actual fun sleepTimerNowMs(): Long {
    return js("Date.now()").unsafeCast<Double>().toLong()
}

internal actual fun platformSleepTimerScheduler(): SleepTimerScheduler {
    return SleepTimerScheduler { delayMs, action ->
        val delay = delayMs.coerceAtLeast(0L).toInt()
        val id = js("setTimeout(action, delay)")
        SleepTimerCancelable {
            js("clearTimeout(id)")
        }
    }
}
