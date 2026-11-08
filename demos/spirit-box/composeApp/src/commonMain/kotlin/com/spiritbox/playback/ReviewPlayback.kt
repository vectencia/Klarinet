package com.spiritbox.playback

import com.spiritbox.audio.SAMPLE_RATE
import com.spiritbox.audio.applyPlaybackGain
import com.vectencia.klarinet.AudioEngine
import com.vectencia.klarinet.AudioFileReader
import com.vectencia.klarinet.AudioStreamCallback
import com.vectencia.klarinet.AudioStreamConfig
import com.vectencia.klarinet.PerformanceMode
import com.vectencia.klarinet.StreamDirection
import kotlin.concurrent.Volatile
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds


internal class PlayControl {
    @Volatile var samples: FloatArray = FloatArray(0)
    @Volatile var paused: Boolean = false
    @Volatile var reverse: Boolean = false
    @Volatile var rate: Float = 1f
    @Volatile var gain: Float = 1f
    @Volatile var frame: Int = 0
    @Volatile var index: Double = 0.0
    @Volatile var ended: Boolean = false
    @Volatile var restart: Boolean = false
}

internal suspend fun playReview(control: PlayControl) {
    val session = preparePlaybackSession()
    val engine = AudioEngine.create()
    try {
        val stream = engine.openStream(
            config = AudioStreamConfig(
                sampleRate = SAMPLE_RATE,
                channelCount = 1,
                direction = StreamDirection.OUTPUT,
                performanceMode = PerformanceMode.LOW_LATENCY,
            ),
            callback = object : AudioStreamCallback {
                override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
                    val source = control.samples
                    var written = 0
                    while (written < numFrames) {
                        val snapshot = control.index
                        val cursor = PlaybackCursor(
                            index = snapshot,
                            paused = control.paused,
                            reverse = control.reverse,
                            rate = control.rate,
                        )
                        val tick = tickPlayback(cursor, source)
                        if (control.index != snapshot) continue
                        if (tick.ended) {
                            control.ended = true
                            control.frame = tick.frame
                            return written
                        }
                        control.index = tick.index
                        if (!tick.emit) continue
                        control.frame = tick.frame
                        buffer[written] = applyPlaybackGain(tick.sample, control.gain)
                        written++
                    }
                    return written
                }
            },
        )
        try {
            session?.attach(stream)
            session?.setActive(true)
            stream.start()
            while (!control.ended) delay(20.milliseconds)
        } finally {
            try {
                stream.stop()
            } catch (_: Exception) {
            }
            try {
                session?.setActive(false)
            } catch (_: Exception) {
            }
            stream.close()
        }
    } finally {
        engine.close()
    }
}

internal fun loadTake(path: String): Pair<FloatArray, FloatArray?> {
    val samples = readMono(path)
    val clip = runCatching { readMono("$path.station.wav") }.getOrNull()
    return samples to clip
}

private fun readMono(path: String): FloatArray {
    val reader = AudioFileReader(path)
    try {
        return reader.readAll()
    } finally {
        try {
            reader.close()
        } catch (_: Exception) {
        }
    }
}
