package com.vectencia.klarinet

import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsSleepTimerSchedulerTest {

    @Test
    fun setTimeoutSchedulerFires() = Promise { resolve, reject ->
        platformSleepTimerScheduler().schedule(30) {
            try {
                assertTrue(true)
                resolve(null)
            } catch (error: Throwable) {
                reject(error)
            }
        }
    }

    @Test
    fun setTimeoutSchedulerCancelDoesNotFire() = Promise { resolve, reject ->
        var fired = false
        val handle = platformSleepTimerScheduler().schedule(40) {
            fired = true
        }
        handle.cancel()
        platformSleepTimerScheduler().schedule(90) {
            try {
                assertFalse(fired)
                resolve(null)
            } catch (error: Throwable) {
                reject(error)
            }
        }
    }
}
