package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InterruptionObserverTest {

    @Test
    fun lastListenerClearRemovesNsObserver() {
        val session = AudioSessionManager()
        repeat(2) {
            val listener: (AudioInterruptionInfo) -> Unit = {}
            session.observeInterruptions(listener)
            assertTrue(isInterruptionObserverInstalled())
            session.clearInterruptions(listener)
            assertFalse(isInterruptionObserverInstalled())
        }
    }

    @Test
    fun attachKeepsObserverUntilDetach() {
        val session = AudioSessionManager()
        repeat(2) {
            val stream = AudioStream(AudioStreamConfig())
            val listener: (AudioInterruptionInfo) -> Unit = {}
            session.attach(stream)
            assertTrue(isInterruptionObserverInstalled())
            session.observeInterruptions(listener)
            session.clearInterruptions(listener)
            assertTrue(isInterruptionObserverInstalled())
            session.detach(stream)
            assertFalse(isInterruptionObserverInstalled())
            stream.close()
        }
    }
}
