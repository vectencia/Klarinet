package com.spiritbox.session

import com.spiritbox.audio.NoiseFill
import com.spiritbox.audio.SAMPLE_RATE
import com.spiritbox.audio.decodePcm16Wav
import com.spiritbox.audio.mixNoiseAndClipFrame
import com.spiritbox.audio.mixSession
import com.spiritbox.di.stationModule
import com.spiritbox.playback.preparePlaybackSession
import com.spiritbox.station.MAX_DWELL_MS
import com.spiritbox.station.MIN_DWELL_MS
import com.spiritbox.station.StationLabel
import com.spiritbox.station.amStations
import com.spiritbox.station.bandBandwidth
import com.spiritbox.station.fmStations
import com.spiritbox.station.nextHop
import com.spiritbox.station.stationIndex
import com.vectencia.klarinet.AudioEffectType
import com.vectencia.klarinet.AudioEngine
import com.vectencia.klarinet.AudioSessionCategory
import com.vectencia.klarinet.AudioSessionMode
import com.vectencia.klarinet.AudioStream
import com.vectencia.klarinet.AudioStreamCallback
import com.vectencia.klarinet.AudioStreamConfig
import com.vectencia.klarinet.BPFParams
import com.vectencia.klarinet.GainParams
import com.vectencia.klarinet.PerformanceMode
import com.vectencia.klarinet.StreamDirection
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import spiritbox.composeapp.generated.resources.Res
import kotlin.time.Duration.Companion.milliseconds

private const val CENTER_HZ = 1_000f
private const val GAIN_DB = 0f
private const val PLAY_MILLIS = 200L

private class StationMix(
    private val fm: Array<FloatArray>,
    private val am: Array<FloatArray>,
    private val control: RadioControl,
) {
    private val noise = NoiseFill()

    @Volatile var index: Int = 0

    private var heardBand = false
    private var heard = -1
    private var cursor = 0

    fun render(buffer: FloatArray, numFrames: Int, clipOut: FloatArray? = null): Int {
        val frames = noise.fill(buffer, numFrames)
        val band = control.amBand
        val clips = if (band) am else fm
        val station = index
        val safe = if (station in clips.indices) station else 0
        if (band != heardBand || safe != heard) {
            heardBand = band
            heard = safe
            cursor = 0
        }
        val clip = clips[safe]
        var c = cursor
        for (i in 0 until frames) {
            mixNoiseAndClipFrame(buffer[i], clip, c) { sample, clipSample, nextCursor ->
                buffer[i] = sample
                if (clipOut != null) clipOut[i] = clipSample
                c = nextCursor
            }
        }
        cursor = c
        return frames
    }
}

@OptIn(ExperimentalResourceApi::class)
private suspend fun loadStationClips(count: Int): Array<FloatArray> =
    Array(count) { index ->
        decodePcm16Wav(Res.readBytes("files/stations/$index.wav"))
    }

private fun clipsFor(stations: List<StationLabel>, band: List<StationLabel>, all: Array<FloatArray>): Array<FloatArray> =
    Array(band.size) { all[stations.indexOf(band[it])] }


@OptIn(ExperimentalAtomicApi::class)
private class SampleRing(capacity: Int) {
    private val data = FloatArray(capacity)
    private val mask = capacity - 1
    private val writeAt = AtomicInt(0)
    private val readAt = AtomicInt(0)

    fun free(): Int {
        val write = writeAt.load()
        val read = readAt.load()
        return (read - write - 1) and mask
    }

    fun available(): Int {
        val write = writeAt.load()
        val read = readAt.load()
        return (write - read) and mask
    }

    fun push(buffer: FloatArray, frames: Int) {
        var at = writeAt.load()
        var i = 0
        while (i < frames) {
            data[at] = buffer[i]
            at = (at + 1) and mask
            i++
        }
        writeAt.store(at)
    }

    fun take(dest: FloatArray, frames: Int) {
        var at = readAt.load()
        var i = 0
        while (i < frames) {
            dest[i] = data[at]
            at = (at + 1) and mask
            i++
        }
        readAt.store(at)
    }
}

private fun pushBoth(left: SampleRing, right: SampleRing, radio: FloatArray, clip: FloatArray, frames: Int) {
    val room = minOf(left.free(), right.free(), frames)
    if (room == 0) return
    left.push(radio, room)
    right.push(clip, room)
}

@OptIn(ExperimentalAtomicApi::class)
private class MicRing {
    private val data = FloatArray(16_384)
    private val mask = data.size - 1
    private val writeAt = AtomicInt(0)
    private val readAt = AtomicInt(0)

