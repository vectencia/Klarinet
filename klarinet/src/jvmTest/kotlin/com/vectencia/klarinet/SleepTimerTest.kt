package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SleepTimerTest {

    @Test
    fun expiryFadesThenStopsStream() {
        withTimer { stream, gain, clock, scheduler, timer ->
            stream.start()
            timer.schedule(durationMs = 1_000, fadeMs = 200f)

            assertEquals(SleepTimerState.SCHEDULED, timer.state)
            assertEquals(1_000L, timer.remainingMs)

            scheduler.advance(1_000)

            assertEquals(SleepTimerState.FADING, timer.state)
            assertEquals(0L, timer.remainingMs)
            assertEquals(200f, gain.getParameter(GainParams.FADE_MS), 0.001f)
            assertEquals(-80f, gain.getParameter(GainParams.GAIN_DB), 0.001f)
            assertEquals(StreamState.STARTED, stream.state)

            scheduler.advance(200)

            assertEquals(SleepTimerState.COMPLETED, timer.state)
            assertEquals(StreamState.STOPPED, stream.state)
            assertEquals(clock.now, 1_200L)
        }
    }

    @Test
    fun pauseResumeKeepsRemainingTime() {
        withTimer { stream, _, clock, scheduler, timer ->
            stream.start()
            timer.schedule(durationMs = 1_000, fadeMs = 200f)

            scheduler.advance(400)
            timer.pause()

            assertEquals(SleepTimerState.PAUSED, timer.state)
            assertEquals(600L, timer.remainingMs)
            assertEquals(StreamState.STARTED, stream.state)

            scheduler.advance(5_000)
            assertEquals(SleepTimerState.PAUSED, timer.state)
            assertEquals(600L, timer.remainingMs)
            assertEquals(StreamState.STARTED, stream.state)

            timer.resume()
            assertEquals(SleepTimerState.SCHEDULED, timer.state)
            assertEquals(600L, timer.remainingMs)
            assertEquals(clock.now, 5_400L)

            scheduler.advance(600)
            assertEquals(SleepTimerState.FADING, timer.state)
            assertEquals(StreamState.STARTED, stream.state)

            scheduler.advance(200)
            assertEquals(SleepTimerState.COMPLETED, timer.state)
            assertEquals(StreamState.STOPPED, stream.state)
        }
    }

    @Test
    fun cancelBeforeExpiryLeavesPlaybackRunning() {
        withTimer { stream, gain, _, scheduler, timer ->
            stream.start()
            timer.schedule(durationMs = 1_000, fadeMs = 200f)
            scheduler.advance(300)
            timer.cancel()

            assertEquals(SleepTimerState.IDLE, timer.state)
            assertEquals(0L, timer.remainingMs)
            assertEquals(StreamState.STARTED, stream.state)
            assertEquals(0f, gain.getParameter(GainParams.GAIN_DB), 0.001f)

            scheduler.advance(5_000)
            assertEquals(StreamState.STARTED, stream.state)
            assertEquals(SleepTimerState.IDLE, timer.state)
        }
    }

    @Test
    fun cancelDuringFadePreventsStop() {
        withTimer { stream, _, _, scheduler, timer ->
            stream.start()
            timer.schedule(durationMs = 500, fadeMs = 200f)
            scheduler.advance(500)
            assertEquals(SleepTimerState.FADING, timer.state)

            timer.cancel()
            scheduler.advance(200)

            assertEquals(SleepTimerState.IDLE, timer.state)
            assertEquals(StreamState.STARTED, stream.state)
        }
    }

    @Test
    fun closeCancelsAndIsIdempotent() {
        withTimer { stream, _, _, scheduler, timer ->
            stream.start()
            timer.schedule(durationMs = 1_000, fadeMs = 50f)
            timer.close()
            timer.close()
            scheduler.advance(2_000)
            assertEquals(StreamState.STARTED, stream.state)
            assertEquals(SleepTimerState.IDLE, timer.state)
        }
    }

    private fun withTimer(
        block: (
            stream: AudioStream,
            gain: AudioEffect,
            clock: ManualClock,
            scheduler: ManualScheduler,
            timer: SleepTimer,
        ) -> Unit,
    ) {
        AudioEngine.create().use { engine ->
            val stream = engine.openStream(AudioStreamConfig())
            val gain = engine.createEffect(AudioEffectType.GAIN)
            val chain = engine.createEffectChain()
            chain.add(gain)
            stream.effectChain = chain
            val clock = ManualClock()
            val scheduler = ManualScheduler(clock)
            SleepTimer(stream, gain, clock::now, scheduler).use { timer ->
                try {
                    block(stream, gain, clock, scheduler, timer)
                } finally {
                    if (stream.state == StreamState.STARTED || stream.state == StreamState.PAUSED) {
                        stream.stop()
                    }
                }
            }
            stream.close()
            chain.close()
            gain.close()
        }
    }
}

internal class ManualClock {
    var now: Long = 0L
}

internal class ManualScheduler(private val clock: ManualClock) : SleepTimerScheduler {
    private data class Task(
        val dueMs: Long,
        val action: () -> Unit,
        var cancelled: Boolean = false,
    )

    private val tasks = mutableListOf<Task>()

    override fun schedule(delayMs: Long, action: () -> Unit): SleepTimerCancelable {
        val task = Task(clock.now + delayMs.coerceAtLeast(0L), action)
        tasks += task
        return SleepTimerCancelable { task.cancelled = true }
    }

    fun advance(ms: Long) {
        clock.now += ms
        val due = tasks.filter { !it.cancelled && it.dueMs <= clock.now }.sortedBy { it.dueMs }
        due.forEach { task ->
            if (!task.cancelled) {
                task.cancelled = true
                task.action()
            }
        }
    }
}
