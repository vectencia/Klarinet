package com.vectencia.klarinet.coroutines

import com.vectencia.klarinet.AudioFileFormat
import com.vectencia.klarinet.AudioFileReader
import com.vectencia.klarinet.AudioFileWriter
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileReaderAsFlowTest {

    @Test
    fun asFlowEmitsWavChunksMatchingReadAll() = runBlocking {
        withTempWav(numFrames = 2_048) { path ->
            val expected = AudioFileReader(path).let { reader ->
                try {
                    reader.readAll()
                } finally {
                    reader.close()
                }
            }
            val reader = AudioFileReader(path)
            try {
                val chunks = reader.asFlow(chunkSize = 256).toList()
                assertTrue(chunks.isNotEmpty())
                val concatenated = FloatArray(chunks.sumOf { it.size })
                var offset = 0
                for (chunk in chunks) {
                    chunk.copyInto(concatenated, offset)
                    offset += chunk.size
                }
                assertEquals(expected.size, concatenated.size)
                assertEquals(expected.toList(), concatenated.toList())
                assertTrue(reader.isAtEnd)
            } finally {
                reader.close()
            }
        }
    }

    @Test
    fun asFlowCancelStopsAtChunkBoundary() = runBlocking {
        withTempWav(numFrames = 8_192) { path ->
            val reader = AudioFileReader(path)
            try {
                val collected = mutableListOf<FloatArray>()
                val job = launch {
                    reader.asFlow(chunkSize = 64).collect {
                        collected.add(it)
                        delay(5)
                    }
                }
                withTimeout(2_000) {
                    while (collected.size < 2) yield()
                }
                job.cancelAndJoin()
                assertTrue(collected.size >= 2)
                assertTrue(collected.size < 8_192 / 64)
                assertTrue(job.isCancelled || job.isCompleted)
            } finally {
                reader.close()
            }
        }
    }

    @Test
    fun asFlowOnClosedReaderCompletesEmpty() = runBlocking {
        withTempWav(numFrames = 256) { path ->
            val reader = AudioFileReader(path)
            reader.close()
            val chunks = withTimeout(1_000) {
                reader.asFlow(chunkSize = 64).toList()
            }
            assertTrue(chunks.isEmpty())
            assertTrue(reader.isAtEnd)
        }
    }

    private inline fun withTempWav(numFrames: Int, block: (String) -> Unit) {
        val sampleRate = 16_000
        val samples = FloatArray(numFrames) { i ->
            sin(2.0 * PI * 440.0 * i / sampleRate).toFloat() * 0.25f
        }
        val file = File.createTempFile("klarinet-asflow-", ".wav")
        try {
            val writer = AudioFileWriter(
                filePath = file.absolutePath,
                format = AudioFileFormat.WAV,
                sampleRate = sampleRate,
                channelCount = 1,
            )
            try {
                writer.writeFrames(samples, numFrames)
            } finally {
                writer.close()
            }
            block(file.absolutePath)
        } finally {
            file.delete()
        }
    }
}
