package com.spiritbox.session

import com.spiritbox.audio.SAMPLE_RATE
import com.spiritbox.audio.applyRecordGain
import com.vectencia.klarinet.AudioFileFormat
import com.vectencia.klarinet.AudioFileWriter
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

// Writes the dry mix. Klarinet has no post-chain tap, so this is before the band-pass.
// The audio callback only copies into a ring. This writer runs on the session coroutine.
@OptIn(ExperimentalAtomicApi::class)
internal class SessionTape {
    private val gate = AtomicInt(0)

    @Volatile
    var ticket: String? = null

    @Volatile
    var recordGain: Float = 1f

    @Volatile
    var frames: Long = 0L

    @Volatile
    var recent: FloatArray = FloatArray(0)

    private var writer: AudioFileWriter? = null
    private var clipWriter: AudioFileWriter? = null
    private var openPath: String? = null

    fun arm(path: String) {
        withGate {
            ticket = path
            frames = 0L
        }
    }

    fun disarm() {
        val closing = ticket
        ticket = null
        if (closing == null) return
        withGate {
            if (openPath == closing) closeLocked()
        }
    }

    fun write(buffer: FloatArray, numFrames: Int, clip: FloatArray? = null) {
        val path = ticket ?: return
        if (numFrames <= 0) return
        withGate {
            if (ticket != path) return
            try {
                val out = if (writer != null && openPath == path) {
                    writer!!
                } else {
                    closeLocked()
                    AudioFileWriter(
                        filePath = path,
                        format = AudioFileFormat.WAV,
                        sampleRate = SAMPLE_RATE,
                        channelCount = 1,
                    ).also {
                        writer = it
                        openPath = path
                    }
                }
                applyRecordGain(buffer, numFrames, recordGain)
                out.writeFrames(buffer, numFrames)
                frames += numFrames
                recent = buffer.copyOf(numFrames)
                if (clip != null) {
                    val station = clipWriter ?: AudioFileWriter(
                        filePath = "$path.station.wav",
                        format = AudioFileFormat.WAV,
                        sampleRate = SAMPLE_RATE,
                        channelCount = 1,
                    ).also { clipWriter = it }
                    station.writeFrames(clip, numFrames)
                }
            } catch (_: Exception) {
                closeLocked()
                if (ticket == path) ticket = null
            }
        }
    }

    fun seal() = disarm()

    private inline fun <T> withGate(block: () -> T): T {
        while (!gate.compareAndSet(0, 1)) {
            // The other side is the callback worker or the UI thread.
        }
        try {
            return block()
        } finally {
            gate.store(0)
        }
    }

    private fun closeLocked() {
        val current = writer
        val station = clipWriter
        writer = null
        clipWriter = null
        openPath = null
        try {
            current?.close()
        } catch (_: Exception) {
        }
        try {
            station?.close()
        } catch (_: Exception) {
        }
    }
}
