package com.vectencia.klarinet

/**
 * Callback interface for audio stream events.
 *
 * Implement this interface to receive callbacks from an [AudioStream].
 *
 * **Important**: [onAudioReady] is called on a high-priority audio thread.
 * Implementations must be real-time safe — avoid allocations, locks, I/O,
 * or any operation that may block. On Android this callback crosses a JNI
 * boundary, so keep work minimal to avoid adding latency. On Apple platforms,
 * the same real-time constraints apply via the Core Audio render callback.
 */
interface AudioStreamCallback {

    /**
     * Called when the audio stream needs data (output) or has data available (input).
     *
     * For output streams, fill [buffer] with [numFrames] frames of audio data.
     * For input streams, read [numFrames] frames from [buffer].
     *
     * **Warning**: This is called on a real-time audio thread. Do not allocate
     * memory, acquire locks, perform I/O, or call any blocking function.
     * On Android, each invocation crosses the JNI boundary — minimize work
     * to avoid added latency.
     *
     * @param buffer The audio buffer to read from or write to.
     * @param numFrames The number of frames requested or available.
     * @return The number of frames actually processed. Return [numFrames] for normal operation.
     */
    fun onAudioReady(buffer: FloatArray, numFrames: Int): Int = numFrames

    /**
     * Called when the stream's lifecycle state changes.
     *
     * @param stream The stream whose state changed.
     * @param state The new state.
     */
    fun onStreamStateChanged(stream: AudioStream, state: StreamState) {}

    /**
     * Called when an error occurs on the stream.
     *
     * @param stream The stream that encountered the error.
     * @param error The error that occurred.
     */
    fun onStreamError(stream: AudioStream, error: KlarinetException) {}

    /**
     * Called when an audio underrun (output) or overrun (input) is detected.
     *
     * @param stream The stream that experienced the underrun.
     * @param count The cumulative number of underruns detected.
     */
    fun onStreamUnderrun(stream: AudioStream, count: Int) {}
}
