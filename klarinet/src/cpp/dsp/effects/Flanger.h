#pragma once
#include <atomic>
#include <cstdint>
#include <vector>
#include "AudioEffect.h"
#include "primitives/LFO.h"
#include "primitives/CircularBuffer.h"

namespace klarinet {

/**
 * @brief Parameter IDs for the Flanger effect.
 */
namespace FlangerParams {
    /// @brief LFO rate in Hertz. ID = 0.
    ///
    /// Controls the speed of the delay time modulation sweep.
    /// Typical flanger rates are very slow (0.1 to 2 Hz).
    /// Range: 0.01 to 10.0 Hz.
    /// Default: 0.5 Hz.
    /// Units: Hertz (Hz).
    constexpr int32_t kRateHz    = 0;

    /// @brief Modulation depth. ID = 1.
    ///
    /// Controls how much the delay time varies. At depth = 0, there is
    /// no modulation. At depth = 1.0, the delay sweeps the full range
    /// of 0 to kModDelayMs (5 ms).
    /// Range: 0.0 to 1.0.
    /// Default: 0.7.
    /// Units: normalized.
    constexpr int32_t kDepth     = 1;

    /// @brief Feedback amount. ID = 2.
    ///
    /// Controls the intensity of the flanging effect. Positive values
    /// create resonant peaks (constructive interference), negative values
    /// create resonant notches. Higher absolute values produce a more
    /// pronounced, metallic character.
    /// Range: -1.0 to 1.0.
    /// Default: 0.5.
    /// Units: normalized.
    constexpr int32_t kFeedback  = 2;

    /// @brief Wet/dry mix ratio. ID = 3.
    ///
    /// Controls the balance between the original (dry) signal and the
    /// flanged (wet) signal. 0.0 = fully dry, 1.0 = fully wet.
    /// Range: 0.0 to 1.0.
    /// Default: 0.5.
    /// Units: normalized.
    constexpr int32_t kWetDryMix = 3;
}

/**
 * @brief Flanger effect using a short LFO-modulated delay with feedback.
 *
 * Creates a sweeping, metallic, jet-like sound by mixing the input signal
 * with a very short, time-varying delayed copy of itself. The feedback
 * path creates resonant comb-filter peaks that sweep up and down in
 * frequency as the LFO modulates the delay time.
 *
 * @par DSP Algorithm
 * For each frame:
 * 1. Advance the LFO and get the current sample [-1, +1].
 * 2. Compute the modulated delay time in samples:
 *    `delaySamples = depth * kModDelayMs * (lfo * 0.5 + 0.5) * sr / 1000`
 *    This sweeps from 0 to `depth * kModDelayMs` (up to 5 ms).
 * 3. For each channel: read the delayed sample, write `input + delayed * feedback`
 *    into the delay line.
 * 4. Mix: `output = dry * (1 - mix) + delayed * mix`
 *
 * @par Key Constants
 * - kMaxDelayMs (10 ms): Maximum delay capability of the effect.
 * - kModDelayMs (5 ms): The modulation range of the delay time. The LFO
 *   sweeps the delay from 0 to this value (scaled by depth).
 * - kMaxCapacityMs (20 ms): Circular buffer capacity with headroom.
 *
 * @par Difference from Chorus
 * The flanger uses a much shorter delay (0-5 ms vs 7-14 ms for chorus)
 * and adds feedback. The short delay creates comb-filter resonances whose
 * frequencies are audible harmonics, producing the characteristic metallic
 * sweep. Chorus uses no feedback and a longer delay, creating a smooth
 * doubling/thickening effect without resonance.
 *
 * @par Mono/Stereo Handling
 * One delay line and feedback state is allocated per channel during
 * prepare(). All channels share the same LFO phase.
 *
 * @par Thread Safety
 * All parameters are stored in `std::atomic<float>` and can be safely
 * updated from any thread.
 */
class Flanger : public AudioEffect {
public:
    /** @brief Constructs a Flanger effect with default parameters. */
    Flanger();

    /** @brief Default destructor. */
    ~Flanger() override = default;

    /**
     * @brief Processes the audio buffer, applying flanging modulation.
     *
     * For each frame, advances the LFO, computes the modulated delay time,
     * reads a delayed sample with feedback, and mixes dry and wet signals.
     *
     * @param buffer      Interleaved audio buffer (frames x channels).
     * @param numFrames   Number of audio frames in the buffer.
     * @param channelCount Number of interleaved channels per frame.
     */
    void process(float* buffer, int32_t numFrames, int32_t channelCount) override;

    /**
     * @brief Sets a parameter value by ID.
     *
     * When setting kRateHz, the LFO frequency is updated immediately.
     *
     * @param paramId  Parameter ID (see FlangerParams).
     * @param value    The parameter value.
     */
    void setParameter(int32_t paramId, float value) override;

    /**
     * @brief Gets a parameter value by ID.
     * @param paramId  Parameter ID (see FlangerParams).
     * @return The current parameter value, or 0.0f for unknown IDs.
     */
    float getParameter(int32_t paramId) const override;

    /**
     * @brief Prepares the flanger for playback.
     *
     * Allocates delay lines and feedback state (one per channel) with
     * capacity for kMaxCapacityMs of audio, and initializes the LFO.
     *
     * @param sampleRate    The audio sample rate in Hz.
     * @param channelCount  The number of audio channels.
     */
    void prepare(int32_t sampleRate, int32_t channelCount) override;

    /**
     * @brief Resets the flanger to default state.
     *
     * Clears delay lines, resets feedback state, resets the LFO phase,
     * and restores default parameter values.
     */
    void reset() override;

private:
    /// @brief Maximum delay time capability in milliseconds.
    static constexpr float kMaxDelayMs = 10.0f;

    /// @brief LFO modulation range in milliseconds (delay sweeps 0 to depth * kModDelayMs).
    static constexpr float kModDelayMs = 5.0f;

    /// @brief Maximum circular buffer capacity in milliseconds (with headroom).
    static constexpr float kMaxCapacityMs = 20.0f;

    /// @brief Low-frequency oscillator for delay time modulation.
    LFO lfo_;

    /// @brief One circular buffer delay line per audio channel.
    std::vector<CircularBuffer> delayLines_;

    /// @brief Per-channel feedback state for the delay line's feedback path.
    std::vector<float> feedbackState_;

    /// @brief LFO modulation rate in Hz. Default: 0.5 Hz.
    std::atomic<float> rateHz_{0.5f};

    /// @brief Modulation depth [0, 1]. Default: 0.7.
    std::atomic<float> depth_{0.7f};

    /// @brief Feedback amount [-1, 1]. Default: 0.5.
    std::atomic<float> feedback_{0.5f};

    /// @brief Wet/dry mix [0, 1]. Default: 0.5.
    std::atomic<float> wetDryMix_{0.5f};
};

} // namespace klarinet
