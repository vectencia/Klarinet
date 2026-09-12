package com.vectencia.klarinet.coroutines

import com.vectencia.klarinet.AudioFileFormat
import com.vectencia.klarinet.AudioFileReader
import com.vectencia.klarinet.AudioFileWriter
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileWriterExtensionsTest {

    @Test
    fun writeFramesSuspendRoundTripsWav() = runBlocking {
        val sampleRate = 16_000
        val numFrames = 256
        val samples = FloatArray(numFrames) { i ->
            sin(2.0 * PI * 440.0 * i / sampleRate).toFloat() * 0.25f
        }
        val file = File.createTempFile("klarinet-coroutines-write-", ".wav")
        try {
            val writer = AudioFileWriter(
                filePath = file.absolutePath,
                format = AudioFileFormat.WAV,
                sampleRate = sampleRate,
                channelCount = 1,
            )
            try {
                writer.writeFramesSuspend(samples, numFrames)
            } finally {
                writer.close()
            }

            val reader = AudioFileReader(file.absolutePath)
            try {
                val decoded = reader.readAllSuspend()
                assertTrue(decoded.size >= numFrames - 16)
                assertEquals(sampleRate, reader.info.sampleRate)
            } finally {
                reader.close()
            }
        } finally {
            file.delete()
        }
    }
}
