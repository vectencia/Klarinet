#pragma once
#include <atomic>
#include <cmath>
#include <cstdint>
#include "AudioEffect.h"

namespace klarinet {

/**
 * @brief Parameter IDs for the Pan effect.
 */
namespace PanParams {
    /// @brief Stereo pan position. ID = 0.
    ///
    /// Range: -1.0 (hard left) to +1.0 (hard right).
    /// Default: 0.0 (center).
    /// Units: normalized position.
    constexpr int32_t kPan = 0;
}

/**
 * @brief Constant-power stereo panning effect.
 *
 * Distributes the audio signal between left and right channels using a
 * constant-power panning law based on sine and cosine functions. This
 * preserves the perceived loudness when the signal is panned between
 * speakers, unlike linear panning which causes a ~3 dB dip at center.
 *
 * @par DSP Algorithm
 * The pan value [-1, +1] is mapped to an angle in the range [0, PI/2]:
 *
 *     angle = (pan + 1) * PI / 4
 *     gainL = cos(angle)
 *     gainR = sin(angle)
 *
 * At center (pan = 0), angle = PI/4, so gainL = gainR = cos(PI/4) ~= 0.707,
 * yielding equal power in both channels.
 * At hard left (pan = -1), angle = 0, so gainL = 1.0 and gainR = 0.0.
 * At hard right (pan = +1), angle = PI/2, so gainL = 0.0 and gainR = 1.0.
 *
 * @par Mono Handling
 * For mono signals (channelCount < 2), the effect is a no-op since panning
 * requires at least two channels. Channels beyond the first two (e.g., in
 * surround layouts) are left unchanged.
 *
 * @par Thread Safety
 * The pan parameter is stored in a `std::atomic<float>` and can be safely
 * updated from any thread while the audio thread is processing.
 */
class Pan : public AudioEffect {
public:
    /** @brief Constructs a Pan effect with default center position (0.0). */
    Pan();

    /** @brief Default destructor. */
    ~Pan() override = default;

    /**
     * @brief Applies constant-power panning to the stereo audio buffer.
     *
     * Multiplies the left channel by cos(angle) and the right channel by
     * sin(angle). If the channel count is less than 2, the buffer is left
     * untouched. If the effect is disabled, the buffer is also untouched.
     *
     * @param buffer      Interleaved audio buffer (frames x channels).
     * @param numFrames   Number of audio frames in the buffer.
     * @param channelCount Number of interleaved channels per frame.
     */
    void process(float* buffer, int32_t numFrames, int32_t channelCount) override;

    /**
     * @brief Sets a parameter value by ID.
     * @param paramId  Parameter ID (use PanParams::kPan).
     * @param value    Pan position from -1.0 (left) to +1.0 (right).
     */
    void setParameter(int32_t paramId, float value) override;

    /**
     * @brief Gets a parameter value by ID.
     * @param paramId  Parameter ID (use PanParams::kPan).
     * @return The current pan position, or 0.0f for unknown IDs.
     */
    float getParameter(int32_t paramId) const override;

    /**
     * @brief Prepares the effect for playback.
     * @param sampleRate    The audio sample rate in Hz.
     * @param channelCount  The number of audio channels.
     */
    void prepare(int32_t sampleRate, int32_t channelCount) override;

    /**
     * @brief Resets the pan position to center (0.0).
     */
    void reset() override;

private:
    /// @brief Current pan position. Range: -1.0 (left) to +1.0 (right). Default: 0.0 (center).
    std::atomic<float> pan_{0.0f};
};

} // namespace klarinet
