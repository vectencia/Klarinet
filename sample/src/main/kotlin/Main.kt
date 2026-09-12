import com.vectencia.klarinet.AudioEffectType
import com.vectencia.klarinet.AudioEngine
import com.vectencia.klarinet.AudioScene
import com.vectencia.klarinet.AudioScenePlayer
import com.vectencia.klarinet.AudioStreamCallback
import com.vectencia.klarinet.AudioStreamConfig
import com.vectencia.klarinet.GainParams
import com.vectencia.klarinet.SceneLayer
import com.vectencia.klarinet.SleepTimer
import com.vectencia.klarinet.SleepTimerState
import com.vectencia.klarinet.StreamDirection
import kotlin.math.PI
import kotlin.math.sin

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "fade" -> listenFade()
        "sleep" -> listenSleepTimer()
        "scene" -> listenScene()
        else -> playSine()
    }
}

private fun playSine() {
    println("Klarinet sample: 440 Hz sine for 1 second")
    AudioEngine.create().use { engine ->
        val sampleRate = 48_000
        openTone(engine, sampleRate).use { stream ->
            stream.start()
            Thread.sleep(1_000)
            stream.stop()
        }
    }
    println("Done.")
}

/**
 * Device listen for Task 1: 2s fade 0→full, short hold, 2s fade full→0.
 *
 * Run: `./gradlew :sample:run --args=fade`
 */
private fun listenFade() {
    println("Klarinet fade listen: 2s 0→full, hold, 2s full→0 (440 Hz)")
    AudioEngine.create().use { engine ->
        val sampleRate = 48_000
        val gain = engine.createEffect(AudioEffectType.GAIN)
        gain.setParameter(GainParams.GAIN_DB, -80f)
        val chain = engine.createEffectChain()
        chain.add(gain)
        openTone(engine, sampleRate).use { stream ->
            stream.effectChain = chain
            stream.start()

            gain.setParameter(GainParams.FADE_MS, 2_000f)
            gain.setParameter(GainParams.GAIN_DB, 0f)
            Thread.sleep(2_200)
            println("  at full")

            gain.setParameter(GainParams.GAIN_DB, -80f)
            Thread.sleep(2_200)
            println("  at silence")

            stream.stop()
        }
        chain.close()
        gain.close()
    }
    println("Done.")
}

/**
 * Device listen for Task 2: 1s countdown, 1s fade, then stream stop.
 *
 * Run: `./gradlew :sample:run --args=sleep`
 */
private fun listenSleepTimer() {
    println("Klarinet sleep-timer listen: 1s play, 1s fade, then stop (440 Hz)")
    AudioEngine.create().use { engine ->
        val sampleRate = 48_000
        val gain = engine.createEffect(AudioEffectType.GAIN)
        val chain = engine.createEffectChain()
        chain.add(gain)
        openTone(engine, sampleRate).use { stream ->
            stream.effectChain = chain
            stream.start()
            SleepTimer(stream, gain).use { timer ->
                timer.schedule(durationMs = 1_000, fadeMs = 1_000f)
                val deadline = System.nanoTime() + 5_000_000_000L
                while (timer.state != SleepTimerState.COMPLETED && System.nanoTime() < deadline) {
                    Thread.sleep(50)
                }
                println("  timer=${timer.state} stream=${stream.state}")
            }
        }
        chain.close()
        gain.close()
    }
    println("Done.")
}

/**
 * Device listen for Task 4: 440 Hz scene A, 2s crossfade to 660 Hz scene B.
 *
 * Run: `./gradlew :sample:run --args=scene`
 */
private fun listenScene() {
    println("Klarinet scene listen: 1s 440 Hz, 2s crossfade to 660 Hz, 1s hold")
    AudioEngine.create().use { engine ->
        val sampleRate = 48_000
        AudioScenePlayer(engine).use { player ->
            player.transitionTo(
                AudioScene("a", listOf(SceneLayer("low"))),
                fadeMs = 0f,
            ) { openTone(engine, sampleRate, hz = 440.0) }
            Thread.sleep(1_000)
            println("  crossfade")
            player.transitionTo(
                AudioScene("b", listOf(SceneLayer("high"))),
                fadeMs = 2_000f,
            ) { openTone(engine, sampleRate, hz = 660.0) }
            Thread.sleep(3_000)
            println("  at B")
        }
    }
    println("Done.")
}

private fun openTone(engine: AudioEngine, sampleRate: Int) =
    openTone(engine, sampleRate, hz = 440.0)

private fun openTone(engine: AudioEngine, sampleRate: Int, hz: Double) =
    engine.openStream(
        config = AudioStreamConfig(
            sampleRate = sampleRate,
            channelCount = 1,
            direction = StreamDirection.OUTPUT,
        ),
        callback = object : AudioStreamCallback {
            private var phase = 0.0
            override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
                val increment = 2.0 * PI * hz / sampleRate
                for (i in 0 until numFrames) {
                    buffer[i] = (sin(phase) * 0.2).toFloat()
                    phase += increment
                }
                return numFrames
            }
        },
    )
