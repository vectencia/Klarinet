#pragma once
#include <atomic>
#include <cstdint>
#include <vector>
#include "AudioEffect.h"
#include "primitives/Biquad.h"

namespace klarinet {

/**
 * @brief Parameter IDs for the BandPassFilter effect.
 */
namespace BPFParams {
    /// @brief Center frequency in Hertz. ID = 0.
    ///
    /// The frequency at the center of the passband. Frequencies above
    /// and below are progressively attenuated.
    /// Range: 20.0 to 20000.0 Hz.
    /// Default: 1000.0 Hz.
    /// Units: Hertz (Hz).
    constexpr int32_t kCenterHz  = 0;

    /// @brief Bandwidth (Q factor). ID = 1.
    ///
    /// Controls the width of the passband. Higher values produce a
    /// narrower band (more selective), lower values produce a wider band.
    /// Range: 0.1 to 18.0.
    /// Default: 1.0.
    /// Units: dimensionless (Q factor).
    constexpr int32_t kBandwidth = 1;
}

/**
 * @brief Single second-order (biquad) band-pass filter.
 *
 * Passes frequencies near the center frequency and attenuates frequencies
 * above and below it. The bandwidth (Q factor) controls how wide or narrow
 * the passband is. Useful for isolating specific frequency ranges, tone
 * shaping, or implementing wah-wah effects.
 *
 * @par DSP Algorithm
 * Uses a biquad filter configured as BiquadType::BandPass. The second-order
 * IIR filter provides a -6 dB/octave roll-off on each side of the passband
 * (combined -12 dB/octave for the full skirt).
 *
 *     H(z) = (b0 + b1*z^-1 + b2*z^-2) / (a0 + a1*z^-1 + a2*z^-2)
 *
 * Coefficients are recomputed only when a parameter changes (dirty flag).
 *
 * @par Mono/Stereo Handling
 * One biquad instance is allocated per channel during prepare(), ensuring
 * independent filter state for each channel. The same center frequency
 * and bandwidth apply to all channels.
 *
 * @par Thread Safety
 * All parameters are stored in `std::atomic<float>` and use a dirty flag
 * to defer coefficient recomputation to the audio thread.
 */
class BandPassFilter : public AudioEffect {
public:
    /** @brief Constructs a BandPassFilter centered at 1000 Hz. */
    BandPassFilter();

    /** @brief Default destructor. */
    ~BandPassFilter() override = default;

    /**
     * @brief Processes the audio buffer through the band-pass biquad filter.
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
     * @param paramId  Parameter ID (see BPFParams).
     * @param value    The parameter value.
     */
    void setParameter(int32_t paramId, float value) override;

    /**
     * @brief Gets a parameter value by ID.
     * @param paramId  Parameter ID (see BPFParams).
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
     * @brief Resets the filter to default parameters (1000 Hz center, Q = 1.0).
     *
     * Clears all biquad filter state and marks the filter as dirty.
     */
    void reset() override;

private:
    /// @brief Center frequency in Hz. Default: 1000.0 Hz.
    std::atomic<float> centerHz_{1000.0f};

    /// @brief Bandwidth (Q factor). Default: 1.0.
    std::atomic<float> bandwidth_{1.0f};

    /// @brief Dirty flag; true when biquad coefficients need recomputation.
    std::atomic<bool> dirty_{true};

    /// @brief One biquad filter instance per audio channel.
    std::vector<Biquad> biquads_;
};

} // namespace klarinet
