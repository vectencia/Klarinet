import com.vectencia.klarinet.AudioEffectType
import com.vectencia.klarinet.AudioEngine
import com.vectencia.klarinet.AudioStreamCallback
import com.vectencia.klarinet.AudioStreamConfig
import com.vectencia.klarinet.GainParams
import com.vectencia.klarinet.StreamDirection
import kotlin.math.PI
import kotlin.math.sin

fun main(args: Array<String>) {
    if (args.firstOrNull() == "fade") {
        listenFade()
    } else {
        playSine()
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

private fun openTone(engine: AudioEngine, sampleRate: Int) =
    engine.openStream(
        config = AudioStreamConfig(
            sampleRate = sampleRate,
            channelCount = 1,
            direction = StreamDirection.OUTPUT,
        ),
        callback = object : AudioStreamCallback {
            private var phase = 0.0
            override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
                val increment = 2.0 * PI * 440.0 / sampleRate
                for (i in 0 until numFrames) {
                    buffer[i] = (sin(phase) * 0.2).toFloat()
                    phase += increment
                }
                return numFrames
            }
        },
    )
