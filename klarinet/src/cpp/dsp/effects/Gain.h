#pragma once
#include <atomic>
#include <cmath>
#include <cstdint>
#include "AudioEffect.h"

namespace klarinet {

/**
 * @brief Parameter IDs for the Gain effect.
 */
namespace GainParams {
    /// @brief Gain amount in decibels (dB). ID = 0.
    ///
    /// Range: any float (negative attenuates, positive amplifies).
    /// Default: 0.0 dB (unity gain).
    /// Units: decibels (dB).
    constexpr int32_t kGainDb = 0;

    /// @brief Duration of a gain change in milliseconds. ID = 1.
    ///
    /// `0` (default) applies [kGainDb] instantly, matching historical
    /// behaviour. Values greater than `0` ramp linear amplitude from the
    /// currently applied gain to the new target over that duration.
    /// A change to [kGainDb] or [kFadeMs] while a ramp is running
    /// retargets from the current applied amplitude (no click).
    /// Units: milliseconds.
    constexpr int32_t kFadeMs = 1;
}

/**
 * @brief Simple linear gain effect.
 *
 * Applies a volume change to all samples in the audio buffer by converting
 * a dB value to a linear multiplier using the standard formula:
 *
 *     linear = 10^(dB / 20)
 *
 * At 0 dB the signal passes through unchanged. Positive values amplify,
 * negative values attenuate. For example, +6 dB approximately doubles
 * the amplitude, while -6 dB approximately halves it.
 *
 * The effect processes every sample identically regardless of channel count,
 * so it works transparently for mono, stereo, or any multi-channel layout.
 *
 * When [GainParams::kFadeMs] is `0`, the target dB is applied immediately.
 * When it is greater than `0`, linear amplitude is interpolated per sample
 * from the currently applied gain to the new target. Retargeting a running
 * fade starts from the current applied amplitude so the output stays
 * continuous.
 *
 * @par DSP Algorithm
 * For each sample: `output = input * linearGain`
 * where `linearGain` is either `10^(gainDb / 20)` or a lerp toward that
 * value over `fadeMs` at the prepared sample rate.
 *
 * @par Thread Safety
 * Target parameters are `std::atomic` and may be updated from any thread.
 * Ramp state is touched only from `process()` on the audio thread.
 * `setParameter` does not allocate, lock, or otherwise block.
 */
class Gain : public AudioEffect {
public:
    /** @brief Constructs a Gain effect with default unity gain (0 dB). */
    Gain();

    /** @brief Default destructor. */
    ~Gain() override = default;

    /**
     * @brief Applies the gain to the audio buffer in-place.
     *
     * Converts the current dB value to a linear multiplier and multiplies
     * every sample (across all channels and frames) by that multiplier.
     * If a fade is active, the multiplier advances one step per frame.
     * If the effect is disabled, the buffer is left untouched.
     *
     * @param buffer      Interleaved audio buffer (frames x channels).
     * @param numFrames   Number of audio frames in the buffer.
     * @param channelCount Number of interleaved channels per frame.
     */
    void process(float* buffer, int32_t numFrames, int32_t channelCount) override;

    /**
     * @brief Sets a parameter value by ID.
     * @param paramId  Parameter ID (use GainParams::kGainDb or kFadeMs).
     * @param value    The gain value in decibels, or fade duration in ms.
     */
    void setParameter(int32_t paramId, float value) override;

    /**
     * @brief Gets a parameter value by ID.
     * @param paramId  Parameter ID (use GainParams::kGainDb or kFadeMs).
     * @return The current target gain in decibels, fade duration in ms,
     *   or 0.0f for unknown IDs.
     */
    float getParameter(int32_t paramId) const override;

    /**
     * @brief Prepares the effect for playback.
     * @param sampleRate    The audio sample rate in Hz.
     * @param channelCount  The number of audio channels.
     */
    void prepare(int32_t sampleRate, int32_t channelCount) override;

    /**
     * @brief Resets the gain parameter to its default value (0 dB).
     */
    void reset() override;

private:
    /// @brief Target gain in decibels. Default: 0.0 dB (unity gain).
    std::atomic<float> gainDb_{0.0f};
    /// @brief Fade duration in milliseconds. Default: 0 (instant).
    std::atomic<float> fadeMs_{0.0f};
    /// @brief Incremented on kGainDb changes so process() can start a new ramp.
    std::atomic<uint32_t> generation_{0};

    /// Audio-thread only: last applied linear amplitude.
    float currentLinear_{1.0f};
    /// Audio-thread only: linear amplitude at the start of the current ramp.
    float rampStartLinear_{1.0f};
    /// Audio-thread only: linear amplitude at the end of the current ramp.
    float rampTargetLinear_{1.0f};
    /// Audio-thread only: frames elapsed in the current ramp.
    int32_t rampPosition_{0};
    /// Audio-thread only: total frames in the current ramp; 0 means snap.
    int32_t rampLength_{0};
    /// Audio-thread only: last generation consumed by process().
    uint32_t seenGeneration_{0};
};

} // namespace klarinet
