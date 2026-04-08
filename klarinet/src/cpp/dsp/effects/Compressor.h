#pragma once
#include <atomic>
#include <cmath>
#include <cstdint>
#include "AudioEffect.h"
#include "primitives/EnvelopeFollower.h"

namespace klarinet {

/**
 * @brief Parameter IDs for the Compressor effect.
 */
namespace CompressorParams {
    /// @brief Compression threshold in decibels. ID = 0.
    ///
    /// Signals above this level are compressed. Lower values mean more
    /// of the signal is affected.
    /// Range: typically -60.0 to 0.0 dB.
    /// Default: -20.0 dB.
    /// Units: decibels (dB).
    constexpr int32_t kThreshold  = 0;

    /// @brief Compression ratio. ID = 1.
    ///
    /// Determines how much the signal above the threshold is reduced.
    /// A ratio of 4:1 means that for every 4 dB the input exceeds the
    /// threshold, the output only exceeds it by 1 dB. A ratio of 1:1
    /// means no compression.
    /// Range: 1.0 (no compression) to infinity (limiter behavior).
    /// Default: 4.0.
    /// Units: ratio (e.g. 4.0 means 4:1).
    constexpr int32_t kRatio      = 1;

    /// @brief Attack time in milliseconds. ID = 2.
    ///
    /// How quickly the compressor reacts to signals exceeding the threshold.
    /// Shorter attack times catch transients more aggressively.
    /// Range: 0.1 to 200.0 ms.
    /// Default: 10.0 ms.
    /// Units: milliseconds (ms).
    constexpr int32_t kAttackMs   = 2;

    /// @brief Release time in milliseconds. ID = 3.
    ///
    /// How quickly the compressor stops reducing gain after the signal
    /// drops below the threshold.
    /// Range: 10.0 to 2000.0 ms.
    /// Default: 100.0 ms.
    /// Units: milliseconds (ms).
    constexpr int32_t kReleaseMs  = 3;

    /// @brief Makeup gain in decibels. ID = 4.
    ///
    /// Applied after compression to restore lost volume. Since compression
    /// reduces the overall level, makeup gain compensates for this reduction.
    /// Range: 0.0 to 40.0 dB.
    /// Default: 0.0 dB.
    /// Units: decibels (dB).
    constexpr int32_t kMakeupGain = 4;
}

/**
 * @brief Dynamic range compressor with envelope follower.
 *
 * Reduces the dynamic range of audio by attenuating signals that exceed
 * a given threshold. Uses an envelope follower to smoothly track the
 * signal level, preventing abrupt gain changes that would cause audible
 * artifacts.
 *
 * @par DSP Algorithm
 * For each frame:
 * 1. **Peak detection**: Find the maximum absolute sample across all channels.
 * 2. **Envelope following**: Smooth the peak with attack/release time constants.
 * 3. **dB conversion**: Convert envelope to dB: `envDb = 20 * log10(envLevel)`.
 * 4. **Gain computation**: If `envDb > threshold`:
 *    - `overDb = envDb - threshold`
 *    - `compressedDb = threshold + overDb / ratio`
 *    - `gainReductionDb = compressedDb - envDb`
 *    - `gainLinear = 10^(gainReductionDb / 20)`
 * 5. **Apply**: Multiply all channels by `gainLinear * makeupLinear`.
 *
 * @par Mono/Stereo Handling
 * Peak detection operates across all channels in a frame (linked stereo),
 * ensuring the stereo image is preserved. The same gain reduction is
 * applied uniformly to all channels within each frame.
 *
 * @par Thread Safety
 * All parameters are stored in `std::atomic<float>` and can be safely
 * updated from any thread while the audio thread is processing.
 */
class Compressor : public AudioEffect {
public:
    /** @brief Constructs a Compressor with default parameters. */
    Compressor();

    /** @brief Default destructor. */
    ~Compressor() override = default;

    /**
     * @brief Processes the audio buffer, applying dynamic range compression.
     *
     * For each frame, detects the peak level, tracks it with the envelope
     * follower, computes gain reduction based on the threshold and ratio,
     * and applies the gain reduction plus makeup gain to all channels.
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
     * constants are updated immediately. The ratio is clamped to a minimum
     * of 1.0 to prevent invalid compression behavior.
     *
     * @param paramId  Parameter ID (see CompressorParams).
     * @param value    The parameter value.
     */
    void setParameter(int32_t paramId, float value) override;

    /**
     * @brief Gets a parameter value by ID.
     * @param paramId  Parameter ID (see CompressorParams).
     * @return The current parameter value, or 0.0f for unknown IDs.
     */
    float getParameter(int32_t paramId) const override;

    /**
     * @brief Prepares the compressor for playback.
     *
     * Initializes the envelope follower with the current sample rate
     * and configures its attack and release times from the stored parameters.
     *
     * @param sampleRate    The audio sample rate in Hz.
     * @param channelCount  The number of audio channels.
     */
    void prepare(int32_t sampleRate, int32_t channelCount) override;

    /**
     * @brief Resets the envelope follower state.
     *
     * Clears the internal envelope state so that the compressor starts
     * fresh with no gain reduction history.
     */
    void reset() override;

private:
    /// @brief Compression threshold in dB. Default: -20.0 dB.
    std::atomic<float> threshold_{-20.0f};

    /// @brief Compression ratio (e.g. 4.0 = 4:1). Minimum: 1.0. Default: 4.0.
    std::atomic<float> ratio_{4.0f};

    /// @brief Attack time in milliseconds. Default: 10.0 ms.
    std::atomic<float> attackMs_{10.0f};

    /// @brief Release time in milliseconds. Default: 100.0 ms.
    std::atomic<float> releaseMs_{100.0f};

    /// @brief Makeup gain in dB, applied after compression. Default: 0.0 dB.
    std::atomic<float> makeupGain_{0.0f};

    /// @brief Envelope follower for smooth level tracking with attack/release.
    EnvelopeFollower envFollower_;
};

} // namespace klarinet
