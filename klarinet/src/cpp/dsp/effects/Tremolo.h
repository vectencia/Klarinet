#pragma once
#include <atomic>
#include <cstdint>
#include "AudioEffect.h"
#include "primitives/LFO.h"

namespace klarinet {

/**
 * @brief Parameter IDs for the Tremolo effect.
 */
namespace TremoloParams {
    /// @brief LFO rate in Hertz. ID = 0.
    ///
    /// Controls the speed of the amplitude modulation. Typical tremolo
    /// rates are moderate (2 to 10 Hz).
    /// Range: 0.1 to 20.0 Hz.
    /// Default: 5.0 Hz.
    /// Units: Hertz (Hz).
    constexpr int32_t kRateHz = 0;

    /// @brief Modulation depth. ID = 1.
    ///
    /// Controls the intensity of the volume variation. At depth = 0,
    /// the signal passes through unchanged. At depth = 1.0, the volume
    /// swings from silence to full volume.
    /// Range: 0.0 to 1.0.
    /// Default: 0.5.
    /// Units: normalized.
    constexpr int32_t kDepth  = 1;
}

/**
 * @brief Tremolo effect using LFO amplitude modulation.
 *
 * Produces a periodic volume fluctuation by multiplying the audio signal
 * by a gain that oscillates between a minimum and maximum value. This is
 * the simplest modulation effect -- it only varies amplitude, not pitch
 * or timbre.
 *
 * @par DSP Algorithm
 * For each frame:
 * 1. Advance the LFO and get the current sample [-1, +1].
 * 2. Compute the modulation gain:
 *    `mod = (1 - depth) + depth * (lfo * 0.5 + 0.5)`
 *
 *    Breaking this formula down:
 *    - `(lfo * 0.5 + 0.5)` maps the LFO from [-1, +1] to [0, 1].
 *    - `depth * (lfo * 0.5 + 0.5)` scales this by the depth.
 *    - `(1 - depth) + ...` adds a DC offset so that the minimum gain
 *      is `(1 - depth)` instead of 0.
 *
 *    Result: `mod` oscillates between `(1 - depth)` and `1.0`.
 *    - At depth = 0: mod is always 1.0 (no effect).
 *    - At depth = 0.5: mod oscillates between 0.5 and 1.0.
 *    - At depth = 1.0: mod oscillates between 0.0 and 1.0 (full tremolo).
 *
 * 3. Multiply all channels in this frame by `mod`.
 *
 * @par Mono/Stereo Handling
 * The same modulation gain is applied to all channels in a frame,
 * so the effect works identically for mono, stereo, or any channel count.
 *
 * @par Thread Safety
 * All parameters are stored in `std::atomic<float>` and can be safely
 * updated from any thread.
 */
class Tremolo : public AudioEffect {
public:
    /** @brief Constructs a Tremolo effect with default parameters. */
    Tremolo();

    /** @brief Default destructor. */
    ~Tremolo() override = default;

    /**
     * @brief Processes the audio buffer, applying amplitude modulation.
     *
     * For each frame, advances the LFO, computes the modulation gain,
     * and multiplies all channels by that gain.
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
     * @param paramId  Parameter ID (see TremoloParams).
     * @param value    The parameter value.
     */
    void setParameter(int32_t paramId, float value) override;

    /**
     * @brief Gets a parameter value by ID.
     * @param paramId  Parameter ID (see TremoloParams).
     * @return The current parameter value, or 0.0f for unknown IDs.
     */
    float getParameter(int32_t paramId) const override;

    /**
     * @brief Prepares the tremolo for playback.
     *
     * Initializes the LFO with the current sample rate and rate parameter.
     *
     * @param sampleRate    The audio sample rate in Hz.
     * @param channelCount  The number of audio channels.
     */
    void prepare(int32_t sampleRate, int32_t channelCount) override;

    /**
     * @brief Resets the tremolo to default state.
     *
     * Resets the LFO phase and restores default parameter values.
     */
    void reset() override;

private:
    /// @brief Low-frequency oscillator for amplitude modulation.
    LFO lfo_;

    /// @brief LFO modulation rate in Hz. Default: 5.0 Hz.
    std::atomic<float> rateHz_{5.0f};

    /// @brief Modulation depth [0, 1]. Default: 0.5.
    std::atomic<float> depth_{0.5f};
};

} // namespace klarinet
