package com.vectencia.klarinet

import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AudioFileRoundTripTest {

    @Test
    fun wavSineRoundTripPreservesSignal() {
        val sampleRate = 48_000
        val channelCount = 1
        val numFrames = sampleRate / 2
        val frequency = 440.0
        val amplitude = 0.5
        val samples = FloatArray(numFrames) { i ->
            (sin(2.0 * PI * frequency * i / sampleRate) * amplitude).toFloat()
        }

        val file = File.createTempFile("klarinet-roundtrip-", ".wav")
        try {
            AudioFileWriter(
                filePath = file.absolutePath,
                format = AudioFileFormat.WAV,
                sampleRate = sampleRate,
                channelCount = channelCount,
            ).use { writer ->
                writer.writeFrames(samples, numFrames)
            }

            AudioFileReader(file.absolutePath).use { reader ->
                assertEquals(AudioFileFormat.WAV, reader.info.format)
                assertEquals(sampleRate, reader.info.sampleRate)
                assertEquals(channelCount, reader.info.channelCount)
                assertTrue(abs(reader.info.durationMs - 500L) <= 20L)

                val decoded = reader.readAll()
                assertTrue(abs(decoded.size - numFrames) <= 64)

                val analyzer = AudioAnalyzer(fftSize = 2048, sampleRate = sampleRate)
                val result = analyzer.analyze(decoded, decoded.size)
                assertEquals(amplitude.toFloat() / kotlin.math.sqrt(2f), result.rmsLevel, 0.05f)
                assertEquals(amplitude.toFloat(), result.peakLevel, 0.05f)

                val toneEnergy = analyzer.bandEnergy(result, 400f, 480f)
                val highEnergy = analyzer.bandEnergy(result, 2_000f, 4_000f)
                assertTrue(
                    toneEnergy > highEnergy * 8,
                    "440 Hz energy ($toneEnergy) should dominate 2–4 kHz ($highEnergy)",
                )

                reader.seekTo(0)
                val again = reader.readFrames(256)
                assertEquals(256, again.size)
                for (i in again.indices) {
                    assertEquals(samples[i], again[i], 0.02f)
                }
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun stereoWavRoundTripKeepsChannelCount() {
        val sampleRate = 44_100
        val frames = 1_024
        val interleaved = FloatArray(frames * 2) { i ->
            if (i % 2 == 0) 0.25f else -0.25f
        }
        val file = File.createTempFile("klarinet-stereo-", ".wav")
        try {
            AudioFileWriter(file.absolutePath, AudioFileFormat.WAV, sampleRate, 2).use { writer ->
                writer.writeFrames(interleaved, frames)
            }
            AudioFileReader(file.absolutePath).use { reader ->
                assertEquals(2, reader.info.channelCount)
                val decoded = reader.readAll()
                assertTrue(abs(decoded.size - frames * 2) <= 16)
                assertEquals(0.25f, AudioMath.peak(decoded), 0.02f)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun missingFileThrows() {
        assertFailsWith<AudioFileException> {
            AudioFileReader("/tmp/klarinet-does-not-exist-${System.nanoTime()}.wav")
        }
    }

    @Test
    fun unsupportedEncodeFormatThrows() {
        assertFailsWith<UnsupportedFormatException> {
            AudioFileWriter(
                filePath = "/tmp/klarinet-unsupported.m4a",
                format = AudioFileFormat.AAC,
                sampleRate = 48_000,
                channelCount = 1,
            )
        }
    }
}

private fun AudioFileWriter.use(block: (AudioFileWriter) -> Unit) {
    try {
        block(this)
    } finally {
        close()
    }
}

private fun AudioFileReader.use(block: (AudioFileReader) -> Unit) {
    try {
        block(this)
    } finally {
        close()
    }
}
