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
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class AnalyzingCallback(
    private val analyzer: AudioAnalyzer,
    private val delegate: AudioStreamCallback? = null,
    scope: CoroutineScope,
) : AudioStreamCallback {

    private val bufferChannel = Channel<FloatArray>(Channel.CONFLATED)

    private val _results = MutableSharedFlow<AudioAnalysisResult>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val results: SharedFlow<AudioAnalysisResult> = _results.asSharedFlow()

    init {
        scope.launch(Dispatchers.Default) {
            for (buffer in bufferChannel) {
                val result = analyzer.analyze(buffer, buffer.size)
                _results.emit(result)
            }
        }
    }

    override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
        val framesToCopy = minOf(numFrames, analyzer.fftSize)
        val copy = FloatArray(framesToCopy)
        buffer.copyInto(copy, 0, 0, framesToCopy)
        bufferChannel.trySend(copy)

        return delegate?.onAudioReady(buffer, numFrames) ?: numFrames
    }

    override fun onStreamStateChanged(stream: AudioStream, state: StreamState) {
        delegate?.onStreamStateChanged(stream, state)
    }

    override fun onStreamError(stream: AudioStream, error: KlarinetException) {
        delegate?.onStreamError(stream, error)
    }

    override fun onStreamUnderrun(stream: AudioStream, count: Int) {
        delegate?.onStreamUnderrun(stream, count)
    }
}
