package com.vectencia.koboe

/**
 * Entry point for the Koboe audio library.
 *
 * [AudioEngine] is the main factory for creating audio streams and
 * querying available audio devices on the current platform.
 *
 * ## Usage
 *
 * ```kotlin
 * val engine = AudioEngine.create()
 * val stream = engine.openStream(
 *     config = AudioStreamConfig(sampleRate = 48000, channelCount = 2),
 *     callback = myCallback,
 * )
 * stream.start()
 * // ... audio is playing ...
 * stream.stop()
 * stream.close()
 * engine.release()
 * ```
 *
 * ## Platform Behavior
 *
 * - **Android**: Backed by Google Oboe (AAudio / OpenSL ES).
 * - **Apple**: Backed by AVAudioEngine / Core Audio.
 *
 * Always call [release] when the engine is no longer needed to free
 * native resources.
 */
expect class AudioEngine {

    companion object {
        /**
         * Create a new [AudioEngine] instance.
         *
         * @return A new engine ready to open streams.
         */
        fun create(): AudioEngine
    }

    /**
     * Open a new audio stream with the given configuration.
     *
     * @param config Stream configuration. See [AudioStreamConfig] for defaults.
     * @param callback Optional callback for real-time audio processing.
     * @return A new [AudioStream] in the [StreamState.OPEN] state.
     * @throws StreamCreationException if the stream could not be created.
     */
    fun openStream(config: AudioStreamConfig, callback: AudioStreamCallback? = null): AudioStream

    /**
     * List all available audio devices on the current platform.
     *
     * @return A list of [AudioDeviceInfo] for each available device.
     */
    fun getAvailableDevices(): List<AudioDeviceInfo>

    /**
     * Get the default audio device for the given direction.
     *
     * @param direction Whether to get the default input or output device.
     * @return The default [AudioDeviceInfo], or null if none is available.
     */
    fun getDefaultDevice(direction: StreamDirection): AudioDeviceInfo?

    /**
     * Release all resources held by this engine.
     *
     * After calling this method, the engine instance must not be used.
     * Any open streams should be closed before releasing the engine.
     */
    fun release()
}
