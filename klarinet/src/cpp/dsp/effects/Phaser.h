#pragma once
#include <atomic>
#include <cstdint>
#include <vector>
#include <array>
#include "AudioEffect.h"
#include "primitives/LFO.h"
#include "primitives/Biquad.h"

namespace klarinet {

/**
 * @brief Parameter IDs for the Phaser effect.
 */
namespace PhaserParams {
    /// @brief LFO rate in Hertz. ID = 0.
    ///
    /// Controls the speed of the phaser's frequency sweep.
    /// Typical phaser rates are very slow (0.1 to 2 Hz).
    /// Range: 0.01 to 10.0 Hz.
    /// Default: 0.5 Hz.
    /// Units: Hertz (Hz).
    constexpr int32_t kRateHz   = 0;

    /// @brief Modulation depth. ID = 1.
    ///
    /// Controls how wide the frequency sweep is. At depth = 0, the
    /// allpass center frequency is fixed at kMinFreqHz. At depth = 1.0,
    /// it sweeps the full range from kMinFreqHz to kMaxFreqHz.
    /// Range: 0.0 to 1.0.
    /// Default: 0.5.
    /// Units: normalized.
    constexpr int32_t kDepth    = 1;

    /// @brief Number of allpass filter stages. ID = 2.
    ///
    /// More stages create more notches in the frequency response,
    /// producing a more complex, "swirly" phaser sound.
    /// Range: 1 to kMaxStages (8), stored as float, cast to int.
    /// Default: 4 stages.
    /// Units: count (integer).
    constexpr int32_t kStages   = 2;

    /// @brief Feedback amount. ID = 3.
    ///
    /// Routes the allpass chain output back to the input, deepening the
    /// notches in the frequency response for a more intense effect.
    /// Range: -1.0 to 1.0.
    /// Default: 0.3.
    /// Units: normalized.
    constexpr int32_t kFeedback = 3;
}

/**
 * @brief Phaser effect using cascaded allpass filters with LFO modulation.
 *
 * Creates a sweeping, spacey sound by passing the signal through a chain
 * of allpass filters whose center frequency is modulated by an LFO. The
 * allpass filters shift the phase of different frequencies by different
 * amounts. When mixed with the dry signal, this creates moving notches
 * and peaks in the frequency response (phase cancellation and reinforcement).
 *
 * @par DSP Algorithm
 * For each frame:
 * 1. Advance the LFO and get the current sample [-1, +1].
 * 2. Map the LFO to the center frequency for the allpass filters:
 *    `lfoUnipolar = lfo * 0.5 + 0.5`
 *    `centerFreq = kMinFreqHz + (kMaxFreqHz - kMinFreqHz) * lfoUnipolar * depth`
 *    This sweeps from 200 Hz to up to 4000 Hz.
 * 3. For each channel: add the feedback-scaled previous allpass output to
 *    the input, then process through `stages` cascaded allpass filters.
 * 4. Store the allpass output as feedback state.
 * 5. Output = input (with feedback) + allpassOutput (direct mix, no wet/dry control).
 *
 * @par Frequency Sweep Range
 * - kMinFreqHz (200 Hz): Lower bound of the allpass center frequency sweep.
 * - kMaxFreqHz (4000 Hz): Upper bound of the allpass center frequency sweep.
 * - The depth parameter scales how much of this range is actually used.
 *   At depth = 0.5, the sweep covers 200 Hz to 2100 Hz.
 *
 * @par Allpass Filters
 * Each stage is a second-order allpass biquad (BiquadType::AllPass) with
 * Q = 0.707. The allpass filter passes all frequencies at unity gain but
 * shifts their phase. When the phase-shifted signal is added to the
 * original, certain frequencies cancel (notches) and others reinforce
 * (peaks). The LFO sweeps these notches through the spectrum.
 *
 * Note: The allpass coefficients are recomputed every sample (per frame)
 * since the center frequency changes continuously with the LFO. This
 * is computationally more expensive than other effects.
 *
 * @par Mono/Stereo Handling
 * Allpass filter arrays and feedback state are allocated per channel,
 * ensuring independent processing. All channels share the same LFO phase.
 *
 * @par Thread Safety
 * All parameters are stored in `std::atomic<float>` and can be safely
 * updated from any thread.
 */
class Phaser : public AudioEffect {
public:
    /** @brief Constructs a Phaser effect with default parameters. */
    Phaser();

    /** @brief Default destructor. */
    ~Phaser() override = default;

    /**
     * @brief Processes the audio buffer through the phaser allpass chain.
     *
     * For each frame, advances the LFO, configures the allpass filters at
     * the modulated frequency, processes through the allpass chain with
     * feedback, and sums with the input for the output.
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
     * @param paramId  Parameter ID (see PhaserParams).
     * @param value    The parameter value.
     */
    void setParameter(int32_t paramId, float value) override;

    /**
     * @brief Gets a parameter value by ID.
     * @param paramId  Parameter ID (see PhaserParams).
     * @return The current parameter value, or 0.0f for unknown IDs.
     */
    float getParameter(int32_t paramId) const override;

    /**
     * @brief Prepares the phaser for playback.
     *
     * Allocates allpass filter arrays and feedback state (one set per
     * channel), resets all biquad states, and initializes the LFO.
     *
     * @param sampleRate    The audio sample rate in Hz.
     * @param channelCount  The number of audio channels.
     */
    void prepare(int32_t sampleRate, int32_t channelCount) override;

    /**
     * @brief Resets the phaser to default state.
     *
     * Clears all allpass filter state, resets feedback state, resets the
     * LFO phase, and restores default parameter values.
     */
    void reset() override;

private:
    /// @brief Maximum number of allpass stages supported.
    static constexpr int32_t kMaxStages = 8;

    /// @brief Lower bound of the allpass center frequency sweep in Hz.
    static constexpr float kMinFreqHz = 200.0f;

    /// @brief Upper bound of the allpass center frequency sweep in Hz.
    static constexpr float kMaxFreqHz = 4000.0f;

    /// @brief Low-frequency oscillator for allpass frequency modulation.
    LFO lfo_;

    /// @brief Cascaded allpass filters: allpasses_[channel][stage].
    /// Up to kMaxStages (8) allpass stages per channel.
    std::vector<std::array<Biquad, kMaxStages>> allpasses_;

    /// @brief Per-channel feedback state (stores previous allpass chain output).
    std::vector<float> feedbackState_;

    /// @brief LFO modulation rate in Hz. Default: 0.5 Hz.
    std::atomic<float> rateHz_{0.5f};

    /// @brief Modulation depth [0, 1]. Default: 0.5.
    std::atomic<float> depth_{0.5f};

    /// @brief Number of allpass stages (as float, cast to int). Default: 4.
    std::atomic<float> stages_{4.0f};

    /// @brief Feedback amount [-1, 1]. Default: 0.3.
    std::atomic<float> feedback_{0.3f};
};

} // namespace klarinet
