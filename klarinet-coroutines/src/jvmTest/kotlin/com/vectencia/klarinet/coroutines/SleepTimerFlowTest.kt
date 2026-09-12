package com.vectencia.klarinet.coroutines

import com.vectencia.klarinet.AudioEffect
import com.vectencia.klarinet.AudioEffectType
import com.vectencia.klarinet.AudioEngine
import com.vectencia.klarinet.AudioStream
import com.vectencia.klarinet.AudioStreamConfig
import com.vectencia.klarinet.SleepTimer
import com.vectencia.klarinet.SleepTimerState
import com.vectencia.klarinet.StreamState
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SleepTimerFlowTest {

    @Test
    fun awaitStateReturnsWhenAlreadyTarget() = runBlocking {
        withLiveTimer { timer, _, _ ->
            assertEquals(SleepTimerState.IDLE, timer.state)
            withTimeout(1_000) { timer.awaitState(SleepTimerState.IDLE) }
        }
    }

    @Test
    fun awaitStateCompletesAfterExpiry() = runBlocking {
        withLiveTimer { timer, stream, _ ->
            stream.start()
            timer.schedule(durationMs = 80, fadeMs = 20f)
            withTimeout(2_000) { timer.awaitState(SleepTimerState.COMPLETED) }
            assertEquals(SleepTimerState.COMPLETED, timer.state)
            assertEquals(StreamState.STOPPED, stream.state)
        }
    }

    @Test
    fun remainingMsFlowTicksThenCompletes() = runBlocking {
        withLiveTimer { timer, stream, _ ->
            stream.start()
            timer.schedule(durationMs = 120, fadeMs = 0f)
            val values = mutableListOf<Long>()
            withTimeout(2_000) {
                timer.remainingMsFlow(intervalMs = 20).collect { values += it }
            }
            assertTrue(values.isNotEmpty())
            assertTrue(values.first() > 0L, "first remaining was ${values.first()}")
            assertEquals(0L, values.last())
            assertEquals(SleepTimerState.COMPLETED, timer.state)
        }
    }

    @Test
    fun stateFlowEmitsScheduledThenCompleted() = runBlocking {
        withLiveTimer { timer, stream, _ ->
            stream.start()
            val states = mutableListOf<SleepTimerState>()
            timer.schedule(durationMs = 80, fadeMs = 0f)
            withTimeout(2_000) {
                timer.stateFlow(intervalMs = 15).collect { states += it }
            }
            assertTrue(SleepTimerState.SCHEDULED in states || states.first() == SleepTimerState.COMPLETED)
            assertEquals(SleepTimerState.COMPLETED, states.last())
        }
    }

    private inline fun withLiveTimer(
        block: (SleepTimer, AudioStream, AudioEffect) -> Unit,
    ) {
        AudioEngine.create().use { engine ->
            val stream = engine.openStream(AudioStreamConfig())
            val gain = engine.createEffect(AudioEffectType.GAIN)
            val chain = engine.createEffectChain()
            chain.add(gain)
            stream.effectChain = chain
            try {
                SleepTimer(stream, gain).use { timer ->
                    block(timer, stream, gain)
                }
            } finally {
                if (stream.state == StreamState.STARTED || stream.state == StreamState.PAUSED) {
                    stream.stop()
                }
                stream.close()
                chain.close()
                gain.close()
            }
        }
    }
}
