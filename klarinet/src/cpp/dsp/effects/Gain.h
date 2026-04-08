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
 * @par DSP Algorithm
 * For each sample: `output = input * 10^(gainDb / 20)`
 *
 * @par Thread Safety
 * The gain parameter is stored in a `std::atomic<float>` and can be safely
 * updated from any thread while the audio thread is processing.
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
     * If the effect is disabled, the buffer is left untouched.
     *
     * @param buffer      Interleaved audio buffer (frames x channels).
     * @param numFrames   Number of audio frames in the buffer.
     * @param channelCount Number of interleaved channels per frame.
     */
    void process(float* buffer, int32_t numFrames, int32_t channelCount) override;

    /**
     * @brief Sets a parameter value by ID.
     * @param paramId  Parameter ID (use GainParams::kGainDb).
     * @param value    The gain value in decibels.
     */
    void setParameter(int32_t paramId, float value) override;

    /**
     * @brief Gets a parameter value by ID.
     * @param paramId  Parameter ID (use GainParams::kGainDb).
     * @return The current gain value in decibels, or 0.0f for unknown IDs.
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
    /// @brief Current gain in decibels. Default: 0.0 dB (unity gain).
    std::atomic<float> gainDb_{0.0f};
};

} // namespace klarinet
