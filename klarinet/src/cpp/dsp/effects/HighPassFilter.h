#pragma once
#include <atomic>
#include <cstdint>
#include <vector>
#include "AudioEffect.h"
#include "primitives/Biquad.h"

namespace klarinet {

/**
 * @brief Parameter IDs for the HighPassFilter effect.
 */
namespace HPFParams {
    /// @brief Cutoff frequency in Hertz. ID = 0.
    ///
    /// Frequencies below this value are progressively attenuated.
    /// The filter has a -3 dB point at the cutoff frequency.
    /// Range: 20.0 to 20000.0 Hz.
    /// Default: 20000.0 Hz.
    /// Units: Hertz (Hz).
    constexpr int32_t kCutoffHz  = 0;

    /// @brief Resonance (Q factor). ID = 1.
    ///
    /// Controls the sharpness of the filter's response at the cutoff
    /// frequency. Higher values create a resonant peak at the cutoff.
    /// Range: 0.1 to 18.0.
    /// Default: 0.707 (Butterworth, maximally flat passband).
    /// Units: dimensionless.
    constexpr int32_t kResonance = 1;
}

/**
 * @brief Single second-order (biquad) high-pass filter.
 *
 * Attenuates frequencies below the cutoff frequency at a rate of
 * -12 dB/octave (second-order roll-off). Useful for removing rumble,
 * DC offset, and other low-frequency content from audio signals.
 * The resonance parameter controls whether the response is flat
 * (Butterworth at Q = 0.707) or has a peak at the cutoff.
 *
 * @par DSP Algorithm
 * Uses a biquad filter configured as BiquadType::HighPass. Identical
 * architecture to LowPassFilter but with high-pass coefficients.
 *
 *     H(z) = (b0 + b1*z^-1 + b2*z^-2) / (a0 + a1*z^-1 + a2*z^-2)
 *
 * Coefficients are recomputed only when a parameter changes (dirty flag).
 *
 * @par Mono/Stereo Handling
 * One biquad instance is allocated per channel during prepare(), ensuring
 * independent filter state for each channel. The same cutoff and resonance
 * apply to all channels.
 *
 * @par Thread Safety
 * All parameters are stored in `std::atomic<float>` and use a dirty flag
 * to defer coefficient recomputation to the audio thread.
 */
class HighPassFilter : public AudioEffect {
public:
    /** @brief Constructs a HighPassFilter with cutoff at 20 kHz. */
    HighPassFilter();

    /** @brief Default destructor. */
    ~HighPassFilter() override = default;

    /**
     * @brief Processes the audio buffer through the high-pass biquad filter.
     *
     * If parameters have changed (dirty flag set), recomputes biquad
     * coefficients before processing. Each sample is passed through
     * the biquad for its respective channel.
     *
     * @param buffer      Interleaved audio buffer (frames x channels).
     * @param numFrames   Number of audio frames in the buffer.
     * @param channelCount Number of interleaved channels per frame.
     */
    void process(float* buffer, int32_t numFrames, int32_t channelCount) override;

    /**
     * @brief Sets a parameter value by ID.
     *
     * Raises the dirty flag to trigger coefficient recomputation on the
     * next process() call.
     *
     * @param paramId  Parameter ID (see HPFParams).
     * @param value    The parameter value.
     */
    void setParameter(int32_t paramId, float value) override;

    /**
     * @brief Gets a parameter value by ID.
     * @param paramId  Parameter ID (see HPFParams).
     * @return The current parameter value, or 0.0f for unknown IDs.
     */
    float getParameter(int32_t paramId) const override;

    /**
     * @brief Prepares the filter for playback.
     *
     * Allocates one biquad per channel and marks the filter as dirty
     * to force initial coefficient computation.
     *
     * @param sampleRate    The audio sample rate in Hz.
     * @param channelCount  The number of audio channels.
     */
    void prepare(int32_t sampleRate, int32_t channelCount) override;

    /**
     * @brief Resets the filter to default parameters (20 kHz, Q = 0.707).
     *
     * Clears all biquad filter state and marks the filter as dirty.
     */
    void reset() override;

private:
    /// @brief Cutoff frequency in Hz. Default: 20000.0 Hz.
    std::atomic<float> cutoffHz_{20000.0f};

    /// @brief Resonance (Q factor). Default: 0.707 (Butterworth).
    std::atomic<float> resonance_{0.707f};

    /// @brief Dirty flag; true when biquad coefficients need recomputation.
    std::atomic<bool> dirty_{true};

    /// @brief One biquad filter instance per audio channel.
    std::vector<Biquad> biquads_;
};

} // namespace klarinet
