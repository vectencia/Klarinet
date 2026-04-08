/**
 * @file AudioEffect.h
 * @brief Base class for all Klarinet DSP audio effects.
 *
 * Defines the abstract AudioEffect interface and the EffectType enumeration.
 * Every concrete effect (Gain, Reverb, Compressor, etc.) inherits from AudioEffect
 * and implements its pure-virtual methods.
 *
 * This header is the C++ side of the Klarinet KMP expect/actual pattern: Kotlin
 * code declares an `expect` AudioEffect class, and the platform-specific `actual`
 * implementations delegate to these native C++ classes via JNI (Android) or
 * cinterop (Apple). All DSP runs natively; Kotlin is the control plane only.
 *
 * @note Threading contract:
 *   - process()       is called on the **audio thread** and must be real-time safe.
 *   - setParameter()  may be called from **any thread** (typically the control/UI thread).
 *   - getParameter()  may be called from **any thread**.
 *   - prepare()       is called on the **control thread** before audio starts.
 *   - reset()         is called on the **control thread**.
 *   - setEnabled() / isEnabled() use relaxed atomics and are safe from any thread.
 */
#pragma once
#include <atomic>
#include <cstdint>

namespace klarinet {

/**
 * @enum EffectType
 * @brief Identifies every built-in effect that the EffectFactory can instantiate.
 *
 * The integer values are stable and are used across the JNI / cinterop boundary,
 * so new entries must be appended before Count and existing values must not change.
 */
enum class EffectType : int32_t {
    Gain = 0,        ///< Simple volume gain (linear or dB).
    Pan,             ///< Stereo panning (equal-power or linear law).
    MuteSolo,        ///< Mute and solo toggling.

    Compressor,      ///< Dynamic range compressor (threshold, ratio, attack, release).
    Limiter,         ///< Brick-wall limiter (fast-attack compressor variant).
    NoiseGate,       ///< Noise gate (expander below threshold).

    ParametricEQ,    ///< Parametric equalizer (peaking / shelving bands).
    LowPassFilter,   ///< Low-pass biquad filter.
    HighPassFilter,  ///< High-pass biquad filter.
    BandPassFilter,  ///< Band-pass biquad filter.

    Delay,           ///< Delay / echo effect with feedback.
    Reverb,          ///< Algorithmic reverb.

    Chorus,          ///< Chorus modulation effect.
    Flanger,         ///< Flanger modulation effect.
    Phaser,          ///< Phaser (all-pass sweep) modulation effect.
    Tremolo,         ///< Tremolo (amplitude modulation) effect.

    Count,           ///< Sentinel — total number of effect types. Not a valid effect.
};

/**
 * @class AudioEffect
 * @brief Abstract base class for all audio effects in the Klarinet DSP engine.
 *
 * Subclasses implement the five pure-virtual methods to provide their specific
 * DSP processing. Effects are created through EffectFactory::create() and are
 * typically owned by an EffectChain, which calls process() on the audio thread.
 *
 * The enabled/disabled state is managed via a std::atomic<bool> so that the
 * control thread can toggle effects without synchronization with the audio thread.
 */
class AudioEffect {
public:
    /**
     * @brief Construct an AudioEffect of the given type.
     * @param type The EffectType tag identifying this effect.
     */
    explicit AudioEffect(EffectType type) : type_(type) {}

    /// Virtual destructor for safe polymorphic deletion.
    virtual ~AudioEffect() = default;

    /**
     * @brief Process audio samples in-place.
     *
     * Called on the **audio thread** — must be real-time safe (no allocation,
     * no locks, no system calls).
     *
     * @param buffer      Interleaved audio samples [frame0_ch0, frame0_ch1, ...].
     * @param numFrames   Number of audio frames in the buffer.
     * @param channelCount Number of interleaved channels per frame.
     */
    virtual void process(float* buffer, int32_t numFrames, int32_t channelCount) = 0;

    /**
     * @brief Set an effect-specific parameter by ID.
     *
     * May be called from **any thread**. Each concrete effect defines its own
     * parameter IDs (typically as an enum).
     *
     * @param paramId Effect-specific parameter identifier.
     * @param value   New parameter value.
     */
    virtual void setParameter(int32_t paramId, float value) = 0;

    /**
     * @brief Retrieve the current value of an effect-specific parameter.
     *
     * May be called from **any thread**.
     *
     * @param paramId Effect-specific parameter identifier.
     * @return Current parameter value, or 0.0f if paramId is unknown.
     */
    virtual float getParameter(int32_t paramId) const = 0;

    /**
     * @brief Prepare the effect for playback at a given sample rate and channel count.
     *
     * Called on the **control thread** before audio processing begins, or when
     * the audio format changes. Implementations should pre-compute any
     * sample-rate-dependent coefficients here.
     *
     * @param sampleRate   Audio sample rate in Hz (e.g., 44100, 48000).
     * @param channelCount Number of audio channels (1 = mono, 2 = stereo).
     */
    virtual void prepare(int32_t sampleRate, int32_t channelCount) = 0;

    /**
     * @brief Reset all internal state (delay lines, filter history, etc.) to zero.
     *
     * Called on the **control thread**. Useful when seeking or switching audio sources.
     */
    virtual void reset() = 0;

    /**
     * @brief Enable or disable this effect.
     *
     * When disabled, the EffectChain skips this effect's process() call.
     * Uses relaxed memory ordering — safe to call from any thread.
     *
     * @param enabled true to enable, false to bypass.
     */
    void setEnabled(bool enabled) { enabled_.store(enabled, std::memory_order_relaxed); }

    /**
     * @brief Query whether this effect is currently enabled.
     * @return true if enabled, false if bypassed.
     */
    bool isEnabled() const { return enabled_.load(std::memory_order_relaxed); }

    /**
     * @brief Get the EffectType tag for this effect.
     * @return The EffectType passed at construction time.
     */
    EffectType getType() const { return type_; }

protected:
    int32_t sampleRate_ = 48000;   ///< Current sample rate in Hz (set by prepare()).
    int32_t channelCount_ = 1;     ///< Current channel count (set by prepare()).

private:
    EffectType type_;              ///< Immutable type tag set at construction.
    std::atomic<bool> enabled_{true}; ///< Atomic enabled flag — relaxed ordering is sufficient.
};

} // namespace klarinet
