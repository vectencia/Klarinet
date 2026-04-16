import com.vectencia.klarinet.*
import kotlin.math.sin

fun main() {
    println("Klarinet Demo — Windows Native (miniaudio)")
    println("Playing 440 Hz sine wave for 3 seconds...")

    val engine = AudioEngine.create()
    val sampleRate = 48000
    val twoPi = 2.0 * kotlin.math.PI
    var phase = 0.0

    val callback = object : AudioStreamCallback {
        override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
            val inc = twoPi * 440.0 / sampleRate
            for (i in 0 until numFrames) {
                buffer[i] = (sin(phase) * 0.5).toFloat()
                phase += inc
            }
            return numFrames
        }
    }

    val stream = engine.openStream(
        config = AudioStreamConfig(
            sampleRate = sampleRate,
            channelCount = 1,
            direction = StreamDirection.OUTPUT,
            performanceMode = PerformanceMode.LOW_LATENCY,
        ),
        callback = callback,
    )

    stream.start()
    println("Stream started. State: ${stream.state}")

    // Play for 3 seconds
    platform.posix.sleep(3u)

    stream.stop()
    stream.close()
    engine.release()
    println("Done.")
}
