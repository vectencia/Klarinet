import com.vectencia.klarinet.AudioEngine
import com.vectencia.klarinet.AudioStreamCallback
import com.vectencia.klarinet.AudioStreamConfig
import com.vectencia.klarinet.StreamDirection
import kotlin.math.PI
import kotlin.math.sin

fun main() {
    println("Klarinet sample: 440 Hz sine for 1 second")
    AudioEngine.create().use { engine ->
        val sampleRate = 48_000
        val stream = engine.openStream(
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
        stream.use {
            stream.start()
            Thread.sleep(1_000)
            stream.stop()
        }
    }
    println("Done.")
}
