package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import platform.posix.usleep

class SleepTimerNativeTest {

    @Test
    fun nativeSchedulerExpiryFadesThenStops() {
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
                    waitUntil(2_000) { timer.state == SleepTimerState.COMPLETED }
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
    fun nativeSchedulerCancelLeavesPlaybackRunning() {
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
                    usleep(30_000u)
                    timer.cancel()
                    usleep(200_000u)
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
    fun nativeSchedulerPauseResumeKeepsRemaining() {
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
                    usleep(80_000u)
                    timer.pause()
                    val remaining = timer.remainingMs
                    assertEquals(SleepTimerState.PAUSED, timer.state)
                    assertEquals(StreamState.STARTED, stream.state)
                    assertTrue(remaining in 1L..400L)
                    usleep(250_000u)
                    assertEquals(remaining, timer.remainingMs)
                    timer.resume()
                    waitUntil(2_000) { timer.state == SleepTimerState.COMPLETED }
                    assertEquals(SleepTimerState.COMPLETED, timer.state)
                    assertEquals(StreamState.STOPPED, stream.state)
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

private fun waitUntil(timeoutMs: Long, condition: () -> Boolean) {
    val deadline = sleepTimerNowMs() + timeoutMs
    while (sleepTimerNowMs() < deadline && !condition()) {
        usleep(10_000u)
    }
}
