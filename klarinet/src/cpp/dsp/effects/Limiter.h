#pragma once
#include <atomic>
#include <cmath>
#include <cstdint>
#include "AudioEffect.h"
#include "primitives/EnvelopeFollower.h"

namespace klarinet {

/**
 * @brief Parameter IDs for the Limiter effect.
 */
namespace LimiterParams {
    /// @brief Limiting threshold in decibels. ID = 0.
    ///
    /// The maximum output level; signals exceeding this are attenuated
    /// to prevent clipping. Typically set just below 0 dB.
    /// Range: -60.0 to 0.0 dB.
    /// Default: -1.0 dB.
    /// Units: decibels (dB).
    constexpr int32_t kThreshold  = 0;

    /// @brief Release time in milliseconds. ID = 1.
    ///
    /// How quickly the limiter releases gain reduction after the signal
    /// drops below the threshold. Shorter values may cause pumping artifacts.
    /// Range: 1.0 to 500.0 ms.
    /// Default: 50.0 ms.
    /// Units: milliseconds (ms).
    constexpr int32_t kReleaseMs  = 1;
}

/**
 * @brief Brick-wall limiter (infinite-ratio compressor).
 *
 * Prevents the audio signal from exceeding a specified threshold by
 * applying gain reduction with an effectively infinite compression ratio.
 * This is equivalent to a compressor with ratio = infinity, meaning no
 * signal ever exceeds the threshold at the output.
 *
 * Uses a near-instant attack time (0.1 ms, set during prepare()) to catch
 * transients quickly, while allowing a configurable release time for
 * smooth gain recovery.
 *
 * @par DSP Algorithm
 * For each frame:
 * 1. **Peak detection**: Find the maximum absolute sample across all channels.
 * 2. **Envelope following**: Smooth the peak with near-instant attack and
 *    configurable release time constants.
 * 3. **Gain computation**: If `envLevel > threshLinear`:
 *    - `gainLinear = threshLinear / envLevel`
 *    - This ensures the output never exceeds the threshold.
 *    - Otherwise, `gainLinear = 1.0` (no reduction).
 * 4. **Apply**: Multiply all channels by `gainLinear`.
 *
 * The threshold is converted from dB to linear: `threshLinear = 10^(threshDb / 20)`.
 *
 * @par Difference from Compressor
 * - The Limiter uses an infinite ratio (hard ceiling) instead of a configurable ratio.
 * - The attack is fixed at 0.1 ms (near-instant) to catch all transients.
 * - There is no makeup gain parameter.
 * - Gain computation operates in the linear domain for efficiency.
 *
 * @par Mono/Stereo Handling
 * Peak detection is linked across all channels (same as Compressor),
 * preserving the stereo image.
 *
 * @par Thread Safety
 * All parameters are stored in `std::atomic<float>` and can be safely
 * updated from any thread.
 */
class Limiter : public AudioEffect {
public:
    /** @brief Constructs a Limiter with default parameters. */
    Limiter();

    /** @brief Default destructor. */
    ~Limiter() override = default;

    /**
     * @brief Processes the audio buffer, applying brick-wall limiting.
     *
     * For each frame, detects the peak level across all channels, tracks
     * it with the envelope follower, and if the envelope exceeds the
     * threshold, reduces the gain so that the output stays at or below
     * the threshold level.
     *
     * @param buffer      Interleaved audio buffer (frames x channels).
     * @param numFrames   Number of audio frames in the buffer.
     * @param channelCount Number of interleaved channels per frame.
     */
    void process(float* buffer, int32_t numFrames, int32_t channelCount) override;

    /**
     * @brief Sets a parameter value by ID.
     *
     * When setting kReleaseMs, the envelope follower's release time
     * is updated immediately.
     *
     * @param paramId  Parameter ID (see LimiterParams).
     * @param value    The parameter value.
     */
    void setParameter(int32_t paramId, float value) override;

    /**
     * @brief Gets a parameter value by ID.
     * @param paramId  Parameter ID (see LimiterParams).
     * @return The current parameter value, or 0.0f for unknown IDs.
     */
    float getParameter(int32_t paramId) const override;

    /**
     * @brief Prepares the limiter for playback.
     *
     * Initializes the envelope follower with the current sample rate.
     * Sets the attack time to 0.1 ms (near-instant) for brick-wall
     * limiting behavior.
     *
     * @param sampleRate    The audio sample rate in Hz.
     * @param channelCount  The number of audio channels.
     */
    void prepare(int32_t sampleRate, int32_t channelCount) override;

    /**
     * @brief Resets the envelope follower state.
     */
    void reset() override;

private:
    /// @brief Limiting threshold in dB. Default: -1.0 dB.
    std::atomic<float> threshold_{-1.0f};

    /// @brief Release time in milliseconds. Default: 50.0 ms.
    std::atomic<float> releaseMs_{50.0f};

    /// @brief Envelope follower with near-instant attack for peak tracking.
    EnvelopeFollower envFollower_;
};

} // namespace klarinet
