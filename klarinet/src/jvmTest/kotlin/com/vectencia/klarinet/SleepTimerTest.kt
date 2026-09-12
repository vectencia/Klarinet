package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun requiresGainEffect() {
        AudioEngine.create().use { engine ->
            val stream = engine.openStream(AudioStreamConfig())
            val pan = engine.createEffect(AudioEffectType.PAN)
            val error = assertFailsWith<IllegalArgumentException> {
                SleepTimer(stream, pan)
            }
            assertTrue(error.message!!.contains("GAIN"))
            pan.close()
            stream.close()
        }
    }

    @Test
    fun scheduleReplacesPreviousCountdown() {
        withTimer { stream, _, _, scheduler, timer ->
            stream.start()
            timer.schedule(durationMs = 5_000, fadeMs = 200f)
            scheduler.advance(100)
            timer.schedule(durationMs = 400, fadeMs = 100f)
            assertEquals(SleepTimerState.SCHEDULED, timer.state)
            assertEquals(400L, timer.remainingMs)

            scheduler.advance(400)
            assertEquals(SleepTimerState.FADING, timer.state)
            scheduler.advance(100)
            assertEquals(SleepTimerState.COMPLETED, timer.state)
            assertEquals(StreamState.STOPPED, stream.state)
        }
    }

    @Test
    fun realSchedulerExpiryFadesThenStops() {
        AudioEngine.create().use { engine ->
            val stream = engine.openStream(AudioStreamConfig())
            val gain = engine.createEffect(AudioEffectType.GAIN)
            val chain = engine.createEffectChain()
            chain.add(gain)
            stream.effectChain = chain
            stream.start()
            try {
                SleepTimer(stream, gain).use { timer ->
                    timer.schedule(durationMs = 80, fadeMs = 40f)
                    val deadline = System.nanoTime() + 2_000_000_000L
                    while (System.nanoTime() < deadline && timer.state != SleepTimerState.COMPLETED) {
                        Thread.sleep(10)
                    }
                    assertEquals(SleepTimerState.COMPLETED, timer.state)
                    assertEquals(StreamState.STOPPED, stream.state)
                    assertEquals(-80f, gain.getParameter(GainParams.GAIN_DB), 0.001f)
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

    @Test
    fun realSchedulerCancelLeavesPlaybackRunning() {
        AudioEngine.create().use { engine ->
            val stream = engine.openStream(AudioStreamConfig())
            val gain = engine.createEffect(AudioEffectType.GAIN)
            val chain = engine.createEffectChain()
            chain.add(gain)
            stream.effectChain = chain
            stream.start()
            try {
                SleepTimer(stream, gain).use { timer ->
                    timer.schedule(durationMs = 500, fadeMs = 40f)
                    Thread.sleep(30)
                    timer.cancel()
                    Thread.sleep(200)
                    assertEquals(SleepTimerState.IDLE, timer.state)
                    assertEquals(StreamState.STARTED, stream.state)
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

    @Test
    fun extraStreamsStopAfterFade() {
        AudioEngine.create().use { engine ->
            val primary = engine.openStream(AudioStreamConfig())
            val extra = engine.openStream(AudioStreamConfig())
            val gain = engine.createEffect(AudioEffectType.GAIN)
            val chain = engine.createEffectChain()
            chain.add(gain)
            primary.effectChain = chain
            val clock = ManualClock()
            val scheduler = ManualScheduler(clock)
            primary.start()
            extra.start()
            try {
                SleepTimer(primary, gain, clock::now, scheduler, listOf(extra)).use { timer ->
                    timer.schedule(durationMs = 200, fadeMs = 50f)
                    scheduler.advance(200)
                    assertEquals(SleepTimerState.FADING, timer.state)
                    assertEquals(StreamState.STARTED, extra.state)
                    scheduler.advance(50)
                    assertEquals(SleepTimerState.COMPLETED, timer.state)
                    assertEquals(StreamState.STOPPED, primary.state)
                    assertEquals(StreamState.STOPPED, extra.state)
                }
            } finally {
                stopIfNeeded(primary)
                stopIfNeeded(extra)
                primary.close()
                extra.close()
                chain.close()
                gain.close()
            }
        }
    }

    @Test
    fun pauseStreamsThenResumeRestartsPlayback() {
        withTimer { stream, _, _, scheduler, timer ->
            stream.start()
            timer.schedule(durationMs = 1_000, fadeMs = 50f)
            scheduler.advance(200)
            timer.pause(pauseStreams = true)
            assertEquals(SleepTimerState.PAUSED, timer.state)
            assertEquals(800L, timer.remainingMs)
            assertEquals(StreamState.PAUSED, stream.state)

            scheduler.advance(5_000)
            assertEquals(800L, timer.remainingMs)
            assertEquals(StreamState.PAUSED, stream.state)

            timer.resume()
            assertEquals(SleepTimerState.SCHEDULED, timer.state)
            assertEquals(StreamState.STARTED, stream.state)
            scheduler.advance(800)
            scheduler.advance(50)
            assertEquals(SleepTimerState.COMPLETED, timer.state)
            assertEquals(StreamState.STOPPED, stream.state)
        }
    }

    @Test
    fun pauseStreamsThenCancelRestartsPlayback() {
        withTimer { stream, _, _, scheduler, timer ->
            stream.start()
            timer.schedule(durationMs = 1_000, fadeMs = 50f)
            timer.pause(pauseStreams = true)
            assertEquals(StreamState.PAUSED, stream.state)
            timer.cancel()
            assertEquals(SleepTimerState.IDLE, timer.state)
            assertEquals(StreamState.STARTED, stream.state)
        }
    }

    @Test
    fun realSchedulerPauseResumeKeepsRemainingAndStops() {
        AudioEngine.create().use { engine ->
            val stream = engine.openStream(AudioStreamConfig())
            val gain = engine.createEffect(AudioEffectType.GAIN)
            val chain = engine.createEffectChain()
            chain.add(gain)
            stream.effectChain = chain
            stream.start()
            try {
                SleepTimer(stream, gain).use { timer ->
                    timer.schedule(durationMs = 400, fadeMs = 40f)
                    Thread.sleep(80)
                    timer.pause()
                    val remaining = timer.remainingMs
                    assertEquals(SleepTimerState.PAUSED, timer.state)
                    assertEquals(StreamState.STARTED, stream.state)
                    assertTrue(remaining in 1L..400L)
                    Thread.sleep(250)
                    assertEquals(SleepTimerState.PAUSED, timer.state)
                    assertEquals(remaining, timer.remainingMs)
                    assertEquals(StreamState.STARTED, stream.state)
                    timer.resume()
                    waitUntil(2_000) { timer.state == SleepTimerState.COMPLETED }
                    assertEquals(SleepTimerState.COMPLETED, timer.state)
                    assertEquals(StreamState.STOPPED, stream.state)
                }
            } finally {
                stopIfNeeded(stream)
                stream.close()
                chain.close()
                gain.close()
            }
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
                    stopIfNeeded(stream)
                }
            }
            stream.close()
            chain.close()
            gain.close()
        }
    }
}

private fun stopIfNeeded(stream: AudioStream) {
    if (stream.state == StreamState.STARTED || stream.state == StreamState.PAUSED) {
        stream.stop()
    }
}

private fun waitUntil(timeoutMs: Long, condition: () -> Boolean) {
    val deadline = System.nanoTime() + timeoutMs * 1_000_000L
    while (System.nanoTime() < deadline && !condition()) {
        Thread.sleep(10)
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
