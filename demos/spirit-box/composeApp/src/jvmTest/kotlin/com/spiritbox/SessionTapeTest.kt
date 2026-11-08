package com.spiritbox

import com.spiritbox.PsicofoniaSpan
import com.spiritbox.data.listRecordings
import com.spiritbox.audio.mixSession
import com.spiritbox.playback.durationMs
import com.spiritbox.session.SessionMeta
import com.spiritbox.session.SessionTape
import com.spiritbox.session.loadSessionMeta
import com.spiritbox.session.saveSessionMeta
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionTapeTest {
    @Test
    fun mixAtTheRadioAndMicEnds() {
        val radio = floatArrayOf(0.2f, -0.4f, 0.6f)
        val mic = floatArrayOf(-0.1f, 0.8f, -0.3f)
        val radioEnd = mixSession(radio.copyOf(), mic, radio.size, 0f)
        val micEnd = mixSession(radio.copyOf(), mic, radio.size, 1f)
        radio.forEachIndexed { index, sample -> assertEquals(sample, radioEnd[index]) }
        mic.forEachIndexed { index, sample -> assertEquals(sample, micEnd[index]) }
    }

    @Test
    fun tapeWritesPcmWavHeaderAndSamples() {
        val dir = Files.createTempDirectory("spirit-box").toFile()
        val path = File(dir, "session-1.wav").absolutePath
        val samples = floatArrayOf(0f, 0.25f, -0.5f, 0.75f)
        val tape = SessionTape()
        tape.arm(path)
        tape.write(samples, samples.size)
        tape.seal()
        tape.write(floatArrayOf(-0.125f), 1)

        val pcm = readPcmWav(File(path).readBytes())
        assertEquals(48_000, pcm.sampleRate)
        assertEquals(1, pcm.channels)
        assertEquals(samples.size, pcm.samples.size)
        samples.forEachIndexed { index, sample ->
            assertEquals(sample, pcm.samples[index], 1f / 32_768f)
        }
        assertTrue(pcm.samples.any { it != 0f })
    }

    @Test
    fun recordGainLowersTheWavTheWriterSaves() {
        val dir = Files.createTempDirectory("spirit-box-gain").toFile()
        val path = File(dir, "gain.wav").absolutePath
        val tape = SessionTape()
        tape.recordGain = 0.5f
        tape.arm(path)
        tape.write(floatArrayOf(0.5f, -0.5f), 2)
        tape.seal()
        val pcm = readPcmWav(File(path).readBytes())
        assertEquals(48_000, pcm.sampleRate)
        assertEquals(1, pcm.channels)
        assertEquals(0.25f, pcm.samples[0], 1f / 32_768f)
        assertEquals(-0.25f, pcm.samples[1], 1f / 32_768f)
        assertEquals(2L, tape.frames)
    }

    @Test
    fun savedSessionReloadsNameDateDurationAndManualMarks() {
        val dir = Files.createTempDirectory("spirit-box-meta").toFile()
        val path = File(dir, "session-9.wav").absolutePath
        val tape = SessionTape()
        tape.arm(path)
        tape.write(FloatArray(48_000) { 0.1f }, 48_000)
        tape.seal()
        val meta = SessionMeta(
            name = "Noche",
            recordedAtMillis = 1_700_000_000_000L,
            durationMs = durationMs(tape.frames),
            manual = listOf(PsicofoniaSpan(1_000, 5_000)),
        )
        saveSessionMeta(path, meta)
        val loaded = loadSessionMeta(path)
        assertEquals("Noche", loaded?.name)
        assertEquals(meta.recordedAtMillis, loaded?.recordedAtMillis)
        assertEquals(1_000L, loaded?.durationMs)
        assertEquals(meta.manual, loaded?.manual)
        File("$path.station.wav").writeBytes(byteArrayOf(1, 2, 3))
        File(dir, "other.station.wav").writeBytes(byteArrayOf(4))
        assertEquals(listOf(path), listRecordings(dir.absolutePath))
    }
}

private data class PcmWav(val sampleRate: Int, val channels: Int, val samples: FloatArray)

private fun readPcmWav(bytes: ByteArray): PcmWav {
    fun u16(at: Int) = (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)
    fun u32(at: Int) = u16(at) or (u16(at + 2) shl 16)
    fun tag(at: Int, text: String) = text.indices.all { bytes[at + it] == text[it].code.toByte() }
    require(bytes.size >= 12 && tag(0, "RIFF") && tag(8, "WAVE"))
    var at = 12
    var format = -1
    var channels = 0
    var rate = 0
    var bits = 0
    var dataAt = -1
    var dataBytes = 0
    while (at + 8 <= bytes.size) {
        val size = u32(at + 4)
        val body = at + 8
        if (tag(at, "fmt ")) {
            format = u16(body)
            channels = u16(body + 2)
            rate = u32(body + 4)
            bits = u16(body + 14)
        } else if (tag(at, "data")) {
            dataAt = body
            dataBytes = size
            break
        }
        val next = body + size + (size and 1)
        if (next <= at) break
        at = next
    }
    require(format == 1 || format == 3)
    require(bits == 16 || bits == 32)
    require(dataAt >= 0 && channels > 0)
    val bytesPerSample = bits / 8
    val frameBytes = bytesPerSample * channels
    val frames = minOf(dataBytes, bytes.size - dataAt) / frameBytes
    require(frames > 0)
    val samples = FloatArray(frames * channels)
    var cursor = dataAt
    for (index in samples.indices) {
        samples[index] = if (format == 3) {
            Float.fromBits(u32(cursor))
        } else if (bits == 16) {
            val lo = bytes[cursor].toInt() and 0xFF
            val hi = bytes[cursor + 1].toInt()
            ((hi shl 8) or lo) / 32_768f
        } else {
            u32(cursor) / 2_147_483_648f
        }
        cursor += bytesPerSample
    }
    return PcmWav(rate, channels, samples)
}
