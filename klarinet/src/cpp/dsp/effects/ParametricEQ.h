#pragma once
#include <atomic>
#include <array>
#include <cstdint>
#include <vector>
#include "AudioEffect.h"
#include "primitives/Biquad.h"

namespace klarinet {

/// @brief Maximum number of EQ bands supported by the parametric equalizer.
constexpr int32_t kMaxEQBands = 8;

/**
 * @brief Parameter IDs and layout for the ParametricEQ effect.
 *
 * Parameters are organized in groups of 4 per band. To access a specific
 * band's parameter, use the formula:
 *
 *     paramId = bandIndex * kParamsPerBand + offset
 *
 * For example, band 3's frequency is paramId = 3 * 4 + 0 = 12.
 * Band 5's Q factor is paramId = 5 * 4 + 2 = 22.
 *
 * Total parameter ID range: 0 to (kMaxEQBands * kParamsPerBand - 1) = 31.
 */
namespace EQParams {
    /// @brief Number of parameters per EQ band (frequency, gain, Q, type).
    constexpr int32_t kParamsPerBand = 4;

    /// @brief Band center frequency offset (offset 0 within each band group).
    ///
    /// Range: 20.0 to 20000.0 Hz.
    /// Default: 1000.0 Hz.
    /// Units: Hertz (Hz).
    constexpr int32_t kBandFrequency = 0;

    /// @brief Band gain offset (offset 1 within each band group).
    ///
    /// Boost or cut at the center frequency. Only meaningful for Peak type.
    /// Range: -24.0 to +24.0 dB.
    /// Default: 0.0 dB (flat).
    /// Units: decibels (dB).
    constexpr int32_t kBandGain      = 1;

    /// @brief Band Q factor offset (offset 2 within each band group).
    ///
    /// Controls the bandwidth of the filter. Higher Q means narrower bandwidth.
    /// Range: 0.1 to 18.0.
    /// Default: 0.707 (Butterworth, maximally flat).
    /// Units: dimensionless.
    constexpr int32_t kBandQ         = 2;

    /// @brief Band filter type offset (offset 3 within each band group).
    ///
    /// Cast to BiquadType enum. Common types include LowPass, HighPass,
    /// BandPass, Peak, LowShelf, HighShelf.
    /// Default: BiquadType::Peak.
    constexpr int32_t kBandType      = 3;
}

/**
 * @brief 8-band parametric equalizer using cascaded biquad filters.
 *
 * Provides up to 8 independent EQ bands, each implemented as a biquad
 * filter. Bands are processed in series (cascaded), meaning the output
 * of one band feeds into the next. Each band has independent frequency,
 * gain, Q, and filter type settings.
 *
 * @par DSP Algorithm
 * - Each band is a second-order IIR (biquad) filter with configurable type.
 * - Bands are processed sequentially over the entire buffer. For each band,
 *   every sample is passed through the biquad for its respective channel.
 * - Each channel has its own biquad instance to maintain independent filter
 *   state, ensuring correct stereo/multi-channel processing.
 * - Bands with Peak type and 0 dB gain are skipped as an optimization since
 *   they would have no audible effect.
 * - A "dirty" flag per band ensures biquad coefficients are only recomputed
 *   when a parameter actually changes, minimizing per-frame overhead.
 *
 * @par Parameter Addressing
 * Parameters are addressed using: `paramId = bandIndex * 4 + offset`
 * where offset is one of kBandFrequency, kBandGain, kBandQ, or kBandType.
 * This flat addressing scheme allows simple integer-based parameter access
 * from the Kotlin control plane.
 *
 * @par Mono/Stereo Handling
 * One biquad instance is created per channel per band during prepare().
 * This ensures each channel's filter state is tracked independently.
 *
 * @par Thread Safety
 * All band parameters are stored in `std::atomic<float>`. The dirty flag
 * triggers coefficient recomputation at the start of the next process() call.
 */
class ParametricEQ : public AudioEffect {
public:
    /** @brief Constructs a ParametricEQ with 8 bands at default settings. */
    ParametricEQ();

    /** @brief Default destructor. */
    ~ParametricEQ() override = default;

    /**
     * @brief Processes the audio buffer through all 8 EQ bands.
     *
     * First, any bands with changed parameters (dirty flag set) have their
     * biquad coefficients recomputed. Then, each band processes the entire
     * buffer in sequence. Bands with Peak type and 0 dB gain are skipped.
     *
     * @param buffer      Interleaved audio buffer (frames x channels).
     * @param numFrames   Number of audio frames in the buffer.
     * @param channelCount Number of interleaved channels per frame.
     */
    void process(float* buffer, int32_t numFrames, int32_t channelCount) override;

    /**
     * @brief Sets a parameter value by band index and offset.
     *
     * The paramId encodes both the band index and the parameter offset:
     * `bandIndex = paramId / 4`, `offset = paramId % 4`.
     * After setting, the band's dirty flag is raised to trigger coefficient
     * recomputation on the next process() call.
     *
     * @param paramId  Encoded parameter ID (bandIndex * 4 + offset).
     * @param value    The parameter value.
     */
    void setParameter(int32_t paramId, float value) override;

    /**
     * @brief Gets a parameter value by band index and offset.
     * @param paramId  Encoded parameter ID (bandIndex * 4 + offset).
     * @return The current parameter value, or 0.0f for out-of-range IDs.
     */
    float getParameter(int32_t paramId) const override;

    /**
     * @brief Prepares the equalizer for playback.
     *
     * Allocates one biquad per channel per band and marks all bands as
     * dirty to force initial coefficient computation.
     *
     * @param sampleRate    The audio sample rate in Hz.
     * @param channelCount  The number of audio channels.
     */
    void prepare(int32_t sampleRate, int32_t channelCount) override;

    /**
     * @brief Resets all bands to default parameters and clears filter state.
     *
     * All bands are reset to 1000 Hz, 0 dB gain, Q = 0.707, Peak type.
     */
    void reset() override;

private:
    /**
     * @brief Internal representation of a single EQ band.
     */
    struct Band {
        /// @brief Center frequency in Hz. Default: 1000.0 Hz.
        std::atomic<float> frequency{1000.0f};

        /// @brief Gain in dB (for Peak/Shelf types). Default: 0.0 dB (flat).
        std::atomic<float> gain{0.0f};

        /// @brief Q factor (bandwidth). Default: 0.707 (Butterworth).
        std::atomic<float> q{0.707f};

        /// @brief Filter type as float (cast to BiquadType). Default: Peak.
        std::atomic<float> type{static_cast<float>(BiquadType::Peak)};

        /// @brief Dirty flag; true when coefficients need recomputation.
        std::atomic<bool> dirty{true};

        /// @brief One biquad filter instance per audio channel.
        std::vector<Biquad> biquads;
    };

    /// @brief Array of 8 EQ bands.
    std::array<Band, kMaxEQBands> bands_;
};

} // namespace klarinet
