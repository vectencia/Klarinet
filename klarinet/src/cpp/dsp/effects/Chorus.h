#pragma once
#include <atomic>
#include <cstdint>
#include <vector>
#include "AudioEffect.h"
#include "primitives/LFO.h"
#include "primitives/CircularBuffer.h"

namespace klarinet {

/**
 * @brief Parameter IDs for the Chorus effect.
 */
namespace ChorusParams {
    /// @brief LFO rate in Hertz. ID = 0.
    ///
    /// Controls the speed of the delay time modulation. Typical chorus
    /// rates are slow (0.1 to 5 Hz).
    /// Range: 0.01 to 10.0 Hz.
    /// Default: 1.0 Hz.
    /// Units: Hertz (Hz).
    constexpr int32_t kRateHz    = 0;

    /// @brief Modulation depth. ID = 1.
    ///
    /// Controls how much the delay time varies around the base delay.
    /// At depth = 0, the delay is constant (no chorus effect). At
    /// depth = 1.0, the delay sweeps from kBaseDelayMs to 2 * kBaseDelayMs.
    /// Range: 0.0 to 1.0.
    /// Default: 0.5.
    /// Units: normalized.
    constexpr int32_t kDepth     = 1;

    /// @brief Wet/dry mix ratio. ID = 2.
    ///
    /// Controls the balance between the original (dry) signal and the
    /// chorus (wet) signal. 0.0 = fully dry, 1.0 = fully wet.
    /// Range: 0.0 to 1.0.
    /// Default: 0.5.
    /// Units: normalized.
    constexpr int32_t kWetDryMix = 2;
}

/**
 * @brief Chorus effect using an LFO-modulated delay line.
 *
 * Creates a thickening, doubling effect by mixing the dry signal with
 * a copy whose delay time is slowly modulated by a low-frequency
 * oscillator (LFO). The varying delay produces subtle pitch shifts
 * that simulate multiple instruments or voices playing in unison.
 *
 * @par DSP Algorithm
 * For each frame:
 * 1. Compute the LFO sample (sinusoidal, range [-1, +1]).
 * 2. Calculate the modulated delay time in samples:
 *    `delaySamples = (kBaseDelayMs + depth * kBaseDelayMs * (lfo * 0.5 + 0.5)) * sr / 1000`
 *    This sweeps from kBaseDelayMs to (1 + depth) * kBaseDelayMs.
 * 3. For each channel: write the dry sample to the delay line, read
 *    the delayed sample with linear interpolation.
 * 4. Mix: `output = dry * (1 - mix) + wet * mix`
 *
 * @par Key Constants
 * - kBaseDelayMs (7 ms): The center delay time. Typical for chorus effects,
 *   long enough to create detectable pitch modulation without obvious echo.
 * - kMaxCapacityMs (50 ms): Maximum circular buffer capacity, providing
 *   headroom for the modulated delay plus any overshoot.
 *
 * @par Difference from Flanger
 * Chorus uses a longer base delay (7 ms vs < 5 ms for flanger) and has
 * no feedback. This produces a smooth doubling effect rather than the
 * metallic, resonant sweeps characteristic of flanging.
 *
 * @par Mono/Stereo Handling
 * One delay line is allocated per channel during prepare(). All channels
 * share the same LFO phase, producing a mono-compatible chorus. For
 * stereo widening, an external stereo offset would be needed.
 *
 * @par Thread Safety
 * All parameters are stored in `std::atomic<float>` and can be safely
 * updated from any thread.
 */
class Chorus : public AudioEffect {
public:
    /** @brief Constructs a Chorus effect with default parameters. */
    Chorus();

    /** @brief Default destructor. */
    ~Chorus() override = default;

    /**
     * @brief Processes the audio buffer, applying chorus modulation.
     *
     * For each frame, advances the LFO, computes the modulated delay time,
     * and mixes the dry and delayed signals.
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
     * @param paramId  Parameter ID (see ChorusParams).
     * @param value    The parameter value.
     */
    void setParameter(int32_t paramId, float value) override;

    /**
     * @brief Gets a parameter value by ID.
     * @param paramId  Parameter ID (see ChorusParams).
     * @return The current parameter value, or 0.0f for unknown IDs.
     */
    float getParameter(int32_t paramId) const override;

    /**
     * @brief Prepares the chorus for playback.
     *
     * Allocates delay lines (one per channel) with capacity for
     * kMaxCapacityMs of audio, and initializes the LFO.
     *
     * @param sampleRate    The audio sample rate in Hz.
     * @param channelCount  The number of audio channels.
     */
    void prepare(int32_t sampleRate, int32_t channelCount) override;

    /**
     * @brief Resets the chorus to default state.
     *
     * Clears delay lines, resets the LFO phase, and restores default
     * parameter values.
     */
    void reset() override;

private:
    /// @brief Base (center) delay time in milliseconds for the chorus effect.
    static constexpr float kBaseDelayMs = 7.0f;

    /// @brief Maximum delay line capacity in milliseconds (headroom for modulation).
    static constexpr float kMaxCapacityMs = 50.0f;

    /// @brief Low-frequency oscillator for delay time modulation.
    LFO lfo_;

    /// @brief One circular buffer delay line per audio channel.
    std::vector<CircularBuffer> delayLines_;

    /// @brief LFO modulation rate in Hz. Default: 1.0 Hz.
    std::atomic<float> rateHz_{1.0f};

    /// @brief Modulation depth [0, 1]. Default: 0.5.
    std::atomic<float> depth_{0.5f};

    /// @brief Wet/dry mix [0, 1]. Default: 0.5.
    std::atomic<float> wetDryMix_{0.5f};
};

} // namespace klarinet
