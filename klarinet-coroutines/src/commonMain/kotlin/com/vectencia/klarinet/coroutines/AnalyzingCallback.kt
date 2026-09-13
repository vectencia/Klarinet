package com.vectencia.klarinet.coroutines

import com.vectencia.klarinet.AudioAnalysisResult
import com.vectencia.klarinet.AudioAnalyzer
import com.vectencia.klarinet.AudioStream
import com.vectencia.klarinet.AudioStreamCallback
import com.vectencia.klarinet.KlarinetException
import com.vectencia.klarinet.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AnalyzingCallback(
    private val analyzer: AudioAnalyzer,
    private val delegate: AudioStreamCallback? = null,
    scope: CoroutineScope,
) : AudioStreamCallback {

    private data class Pending(
        val samples: FloatArray,
        val numFrames: Int,
        val channelCount: Int,
    )

    private val mutex = Mutex()
    private var scratch = FloatArray(analyzer.fftSize * 8)
    private var pendingFrames = 0
    private var pendingChannels = 1
    private var pendingSamples = 0
    private val bufferChannel = Channel<Unit>(Channel.CONFLATED)

    private val _results = MutableSharedFlow<AudioAnalysisResult>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val results: SharedFlow<AudioAnalysisResult> = _results.asSharedFlow()

    init {
        scope.launch(Dispatchers.Default) {
            for (unit in bufferChannel) {
                val pending = mutex.withLock {
                    if (pendingSamples <= 0) {
                        null
                    } else {
                        Pending(
                            scratch.copyOf(pendingSamples),
                            pendingFrames,
                            pendingChannels,
                        )
                    }
                } ?: continue
                val result = analyzer.analyze(
                    pending.samples,
                    pending.numFrames,
                    pending.channelCount,
                )
                _results.emit(result)
            }
        }
    }

    /**
     * Copies interleaved frames into a reused scratch buffer. The analysis
     * worker snapshots that buffer on [Dispatchers.Default] so this callback
     * does not allocate except when the scratch must grow.
     */
    override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
        if (numFrames > 0 && buffer.isNotEmpty()) {
            val channelCount = (buffer.size / numFrames).coerceAtLeast(1)
            val framesToCopy = minOf(numFrames, analyzer.fftSize)
            val samplesToCopy = minOf(framesToCopy * channelCount, buffer.size)
            if (mutex.tryLock()) {
                try {
                    if (scratch.size < samplesToCopy) {
                        scratch = FloatArray(samplesToCopy)
                    }
                    buffer.copyInto(scratch, 0, 0, samplesToCopy)
                    pendingFrames = framesToCopy
                    pendingChannels = channelCount
                    pendingSamples = samplesToCopy
                } finally {
                    mutex.unlock()
                }
                bufferChannel.trySend(Unit)
            }
        }
        return delegate?.onAudioReady(buffer, numFrames) ?: numFrames
    }

    override fun onStreamStateChanged(stream: AudioStream, state: StreamState) {
        if (state == StreamState.CLOSED) {
            close()
        }
        delegate?.onStreamStateChanged(stream, state)
    }

    override fun onStreamError(stream: AudioStream, error: KlarinetException) {
        delegate?.onStreamError(stream, error)
    }

    override fun onStreamUnderrun(stream: AudioStream, count: Int) {
        delegate?.onStreamUnderrun(stream, count)
    }

    /** Stops the analysis worker. Safe to call more than once. */
    fun close() {
        bufferChannel.close()
    }
}
