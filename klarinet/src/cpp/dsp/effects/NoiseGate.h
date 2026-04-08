#pragma once
#include <atomic>
#include <cmath>
#include <cstdint>
#include "AudioEffect.h"
#include "primitives/EnvelopeFollower.h"

namespace klarinet {

/**
 * @brief Parameter IDs for the NoiseGate effect.
 */
namespace NoiseGateParams {
    /// @brief Gate threshold in decibels. ID = 0.
    ///
    /// Signals below this level are attenuated (gate closes).
    /// Range: -80.0 to 0.0 dB.
    /// Default: -40.0 dB.
    /// Units: decibels (dB).
    constexpr int32_t kThreshold = 0;

    /// @brief Attack time in milliseconds. ID = 1.
    ///
    /// How quickly the envelope follower reacts to rising signals.
    /// Range: 0.1 to 50.0 ms.
    /// Default: 1.0 ms.
    /// Units: milliseconds (ms).
    constexpr int32_t kAttackMs  = 1;

    /// @brief Release time in milliseconds. ID = 2.
    ///
    /// How quickly the envelope follower reacts to falling signals.
    /// Range: 5.0 to 500.0 ms.
    /// Default: 50.0 ms.
    /// Units: milliseconds (ms).
    constexpr int32_t kReleaseMs = 2;

    /// @brief Hold time in milliseconds. ID = 3.
    ///
    /// After the signal drops below the threshold, the gate remains
    /// open for this duration before starting to close. Prevents the
    /// gate from chattering on signals near the threshold.
    /// Range: 0.0 to 500.0 ms.
    /// Default: 10.0 ms.
    /// Units: milliseconds (ms).
    constexpr int32_t kHoldMs    = 3;
}

/**
 * @brief Noise gate with hold time and smooth close.
 *
 * Attenuates audio signals that fall below a given threshold, effectively
 * silencing noise and low-level signals while allowing louder signals to
 * pass through. Includes a hold timer to prevent rapid on/off switching
 * (chattering) and a smooth exponential close to avoid click artifacts.
 *
 * @par DSP Algorithm
 * For each frame:
 * 1. **Peak detection**: Find the maximum absolute sample across all channels.
 * 2. **Envelope following**: Smooth the peak with attack/release time constants.
 * 3. **Gate state machine**:
 *    - If `envLevel > threshLinear`: gate opens (`gateGain = 1.0`),
 *      hold counter is reset to `holdSamples`.
 *    - Else if `holdCounter > 0`: gate stays open, counter decrements.
 *    - Else: gate smoothly closes by multiplying `gateGain *= 0.999`.
 *      This exponential decay produces a smooth fade-out, reaching -60 dB
 *      in approximately 6900 samples (~144 ms at 48 kHz).
 * 4. **Apply**: Multiply all channels by `gateGain`.
 *
 * @par Smooth Close Behavior
 * The multiplicative factor of 0.999 per sample provides an exponential
 * decay that avoids abrupt silencing clicks. The rate is fixed and does
 * not depend on the release parameter (which controls the envelope follower
 * only).
 *
 * @par Mono/Stereo Handling
 * Peak detection is linked across all channels, and the same gate gain
 * is applied to all channels, preserving the stereo image.
 *
 * @par Thread Safety
 * All parameters are stored in `std::atomic<float>` and can be safely
 * updated from any thread.
 */
class NoiseGate : public AudioEffect {
public:
    /** @brief Constructs a NoiseGate with default parameters. */
    NoiseGate();

    /** @brief Default destructor. */
    ~NoiseGate() override = default;

    /**
     * @brief Processes the audio buffer, applying noise gating.
     *
     * Detects whether the signal level is above or below the threshold
     * and either passes the signal through or smoothly attenuates it.
     *
     * @param buffer      Interleaved audio buffer (frames x channels).
     * @param numFrames   Number of audio frames in the buffer.
     * @param channelCount Number of interleaved channels per frame.
     */
    void process(float* buffer, int32_t numFrames, int32_t channelCount) override;

    /**
     * @brief Sets a parameter value by ID.
     *
     * When setting kAttackMs or kReleaseMs, the envelope follower's time
     * constants are updated immediately. When setting kHoldMs, the hold
     * duration in samples is recalculated from the current sample rate.
     *
     * @param paramId  Parameter ID (see NoiseGateParams).
     * @param value    The parameter value.
     */
    void setParameter(int32_t paramId, float value) override;

    /**
     * @brief Gets a parameter value by ID.
     * @param paramId  Parameter ID (see NoiseGateParams).
     * @return The current parameter value, or 0.0f for unknown IDs.
     */
    float getParameter(int32_t paramId) const override;

    /**
     * @brief Prepares the noise gate for playback.
     *
     * Initializes the envelope follower with the current sample rate,
     * configures attack/release times, and converts the hold time from
     * milliseconds to samples.
     *
     * @param sampleRate    The audio sample rate in Hz.
     * @param channelCount  The number of audio channels.
     */
    void prepare(int32_t sampleRate, int32_t channelCount) override;

    /**
     * @brief Resets all internal state.
     *
     * Clears the envelope follower, sets the gate gain to 0 (closed),
     * and resets the hold counter.
     */
    void reset() override;

private:
    /// @brief Gate threshold in dB. Default: -40.0 dB.
    std::atomic<float> threshold_{-40.0f};

    /// @brief Envelope follower attack time in ms. Default: 1.0 ms.
    std::atomic<float> attackMs_{1.0f};

    /// @brief Envelope follower release time in ms. Default: 50.0 ms.
    std::atomic<float> releaseMs_{50.0f};

    /// @brief Hold time in ms (gate stays open after signal drops). Default: 10.0 ms.
    std::atomic<float> holdMs_{10.0f};

    /// @brief Envelope follower for smooth level tracking.
    EnvelopeFollower envFollower_;

    /// @brief Current gate gain multiplier [0.0, 1.0]. 0 = fully closed, 1 = fully open.
    float gateGain_ = 0.0f;

    /// @brief Number of samples remaining in the hold phase before the gate starts closing.
    int32_t holdCounter_ = 0;

    /// @brief Hold duration converted to samples (computed from holdMs_ and sampleRate_).
    int32_t holdSamples_ = 0;
};

} // namespace klarinet
