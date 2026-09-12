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
 * [gain] must be an [AudioEffectType.GAIN] already in the first stream's
 * effect chain. Extra streams passed to the constructor are stopped at
 * the same time, after the fade. The timer does not close streams; the
 * host still owns [AudioStream.close].
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
 * SleepTimer(stream, gain, extraLayer).use { timer ->
 *     timer.schedule(durationMs = 30 * 60_000L, fadeMs = 2_000f)
 *     timer.pause()              // countdown only
 *     timer.pause(pauseStreams = true)  // also AudioStream.pause()
 *     timer.resume()
 *     timer.cancel()
 * }
 * ```
 *
 * @see GainParams.FADE_MS
 * @see AudioStream.stop
 */
class SleepTimer private constructor(
    private val streams: List<AudioStream>,
    private val gain: AudioEffect,
    private val nowMs: () -> Long,
    private val scheduler: SleepTimerScheduler,
) : AutoCloseable {

    /**
     * @param stream primary stream faded via [gain] then stopped
     * @param gain [AudioEffectType.GAIN] on [stream]'s chain
     * @param extraStreams additional streams stopped after the fade
     */
    constructor(
        stream: AudioStream,
        gain: AudioEffect,
        vararg extraStreams: AudioStream,
    ) : this(
        listOf(stream) + extraStreams.asList(),
        gain,
        ::sleepTimerNowMs,
        platformSleepTimerScheduler(),
    )

    internal constructor(
        stream: AudioStream,
        gain: AudioEffect,
        nowMs: () -> Long,
        scheduler: SleepTimerScheduler,
        extraStreams: List<AudioStream> = emptyList(),
    ) : this(
        listOf(stream) + extraStreams,
        gain,
        nowMs,
        scheduler,
    )

    init {
        require(streams.isNotEmpty()) { "SleepTimer requires at least one stream" }
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
    private val pausedByTimer = mutableListOf<AudioStream>()

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
     * Freeze the countdown. Playback continues unless [pauseStreams] is true.
     *
     * No-op unless the timer is [SleepTimerState.SCHEDULED].
     *
     * @param pauseStreams if true, also [AudioStream.pause] each started
     *   stream. [resume] and [cancel] restart those streams.
     */
    fun pause(pauseStreams: Boolean = false) {
        requireActive(active, "SleepTimer")
        locked {
            if (stateField != SleepTimerState.SCHEDULED) return
            remainingWhenPaused = (deadlineMs - nowMs()).coerceAtLeast(0L)
            cancelPendingLocked()
            stateField = SleepTimerState.PAUSED
            if (pauseStreams) {
                pauseStreamsLocked()
            }
        }
    }

    /**
     * Continue a paused countdown from the remaining time.
     *
     * Restarts streams paused by [pause] with `pauseStreams = true`.
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
            resumeStreamsLocked()
        }
    }

    /**
     * Drop the schedule. Playback is left running, including if a fade
     * already started. Streams paused via [pause] are restarted.
     *
     * No-op when idle or already completed.
     */
    fun cancel() {
        requireActive(active, "SleepTimer")
        locked {
            resumeStreamsLocked()
            cancelPendingLocked()
            remainingWhenPaused = 0L
            stateField = SleepTimerState.IDLE
        }
    }

    /**
     * Cancel the schedule. Does not stop or close streams.
     * Restarts streams paused via [pause]. Idempotent.
     */
    override fun close() {
        locked {
            if (!active) return
            resumeStreamsLocked()
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
        pausedByTimer.clear()
        for (stream in streams) {
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
    }

    private fun pauseStreamsLocked() {
        pausedByTimer.clear()
        for (stream in streams) {
            try {
                val streamState = stream.state
                if (streamState == StreamState.STARTED || streamState == StreamState.STARTING) {
                    stream.pause()
                    pausedByTimer += stream
                }
            } catch (_: StreamOperationException) {
            } catch (_: ResourceReleasedException) {
            }
        }
    }

    private fun resumeStreamsLocked() {
        for (stream in pausedByTimer) {
            try {
                if (stream.state == StreamState.PAUSED) {
                    stream.start()
                }
            } catch (_: StreamOperationException) {
            } catch (_: ResourceReleasedException) {
            }
        }
        pausedByTimer.clear()
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
