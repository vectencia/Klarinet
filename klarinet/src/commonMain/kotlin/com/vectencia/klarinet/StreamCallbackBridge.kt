package com.vectencia.klarinet

/**
 * Named wrapper so JNI can call [notifyXrun] without an [AudioStream] jobject.
 * Also updates [AudioStream.peakLevel] from [onAudioReady].
 */
internal class StreamCallbackBridge(
    private val stream: AudioStream,
    private val delegate: AudioStreamCallback,
) : AudioStreamCallback {

    override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
        val result = delegate.onAudioReady(buffer, numFrames)
        var peak = 0f
        for (i in buffer.indices) {
            val abs = if (buffer[i] >= 0f) buffer[i] else -buffer[i]
            if (abs > peak) peak = abs
        }
        stream.peakLevelAtomic.set(peak)
        return result
    }

    override fun onStreamStateChanged(stream: AudioStream, state: StreamState) {
        delegate.onStreamStateChanged(stream, state)
    }

    override fun onStreamError(stream: AudioStream, error: KlarinetException) {
        delegate.onStreamError(stream, error)
    }

    override fun onStreamUnderrun(stream: AudioStream, count: Int) {
        delegate.onStreamUnderrun(stream, count)
    }

    fun notifyXrun(count: Int) {
        delegate.onStreamUnderrun(stream, count)
    }
}
