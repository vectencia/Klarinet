package com.vectencia.klarinet.coroutines

import com.vectencia.klarinet.AudioSessionManager
import com.vectencia.klarinet.SleepTimerState
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionFlowTest {

    @Test
    fun interruptionFlowCanBeCollectedAndCancelled() = runTest {
        val session = AudioSessionManager()
        val job = launch { session.interruptionFlow().collect { } }
        job.cancel()
        job.join()
        assertTrue(job.isCancelled || job.isCompleted)
    }

    @Test
    fun routeChangeFlowCanBeCollectedAndCancelled() = runTest {
        val session = AudioSessionManager()
        val job = launch { session.routeChangeFlow().collect { } }
        job.cancel()
        job.join()
        assertTrue(job.isCancelled || job.isCompleted)
    }

    @Test
    fun sleepTimerStateHasExpectedValues() {
        assertEquals(SleepTimerState.IDLE, SleepTimerState.entries[0])
        assertEquals(SleepTimerState.COMPLETED, SleepTimerState.entries.last())
    }
}
