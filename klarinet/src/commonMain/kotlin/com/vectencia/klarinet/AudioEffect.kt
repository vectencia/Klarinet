package com.vectencia.klarinet

/**
 * Represents a single audio effect that can be applied to an audio stream.
 *
 * An [AudioEffect] encapsulates a native DSP processor (e.g., gain, compressor, reverb)
 * and exposes a parameter-based API for real-time control. Effects are created through
 * [AudioEngine.createEffect] and are typically added to an [AudioEffectChain] before
 * being attached to an [AudioStream].
 *
 * Each effect has a fixed [type] that determines which parameters are available.
 * Use the corresponding parameter constants object (e.g., [CompressorParams], [ReverbParams])
 * to discover valid parameter IDs for a given effect type.
 *
 * **Example usage:**
 * ```kotlin
 * val engine = AudioEngine()
 * val compressor = engine.createEffect(AudioEffectType.COMPRESSOR)
 *
 * // Configure the compressor
 * compressor.setParameter(CompressorParams.THRESHOLD, -18f)
 * compressor.setParameter(CompressorParams.RATIO, 4f)
 * compressor.setParameter(CompressorParams.ATTACK_MS, 10f)
 * compressor.setParameter(CompressorParams.RELEASE_MS, 100f)
 *
 * // Bypass the effect without removing it from the chain
 * compressor.isEnabled = false
 *
 * // Re-enable it
 * compressor.isEnabled = true
 *
 * // Release native resources when done
 * compressor.release()
 * ```
 *
 * @see AudioEffectType
 * @see AudioEffectChain
 * @see AudioEngine.createEffect
 */
expect class AudioEffect {

    /**
     * The type of this effect, which determines its DSP behavior and available parameters.
     *
     * This property is immutable and set at creation time.
     */
    val type: AudioEffectType

    /**
     * Whether this effect is currently active in the processing chain.
     *
     * When `false`, the effect is bypassed and audio passes through unchanged.
     * This allows toggling effects on and off without removing them from the chain.
     * Defaults to `true` when the effect is created.
     */
    var isEnabled: Boolean

    /**
     * Sets a parameter on this effect.
     *
     * Parameter IDs are defined as constants in the corresponding params object
     * for this effect's [type] (e.g., [CompressorParams.THRESHOLD], [ReverbParams.ROOM_SIZE]).
     *
     * This method is safe to call from any thread and takes effect on the next
     * audio processing cycle.
     *
     * @param paramId The parameter identifier. Must be a valid ID for this effect's [type].
     * @param value The new value for the parameter. Valid ranges depend on the parameter;
     *   see the corresponding params object for details.
     * @see AudioEffectParams
     */
    fun setParameter(paramId: Int, value: Float)

    /**
     * Retrieves the current value of a parameter on this effect.
     *
     * @param paramId The parameter identifier. Must be a valid ID for this effect's [type].
     * @return The current value of the specified parameter.
     * @see setParameter
     */
    fun getParameter(paramId: Int): Float

    /**
     * Releases the native resources associated with this effect.
     *
     * After calling this method, the effect must not be used. Remove the effect
     * from any [AudioEffectChain] before releasing it.
     */
    fun release()
}
