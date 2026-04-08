package com.vectencia.klarinet

/**
 * Entry point for the Klarinet audio library.
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
         * Each call allocates platform-specific native resources, so you should
         * typically create a single engine per application and reuse it. Call
         * [release] when the engine is no longer needed.
         *
         * @return A new engine ready to open streams.
         */
        fun create(): AudioEngine
    }

    /**
     * Open a new audio stream with the given configuration.
     *
     * The returned stream is in the [StreamState.OPEN] state and must be
     * explicitly started via [AudioStream.start]. Multiple streams can be
     * open simultaneously (e.g., one for input and one for output).
     *
     * ```kotlin
     * val stream = engine.openStream(
     *     config = AudioStreamConfig(sampleRate = 48000, channelCount = 2),
     *     callback = object : AudioStreamCallback {
     *         override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
     *             // Fill buffer with audio data
     *             return numFrames
     *         }
     *     },
     * )
     * ```
     *
     * @param config Stream configuration. See [AudioStreamConfig] for defaults
     *   and valid ranges.
     * @param callback Optional callback for real-time audio processing. If
     *   `null`, use [AudioStream.write] or [AudioStream.read] to exchange
     *   audio data manually (blocking I/O mode).
     * @return A new [AudioStream] in the [StreamState.OPEN] state.
     * @throws StreamCreationException if the stream could not be created,
     *   for example due to unsupported configuration or unavailable hardware.
     * @throws PermissionException if recording permission is required but not
     *   granted (input streams only).
     */
    fun openStream(config: AudioStreamConfig, callback: AudioStreamCallback? = null): AudioStream

    /**
     * List all available audio devices on the current platform.
     *
     * The returned list is a snapshot; it does not update automatically if
     * devices are connected or disconnected. Call this method again to
     * refresh the list.
     *
     * @return A list of [AudioDeviceInfo] for each available device, which
     *   may be empty if no devices are detected.
     */
    fun getAvailableDevices(): List<AudioDeviceInfo>

    /**
     * Get the default audio device for the given direction.
     *
     * @param direction Whether to get the default input ([StreamDirection.INPUT])
     *   or output ([StreamDirection.OUTPUT]) device.
     * @return The default [AudioDeviceInfo], or `null` if no device is
     *   available for the requested direction.
     */
    fun getDefaultDevice(direction: StreamDirection): AudioDeviceInfo?

    /**
     * Create a new audio effect of the given type.
     *
     * The returned effect is initially enabled and configured with default
     * parameters. Use [AudioEffect.setParameter] to adjust its settings,
     * then add it to an [AudioEffectChain] and attach the chain to an
     * [AudioStream] via [AudioStream.effectChain].
     *
     * ```kotlin
     * val reverb = engine.createEffect(AudioEffectType.REVERB)
     * reverb.setParameter(ReverbParams.DECAY_TIME, 1.5f)
     *
     * val chain = engine.createEffectChain()
     * chain.add(reverb)
     * stream.effectChain = chain
     * ```
     *
     * @param type The type of effect to create. See [AudioEffectType] for
     *   available effects.
     * @return A new [AudioEffect] instance. Call [AudioEffect.release] when
     *   the effect is no longer needed.
     * @throws StreamCreationException if the effect could not be created on
     *   the current platform.
     */
    fun createEffect(type: AudioEffectType): AudioEffect

    /**
     * Create a new empty effect chain for grouping and ordering effects.
     *
     * Effects are processed in the order they are added to the chain via
     * [AudioEffectChain.add]. Attach the chain to a stream by setting
     * [AudioStream.effectChain].
     *
     * @return A new empty [AudioEffectChain] instance. Call
     *   [AudioEffectChain.release] when the chain is no longer needed.
     * @throws StreamCreationException if the chain could not be created.
     */
    fun createEffectChain(): AudioEffectChain

    /**
     * Release all native resources held by this engine.
     *
     * After calling this method, the engine instance must not be used --
     * any subsequent calls will result in undefined behavior.
     *
     * **Important**: Close all streams and release all effects and effect
     * chains created by this engine *before* calling [release]. Failing
     * to do so may leak native resources.
     */
    fun release()
}
