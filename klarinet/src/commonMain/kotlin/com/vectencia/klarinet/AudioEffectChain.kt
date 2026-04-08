package com.vectencia.klarinet

/**
 * An ordered chain of [AudioEffect] instances that are applied sequentially to an audio stream.
 *
 * Effects in the chain are processed in the order they were added: audio flows through
 * the first effect, then the second, and so on. The chain can be attached to an
 * [AudioStream] via its `effectChain` property, and effects can be added or removed
 * at any time (hot-swapped) without stopping the stream.
 *
 * Create an [AudioEffectChain] through [AudioEngine.createEffectChain].
 *
 * **Example usage:**
 * ```kotlin
 * val engine = AudioEngine()
 * val chain = engine.createEffectChain()
 *
 * // Create and configure effects
 * val eq = engine.createEffect(AudioEffectType.PARAMETRIC_EQ)
 * val compressor = engine.createEffect(AudioEffectType.COMPRESSOR)
 * val reverb = engine.createEffect(AudioEffectType.REVERB)
 *
 * // Add effects in processing order: EQ -> Compressor -> Reverb
 * chain.add(eq)
 * chain.add(compressor)
 * chain.add(reverb)
 *
 * // Attach the chain to a stream
 * stream.effectChain = chain
 *
 * // Hot-swap: remove an effect while the stream is running
 * chain.remove(reverb)
 *
 * // Apply multiple parameter changes atomically
 * chain.applyBatch(listOf(
 *     ParameterChange(compressor, CompressorParams.THRESHOLD, -12f),
 *     ParameterChange(compressor, CompressorParams.RATIO, 6f),
 * ))
 *
 * // Release all resources when done
 * chain.release()
 * ```
 *
 * @see AudioEffect
 * @see AudioEngine.createEffectChain
 * @see ParameterChange
 */
expect class AudioEffectChain {

    /**
     * Appends an effect to the end of this chain.
     *
     * The effect will be processed after all previously added effects. This method
     * is safe to call while the stream is running; the effect is inserted atomically.
     *
     * @param effect The [AudioEffect] to add. Must not already be in this chain.
     */
    fun add(effect: AudioEffect)

    /**
     * Removes an effect from this chain.
     *
     * If the effect is not in this chain, this method has no effect. This method
     * is safe to call while the stream is running; the effect is removed atomically.
     *
     * @param effect The [AudioEffect] to remove.
     */
    fun remove(effect: AudioEffect)

    /**
     * Applies multiple parameter changes atomically in a single batch.
     *
     * All changes are applied together before the next audio processing cycle,
     * which prevents audible glitches that could occur if parameters were changed
     * individually across multiple cycles.
     *
     * @param changes The list of [ParameterChange] entries to apply.
     * @see ParameterChange
     */
    fun applyBatch(changes: List<ParameterChange>)

    /**
     * Removes all effects from this chain.
     *
     * After calling this method, [effectCount] will be zero. The removed effects
     * are not released; call [AudioEffect.release] on each if they are no longer needed.
     */
    fun clear()

    /**
     * The number of effects currently in this chain.
     */
    val effectCount: Int

    /**
     * Releases the native resources associated with this chain.
     *
     * After calling this method, the chain must not be used. Detach the chain
     * from any [AudioStream] (by setting `stream.effectChain = null`) before releasing it.
     * Individual effects in the chain are not released automatically; call
     * [AudioEffect.release] on each separately if needed.
     */
    fun release()
}
