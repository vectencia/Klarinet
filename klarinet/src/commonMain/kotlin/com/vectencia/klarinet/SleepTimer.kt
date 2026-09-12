package com.vectencia.klarinet

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.ceil

/**
 * Schedules a fade-out and then stops an [AudioStream].
 *
 * Pause and resume freeze the remaining countdown; they do not pause
 * playback. Cancel (and [close]) drop the schedule and leave playback
 * running. After expiry the timer fades [gain] to silence using
 * [GainParams.FADE_MS], then calls [AudioStream.stop].
 *
 * [gain] must be an [AudioEffectType.GAIN] already in the stream's
 * effect chain. The timer does not close the stream; the host still
 * owns [AudioStream.close].
 *
 * Control methods are safe to call from any thread. They never run on
 * the audio callback.
 *
 * ```kotlin
 * val gain = engine.createEffect(AudioEffectType.GAIN)
 * chain.add(gain)
 * stream.effectChain = chain
 * stream.start()
 *
 * SleepTimer(stream, gain).use { timer ->
 *     timer.schedule(durationMs = 30 * 60_000L, fadeMs = 2_000f)
 *     // timer.pause() / timer.resume() / timer.cancel()
 * }
 * ```
 *
 * @see GainParams.FADE_MS
 * @see AudioStream.stop
 */
class SleepTimer internal constructor(
    private val stream: AudioStream,
    private val gain: AudioEffect,
    private val nowMs: () -> Long,
    private val scheduler: SleepTimerScheduler,
) : AutoCloseable {

    constructor(stream: AudioStream, gain: AudioEffect) : this(
        stream,
        gain,
        ::sleepTimerNowMs,
        platformSleepTimerScheduler(),
    )

    init {
        require(gain.type == AudioEffectType.GAIN) {
            "SleepTimer requires an AudioEffectType.GAIN, was ${gain.type}"
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private val gate = AtomicInt(0)
    private var active = true
    private var generation = 0L
    private var pending: SleepTimerCancelable? = null
    private var deadlineMs = 0L
    private var remainingWhenPaused = 0L
    private var fadeMs = DEFAULT_FADE_MS
    private var stateField = SleepTimerState.IDLE

    /**
     * Current countdown state. Readable from any thread.
     */
    val state: SleepTimerState
        get() = locked { stateField }

    /**
     * Milliseconds left until fade-out starts. Zero while idle, fading,
     * or completed. While paused this is the frozen remainder.
     */
    val remainingMs: Long
        get() = locked {
            when (stateField) {
                SleepTimerState.SCHEDULED -> (deadlineMs - nowMs()).coerceAtLeast(0L)
                SleepTimerState.PAUSED -> remainingWhenPaused
                else -> 0L
            }
        }

    /**
     * Start or replace a countdown.
     *
     * @param durationMs time until fade-out starts. Must be `>= 0`.
     * @param fadeMs gain ramp duration after expiry, then [AudioStream.stop].
     *   Must be `>= 0`. Default [DEFAULT_FADE_MS].
     */
    fun schedule(durationMs: Long, fadeMs: Float = DEFAULT_FADE_MS) {
        require(durationMs >= 0L) { "durationMs must be >= 0" }
        require(fadeMs >= 0f) { "fadeMs must be >= 0" }
        requireActive(active, "SleepTimer")
        locked {
            cancelPendingLocked()
            this.fadeMs = fadeMs
            remainingWhenPaused = durationMs
            deadlineMs = nowMs() + durationMs
            stateField = SleepTimerState.SCHEDULED
            armLocked(durationMs, ::onExpiry)
        }
    }

    /**
     * Freeze the countdown. Playback continues.
     *
     * No-op unless the timer is [SleepTimerState.SCHEDULED].
     */
    fun pause() {
        requireActive(active, "SleepTimer")
        locked {
            if (stateField != SleepTimerState.SCHEDULED) return
            remainingWhenPaused = (deadlineMs - nowMs()).coerceAtLeast(0L)
            cancelPendingLocked()
            stateField = SleepTimerState.PAUSED
        }
    }

    /**
     * Continue a paused countdown from the remaining time.
     *
     * No-op unless the timer is [SleepTimerState.PAUSED].
     */
    fun resume() {
        requireActive(active, "SleepTimer")
        locked {
            if (stateField != SleepTimerState.PAUSED) return
            val remaining = remainingWhenPaused
            deadlineMs = nowMs() + remaining
            stateField = SleepTimerState.SCHEDULED
            armLocked(remaining, ::onExpiry)
        }
    }

    /**
     * Drop the schedule. Playback is left running, including if a fade
     * already started.
     *
     * No-op when idle or already completed.
     */
    fun cancel() {
        requireActive(active, "SleepTimer")
        locked {
            cancelPendingLocked()
            remainingWhenPaused = 0L
            stateField = SleepTimerState.IDLE
        }
    }

    /**
     * Cancel the schedule. Does not stop or close the stream.
     * Idempotent.
     */
    override fun close() {
        locked {
            if (!active) return
            cancelPendingLocked()
            remainingWhenPaused = 0L
            stateField = SleepTimerState.IDLE
            active = false
        }
    }

    private fun onExpiry() {
        if (stateField != SleepTimerState.SCHEDULED) return
        stateField = SleepTimerState.FADING
        remainingWhenPaused = 0L
        gain.setParameter(GainParams.FADE_MS, fadeMs)
        gain.setParameter(GainParams.GAIN_DB, SILENCE_DB)
        val fadeDelayMs = ceil(fadeMs.toDouble()).toLong().coerceAtLeast(0L)
        armLocked(fadeDelayMs, ::onFadeComplete)
    }

    private fun onFadeComplete() {
        if (stateField != SleepTimerState.FADING) return
        stopStreamLocked()
        stateField = SleepTimerState.COMPLETED
    }

    private fun armLocked(delayMs: Long, action: () -> Unit) {
        val gen = generation
        pending = scheduler.schedule(delayMs) {
            locked {
                if (gen != generation) return@locked
                pending = null
                action()
            }
        }
    }

    private fun cancelPendingLocked() {
        generation += 1
        pending?.cancel()
        pending = null
    }

    private fun stopStreamLocked() {
        try {
            val streamState = stream.state
            if (
                streamState == StreamState.STARTED ||
                streamState == StreamState.PAUSED ||
                streamState == StreamState.STARTING ||
                streamState == StreamState.PAUSING
            ) {
                stream.stop()
            }
        } catch (_: StreamOperationException) {
        } catch (_: ResourceReleasedException) {
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private inline fun <T> locked(block: () -> T): T {
        while (!gate.compareAndSet(0, 1)) {
            // Control-thread lock; sections are short.
        }
        try {
            return block()
        } finally {
            gate.store(0)
        }
    }

    companion object {
        /** Default fade-out duration after expiry. */
        const val DEFAULT_FADE_MS = 2_000f

        /** Target [GainParams.GAIN_DB] used for the expiry fade. */
        const val SILENCE_DB = -80f
    }
}
