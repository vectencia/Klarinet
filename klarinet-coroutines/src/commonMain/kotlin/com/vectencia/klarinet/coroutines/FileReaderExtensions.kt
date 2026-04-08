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
 *
 * This is a suspending wrapper around [AudioFileReader.readAll] that
 * offloads the blocking I/O and decoding work to the specified
 * [dispatcher] (defaulting to [Dispatchers.IO][kotlinx.coroutines.Dispatchers.IO]).
 *
 * The entire decoded audio is loaded into memory at once. For large
 * files, consider using [asFlow] instead to process audio in chunks.
 *
 * ## Cancellation
 *
 * This function is cancellable at the dispatcher boundary. However,
 * once the native `readAll()` call begins, it runs to completion.
 *
 * @param dispatcher The [CoroutineDispatcher] to run the blocking read
 *   on. Defaults to [Dispatchers.IO][kotlinx.coroutines.Dispatchers.IO].
 * @return A [FloatArray] of interleaved PCM samples normalized to
 *   `[-1.0, 1.0]`.
 * @throws AudioFileException if a decoding error occurs.
 * @see AudioFileReader.readAll
 * @see asFlow
 */
suspend fun AudioFileReader.readAllSuspend(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
): FloatArray = withContext(dispatcher) {
    readAll()
}

/**
 * Reads up to [maxFrames] audio frames on the IO dispatcher.
 *
 * This is a suspending wrapper around [AudioFileReader.readFrames] that
 * offloads the blocking I/O and decoding work to the specified
 * [dispatcher] (defaulting to [Dispatchers.IO][kotlinx.coroutines.Dispatchers.IO]).
 *
 * @param maxFrames The maximum number of frames to read. The returned
 *   array may be shorter if the end of the file is reached.
 * @param dispatcher The [CoroutineDispatcher] to run the blocking read
 *   on. Defaults to [Dispatchers.IO][kotlinx.coroutines.Dispatchers.IO].
 * @return A [FloatArray] of interleaved PCM samples. The array length
 *   is at most `maxFrames * reader.info.channelCount`.
 * @throws AudioFileException if a decoding error occurs.
 * @see AudioFileReader.readFrames
 */
suspend fun AudioFileReader.readFramesSuspend(
    maxFrames: Int,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
): FloatArray = withContext(dispatcher) {
    readFrames(maxFrames)
}

/**
 * Streams decoded audio data as a [Flow] of chunks.
 *
 * Creates a cold [Flow] that reads audio frames from the reader in
 * chunks of [chunkSize] frames and emits each chunk as a [FloatArray].
 * The flow runs on the specified [dispatcher] (defaulting to
 * [Dispatchers.IO][kotlinx.coroutines.Dispatchers.IO]) and completes
 * when [AudioFileReader.isAtEnd] becomes `true`.
 *
 * This is the recommended approach for processing large audio files
 * without loading the entire file into memory.
 *
 * ## Usage
 *
 * ```kotlin
 * val reader = AudioFileReader("/path/to/large-file.wav")
 *
 * reader.asFlow(chunkSize = 8192).collect { chunk ->
 *     // Process each chunk of interleaved PCM samples
 *     processAudio(chunk)
 * }
 *
 * reader.close()
 * ```
 *
 * ## Cancellation
 *
 * The flow is cooperative with structured concurrency. Cancelling the
 * collecting coroutine stops reading at the next chunk boundary. The
 * caller is still responsible for calling [AudioFileReader.close] to
 * release native resources.
 *
 * @param chunkSize The number of frames to read per emission. Each
 *   emitted [FloatArray] contains up to `chunkSize * info.channelCount`
 *   samples. Defaults to 4096 frames.
 * @param dispatcher The [CoroutineDispatcher] to run the blocking reads
 *   on. Defaults to [Dispatchers.IO][kotlinx.coroutines.Dispatchers.IO].
 * @return A [Flow] of [FloatArray] chunks containing interleaved PCM
 *   samples.
 * @see readAllSuspend
 * @see readFramesSuspend
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
