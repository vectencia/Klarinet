package com.vectencia.klarinet.coroutines

import com.vectencia.klarinet.AudioFileWriter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Writes audio frames on the IO dispatcher.
 *
 * Suspending wrapper around [AudioFileWriter.writeFrames]. Cancellation
 * is cooperative at the dispatcher boundary; once the native write
 * starts it runs to completion.
 *
 * @param data Interleaved PCM float samples in `[-1.0, 1.0]`.
 * @param numFrames Number of frames to write.
 * @param dispatcher Dispatcher for the blocking write. Defaults to the
 *   platform IO dispatcher ([Dispatchers.IO][kotlinx.coroutines.Dispatchers.IO]
 *   except JS, which uses [Dispatchers.Default][kotlinx.coroutines.Dispatchers.Default]).
 * @see AudioFileWriter.writeFrames
 * @see AudioFileReader.readAllSuspend
 */
suspend fun AudioFileWriter.writeFramesSuspend(
    data: FloatArray,
    numFrames: Int,
    dispatcher: CoroutineDispatcher = defaultIoDispatcher,
) {
    withContext(dispatcher) {
        writeFrames(data, numFrames)
    }
}
