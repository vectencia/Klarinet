package com.vectencia.klarinet

/**
 * Direction of an audio stream, indicating whether it captures or plays audio.
 *
 * The direction is specified at stream creation time via
 * [AudioStreamConfig.direction] and cannot be changed afterwards.
 * It determines how the stream's [AudioStreamCallback.onAudioReady] callback
 * behaves and which of [AudioStream.read] or [AudioStream.write] is valid.
 *
 * @see AudioStreamConfig.direction
 * @see AudioEngine.getDefaultDevice
 */
enum class StreamDirection {
    /**
     * Playback (output) stream -- audio flows from the application to the
     * audio device (speaker, headphones, etc.).
     *
     * In [AudioStreamCallback.onAudioReady], the application should fill the
     * buffer with audio samples. Alternatively, use [AudioStream.write] to
     * push data outside of a callback.
     */
    OUTPUT,

    /**
     * Recording (input) stream -- audio flows from the audio device
     * (microphone, line-in, etc.) to the application.
     *
     * In [AudioStreamCallback.onAudioReady], the buffer already contains
     * captured audio samples for the application to consume. Alternatively,
     * use [AudioStream.read] to pull data outside of a callback.
     *
     * **Note**: On both Android and iOS, recording requires microphone
     * permission. A [PermissionException] will be thrown if the permission
     * has not been granted.
     */
    INPUT
}