    fun write(buffer: FloatArray, frames: Int) {
        var at = writeAt.load()
        val read = readAt.load()
        var i = 0
        while (i < frames) {
            val next = (at + 1) and mask
            if (next == read) break
            data[at] = buffer[i]
            at = next
            i++
        }
        writeAt.store(at)
    }

    fun readInto(dest: FloatArray, frames: Int) {
        var at = readAt.load()
        val written = writeAt.load()
        for (i in 0 until frames) {
            if (at == written) {
                dest[i] = 0f
            } else {
                dest[i] = data[at]
                at = (at + 1) and mask
            }
        }
        readAt.store(at)
    }
}

internal fun playNoise() {
    val stations = startKoin { modules(stationModule) }.koin.get<List<StationLabel>>()
    try {
        runBlocking {
            playSession(PLAY_MILLIS, {}, RadioControl(), stations, SessionTape(), {})
        }
    } finally {
        stopKoin()
    }
}

internal suspend fun playSession(
    limitMs: Long?,
    onIndex: (Int) -> Unit,
    control: RadioControl,
    stations: List<StationLabel>,
    tape: SessionTape,
    resetMic: () -> Unit,
) {
    val fm = fmStations(stations)
    val am = amStations(stations)
    require(fm.isNotEmpty() && am.isNotEmpty())
    val all = loadStationClips(stations.size)
    val mix = StationMix(
        fm = clipsFor(stations, fm, all),
        am = clipsFor(stations, am, all),
        control = control,
    )
    onIndex(0)
    val micRing = MicRing()
    val micWindow = FloatArray(2_048)
    // shortcut: station capture allocates if a callback exceeds 16384 frames, raise it if the audio thread glitches
    val station = FloatArray(16_384)
    val engine = AudioEngine.create()
    try {
        val session = preparePlaybackSession()
        if (control.micRatio > 0f) {
            session?.configure(AudioSessionCategory.PLAY_AND_RECORD, AudioSessionMode.DEFAULT)
        }
        val band = engine.createEffect(AudioEffectType.BAND_PASS_FILTER)
        try {
            band.setParameter(BPFParams.CENTER_HZ, CENTER_HZ)
            band.setParameter(BPFParams.BANDWIDTH, bandBandwidth(control.amBand))
            val gain = engine.createEffect(AudioEffectType.GAIN)
            try {
                gain.setParameter(GainParams.GAIN_DB, GAIN_DB)
                val chain = engine.createEffectChain()
                try {
                    chain.add(band)
                    chain.add(gain)
                    coroutineScope {
                        var micEngine: AudioEngine? = null
                        var micStream: AudioStream? = null
                        fun shutMic(restorePlayback: Boolean) {
                            val capture = micEngine
                            val input = micStream
                            micEngine = null
                            micStream = null
                            if (capture == null) return
                            try {
                                input?.let { session?.detach(it) }
                            } finally {
                                try {
                                    capture.close()
                                } catch (_: Exception) {
                                }
                            }
                            if (!restorePlayback || control.micRatio > 0f) return
                            try {
                                session?.configure(AudioSessionCategory.PLAYBACK, AudioSessionMode.DEFAULT)
                                session?.setActive(true)
                            } catch (_: Exception) {
                            }
                        }
                        fun openMic() {
                            if (micEngine != null || control.micRatio <= 0f) return
                            var created: AudioEngine? = null
                            var input: AudioStream? = null
                            try {
                                session?.configure(AudioSessionCategory.PLAY_AND_RECORD, AudioSessionMode.DEFAULT)
                                session?.setActive(true)
                                val capture = AudioEngine.create()
                                created = capture
                                val opened = capture.openStream(
                                    config = AudioStreamConfig(
                                        sampleRate = SAMPLE_RATE,
                                        channelCount = 1,
                                        direction = StreamDirection.INPUT,
                                        performanceMode = PerformanceMode.LOW_LATENCY,
                                    ),
                                    callback = object : AudioStreamCallback {
                                        override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
                                            micRing.write(buffer, numFrames)
                                            return numFrames
                                        }
                                    },
                                )
                                input = opened
                                session?.attach(opened)
                                opened.start()
                                micStream = opened
                                micEngine = capture
                            } catch (_: Exception) {
                                input?.let { session?.detach(it) }
                                micStream = null
                                micEngine = null
                                try {
                                    created?.close()
                                } catch (_: Exception) {
                                }
                                control.micRatio = 0f
                                control.requestedMic = 0f
                                resetMic()
                                try {
                                    session?.configure(AudioSessionCategory.PLAYBACK, AudioSessionMode.DEFAULT)
                                    session?.setActive(true)
                                } catch (_: Exception) {
                                }
                            }
                        }
                        // shortcut: ring holds about one second, raise it if a slow disk drops the tail
                        val heard = SampleRing(65_536)
                        val capturedClip = SampleRing(65_536)
                        val stream = engine.openStream(
                            config = AudioStreamConfig(
                                sampleRate = SAMPLE_RATE,
                                channelCount = 1,
                                direction = StreamDirection.OUTPUT,
                                performanceMode = PerformanceMode.LOW_LATENCY,
                            ),
                            callback = object : AudioStreamCallback {
                                override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
                                    val frames = minOf(numFrames, station.size)
                                    mix.render(buffer, frames, station)
                                    val ratio = control.micRatio
                                    if (ratio > 0f) {
                                        var offset = 0
                                        while (offset < frames) {
                                            val count = minOf(micWindow.size, frames - offset)
                                            micRing.readInto(micWindow, count)
                                            mixSession(buffer, micWindow, count, ratio, offset)
                                            offset += count
                                        }
                                    }
                                    val gain = 1f - ratio
                                    if (gain != 1f) {
                                        for (i in 0 until frames) station[i] *= gain
                                    }
                                    pushBoth(heard, capturedClip, buffer, station, frames)
                                    return frames
                                }
                            },
                        )
                        var writer: Job?
                        try {
                            stream.effectChain = chain
                            session?.attach(stream)
                            session?.setActive(true)
                            writer = launch {
                                val chunk = FloatArray(2_048)
                                val clipChunk = FloatArray(2_048)
                                try {
                                    while (isActive) {
                                        val ready = minOf(heard.available(), capturedClip.available(), chunk.size)
                                        if (ready == 0) {
                                            delay(5.milliseconds)
                                        } else {
                                            heard.take(chunk, ready)
                                            capturedClip.take(clipChunk, ready)
                                            tape.write(chunk, ready, clipChunk)
                                        }
                                    }
                                } finally {
                                    while (true) {
                                        val ready = minOf(heard.available(), capturedClip.available(), chunk.size)
                                        if (ready == 0) break
                                        heard.take(chunk, ready)
                                        capturedClip.take(clipChunk, ready)
                                        tape.write(chunk, ready, clipChunk)
                                    }
                                    tape.seal()
                                }
                            }
                            stream.start()
                            println("start")
                            while (control.bandChanged.tryReceive().isSuccess) {
                                // The band is already control.amBand. Drop the wake so the first dwell is not skipped.
                            }
                            val micWatch = launch {
                                try {
                                    openMic()
                                    while (true) {
                                        control.micChanged.receive()
                                        if (control.micRatio > 0f) openMic() else shutMic(restorePlayback = true)
                                    }
                                } finally {
                                    shutMic(restorePlayback = false)
                                }
                            }
                            val clock = launch {
                                var elapsed = 0L
                                var staticAm = control.amBand
                                while (true) {
                                    val amNow = control.amBand
                                    if (amNow != staticAm) {
                                        staticAm = amNow
                                        band.setParameter(BPFParams.BANDWIDTH, bandBandwidth(amNow))
                                    }
                                    val dwell = control.dwellMs.coerceIn(MIN_DWELL_MS, MAX_DWELL_MS)
                                    val nudged = withTimeoutOrNull(dwell.toLong().milliseconds) {
                                        control.bandChanged.receive()
                                    } != null
                                    if (nudged || control.amBand != staticAm) continue
                                    val count = if (staticAm) am.size else fm.size
                                    val next = nextHop(
                                        mix.index,
                                        stationIndex(count, dwell, control.direction, elapsed + dwell, false),
                                        control.holding,
                                    )
                                    // Skip while held so elapsed stays put. nextHop keeps mix.index.
                                    if (control.holding) continue
                                    elapsed += dwell
                                    mix.index = next
                                    onIndex(next)
                                }
                            }
                            try {
                                if (limitMs == null) awaitCancellation() else delay(limitMs.milliseconds)
                            } finally {
                                clock.cancel()
                                micWatch.cancel()
                                stream.stop()
                                println("stop")
                            }
                        } finally {
                            try {
                                session?.setActive(false)
                            } finally {
                                try {
                                    stream.effectChain = null
                                } finally {
                                    stream.close()
                                }
                            }
                            writer?.cancelAndJoin()
                        }
                    }
                } finally {
                    try {
                        chain.clear()
                    } finally {
                        chain.close()
                    }
                }
            } finally {
                gain.close()
            }
        } finally {
            band.close()
        }
    } finally {
        engine.close()
        println("release")
    }
}
