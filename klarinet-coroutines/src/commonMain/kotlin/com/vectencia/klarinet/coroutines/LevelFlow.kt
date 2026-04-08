package com.vectencia.klarinet.coroutines

import com.vectencia.klarinet.AudioStream
import com.vectencia.klarinet.StreamState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Emits the peak audio level at a regular interval.
 *
 * Reads the peak amplitude (0.0 to 1.0) computed by the audio callback
 * and emits it at the configured interval. The flow completes when the
 * stream is no longer started.
 *
 * @param intervalMs Emission interval in milliseconds. Default is 50ms (20 updates/sec).
 */
fun AudioStream.levelFlow(intervalMs: Long = 50L): Flow<Float> = flow {
    while (state == StreamState.STARTED || state == StreamState.STARTING) {
        emit(peakLevel)
        delay(intervalMs)
    }
    emit(0f)
}.flowOn(Dispatchers.Default)
