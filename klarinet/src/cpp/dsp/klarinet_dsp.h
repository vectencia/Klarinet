/**
 * @file klarinet_dsp.h
 * @brief C API for the Klarinet DSP engine.
 *
 * This header exposes the entire Klarinet DSP engine as a flat C API so that it
 * can be consumed from Kotlin/Native (via cinterop), Swift, or any other
 * language with C FFI support.
 *
 * ## Handle pattern
 *
 * All objects are represented as opaque `void*` handles:
 *   - @c KlarinetEffectHandle      — wraps a klarinet::AudioEffect*
 *   - @c KlarinetEffectChainHandle  — wraps a klarinet::EffectChain*
 *
 * Handles are created with `klarinet_create_effect()` / `klarinet_chain_create()`
 * and must be destroyed with the corresponding `_destroy()` function to avoid
 * memory leaks.
 *
 * ## Global registry & thread safety
 *
 * Created effects are kept alive in a global registry (a mutex-protected
 * unordered_map of shared_ptr). This ensures that effect handles remain valid
 * even after being added to (and possibly removed from) an EffectChain. The
 * registry mutex is held only during create / destroy / chain_add operations,
 * never on the audio thread.
 *
 * ## Null-safety
 *
 * All functions accept NULL handles gracefully (no-op or return a sentinel).
 */
#pragma once

#ifdef __cplusplus
extern "C" {
#endif

/// Opaque handle to a single AudioEffect instance.
typedef void* KlarinetEffectHandle;

/// Opaque handle to an EffectChain instance.
typedef void* KlarinetEffectChainHandle;

// ---------------------------------------------------------------------------
// Effect lifecycle
// ---------------------------------------------------------------------------

/**
 * @brief Create an effect of the given type.
 *
 * The new effect is registered in a global registry that keeps it alive until
 * klarinet_effect_destroy() is called.
 *
 * @param effectType Integer value of klarinet::EffectType (e.g., 0 = Gain).
 * @return Handle to the new effect, or NULL if the type is invalid.
 */
KlarinetEffectHandle klarinet_create_effect(int effectType);

/**
 * @brief Set a parameter on an effect.
 *
 * Safe to call from any thread. The exact parameter IDs are defined by each
 * concrete effect class.
 *
 * @param handle  Effect handle.
 * @param paramId Effect-specific parameter identifier.
 * @param value   New parameter value.
 */
void klarinet_effect_set_parameter(KlarinetEffectHandle handle, int paramId, float value);

/**
 * @brief Get the current value of an effect parameter.
 *
 * @param handle  Effect handle.
 * @param paramId Effect-specific parameter identifier.
 * @return Current parameter value, or 0.0f if handle is NULL.
 */
float klarinet_effect_get_parameter(KlarinetEffectHandle handle, int paramId);

/**
 * @brief Enable or disable an effect.
 *
 * @param handle  Effect handle.
 * @param enabled Non-zero to enable, zero to disable (bypass).
 */
void klarinet_effect_set_enabled(KlarinetEffectHandle handle, int enabled);

/**
 * @brief Query whether an effect is enabled.
 *
 * @param handle Effect handle.
 * @return 1 if enabled, 0 if disabled or handle is NULL.
 */
int klarinet_effect_is_enabled(KlarinetEffectHandle handle);

/**
 * @brief Get the EffectType of an effect.
 *
 * @param handle Effect handle.
 * @return Integer value of klarinet::EffectType, or -1 if handle is NULL.
 */
int klarinet_effect_get_type(KlarinetEffectHandle handle);

/**
 * @brief Prepare an effect for playback at a given sample rate and channel count.
 *
 * Must be called before the effect processes audio. Safe to call from the
 * control thread.
 *
 * @param handle       Effect handle.
 * @param sampleRate   Sample rate in Hz.
 * @param channelCount Number of channels.
 */
void klarinet_effect_prepare(KlarinetEffectHandle handle, int sampleRate, int channelCount);

/**
 * @brief Reset all internal state of an effect (delay lines, filter history, etc.).
 *
 * @param handle Effect handle.
 */
void klarinet_effect_reset(KlarinetEffectHandle handle);

/**
 * @brief Destroy an effect and remove it from the global registry.
 *
 * After this call the handle is invalid and must not be used.
 *
 * @param handle Effect handle (NULL is a safe no-op).
 */
void klarinet_effect_destroy(KlarinetEffectHandle handle);

// ---------------------------------------------------------------------------
// EffectChain lifecycle
// ---------------------------------------------------------------------------

/**
 * @brief Create a new, empty EffectChain.
 *
 * @return Handle to the new chain.
 */
KlarinetEffectChainHandle klarinet_chain_create(void);

/**
 * @brief Add an effect to the end of a chain.
 *
 * The chain acquires shared ownership of the effect via the global registry.
 *
 * @param chain  Chain handle.
 * @param effect Effect handle.
 */
void klarinet_chain_add(KlarinetEffectChainHandle chain, KlarinetEffectHandle effect);

/**
 * @brief Remove an effect from a chain.
 *
 * The effect is not destroyed — it remains in the global registry until
 * klarinet_effect_destroy() is called.
 *
 * @param chain  Chain handle.
 * @param effect Effect handle.
 */
void klarinet_chain_remove(KlarinetEffectChainHandle chain, KlarinetEffectHandle effect);

/**
 * @brief Process a buffer of interleaved audio through all enabled effects in the chain.
 *
 * Called on the **audio thread** — must be real-time safe.
 *
 * @param chain        Chain handle.
 * @param buffer       Interleaved float samples (modified in-place).
 * @param numFrames    Number of audio frames.
 * @param channelCount Number of channels per frame.
 */
void klarinet_chain_process(KlarinetEffectChainHandle chain, float* buffer, int numFrames, int channelCount);

/**
 * @brief Prepare all effects in the chain for a new audio format.
 *
 * @param chain        Chain handle.
 * @param sampleRate   Sample rate in Hz.
 * @param channelCount Number of channels.
 */
void klarinet_chain_prepare(KlarinetEffectChainHandle chain, int sampleRate, int channelCount);

/**
 * @brief Remove all effects from the chain.
 *
 * @param chain Chain handle.
 */
void klarinet_chain_clear(KlarinetEffectChainHandle chain);

/**
 * @brief Get the number of effects in a chain.
 *
 * @param chain Chain handle.
 * @return Effect count, or 0 if the chain handle is NULL.
 */
int klarinet_chain_get_effect_count(KlarinetEffectChainHandle chain);

/**
 * @brief Enqueue a parameter change for lock-free delivery to the audio thread.
 *
 * The change is written into an SPSC ring buffer and applied at the start of
 * the next klarinet_chain_process() call. This is the preferred way to update
 * parameters from the control/UI thread without blocking audio.
 *
 * @param chain   Chain handle.
 * @param effect  Target effect handle.
 * @param paramId Parameter identifier.
 * @param value   New parameter value.
 */
void klarinet_chain_enqueue_param(KlarinetEffectChainHandle chain, KlarinetEffectHandle effect, int paramId, float value);

/**
 * @brief Destroy an EffectChain.
 *
 * After this call the chain handle is invalid. Effects that were in the chain
 * are not destroyed — they remain in the global registry.
 *
 * @param chain Chain handle (NULL is a safe no-op).
 */
void klarinet_chain_destroy(KlarinetEffectChainHandle chain);

#ifdef __cplusplus
}
#endif
