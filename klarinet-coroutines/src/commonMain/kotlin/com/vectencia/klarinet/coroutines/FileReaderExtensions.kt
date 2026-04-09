package com.vectencia.klarinet.coroutines

import com.vectencia.klarinet.AudioFileReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Reads and decodes all remaining audio frames on the IO dispatcher.
 */
suspend fun AudioFileReader.readAllSuspend(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
): FloatArray = withContext(dispatcher) {
    readAll()
}

/**
 * Reads up to [maxFrames] audio frames on the IO dispatcher.
 */
suspend fun AudioFileReader.readFramesSuspend(
    maxFrames: Int,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
): FloatArray = withContext(dispatcher) {
    readFrames(maxFrames)
}

/**
 * Streams decoded audio data as a Flow of chunks.
 */
fun AudioFileReader.asFlow(
    chunkSize: Int = 4096,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
): Flow<FloatArray> = flow {
    while (!isAtEnd) {
        val chunk = readFrames(chunkSize)
        if (chunk.isNotEmpty()) {
            emit(chunk)
        }
    }
}.flowOn(dispatcher)
