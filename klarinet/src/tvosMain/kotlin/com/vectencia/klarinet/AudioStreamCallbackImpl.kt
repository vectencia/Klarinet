package com.vectencia.klarinet

class AudioStreamCallbackImpl(
    private val onAudioReadyBlock: (buffer: FloatArray, numFrames: Int) -> Int,
) : AudioStreamCallback {
    var onUnderrun: ((AudioStream, Int) -> Unit)? = null

    override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
        return onAudioReadyBlock(buffer, numFrames)
    }

    override fun onStreamUnderrun(stream: AudioStream, count: Int) {
        onUnderrun?.invoke(stream, count)
    }
}
