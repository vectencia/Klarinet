package com.vectencia.klarinet.coroutines

import com.vectencia.klarinet.AudioInterruptionInfo
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
    fun interruptionFlowAndObserveInterruptionsCanBeUsedTogether() = runTest {
        val session = AudioSessionManager()
        var hostHits = 0
        val host: (AudioInterruptionInfo) -> Unit = { hostHits += 1 }
        session.observeInterruptions(host)
        val job1 = launch { session.interruptionFlow().collect { } }
        val job2 = launch { session.interruptionFlow().collect { } }
        job1.cancel()
        job1.join()
        job2.cancel()
        job2.join()
        session.clearInterruptions(host)
        assertEquals(0, hostHits)
        assertTrue(job1.isCancelled || job1.isCompleted)
        assertTrue(job2.isCancelled || job2.isCompleted)
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
    fun routeChangeFlowCollectCancelDoesNotThrowOnSecondCycle() = runTest {
        val session = AudioSessionManager()
        repeat(2) {
            val job = launch { session.routeChangeFlow().collect { } }
            job.cancel()
            job.join()
            assertTrue(job.isCancelled || job.isCompleted)
        }
        session.clearRouteChanges()
    }

    @Test
    fun sleepTimerStateHasExpectedValues() {
        assertEquals(SleepTimerState.IDLE, SleepTimerState.entries[0])
        assertEquals(SleepTimerState.COMPLETED, SleepTimerState.entries.last())
    }
}
