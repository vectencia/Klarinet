#pragma once
#include <atomic>
#include <vector>
#include <cstdint>
#include "AudioEffect.h"
#include "primitives/CircularBuffer.h"

namespace klarinet {

/**
 * @brief Parameter IDs for the Delay effect.
 */
namespace DelayParams {
    /// @brief Delay time in milliseconds. ID = 0.
    ///
    /// The time offset between the dry signal and the delayed copy.
    /// Range: 0.0 to 2000.0 ms (limited by kMaxDelaySeconds).
    /// Default: 250.0 ms.
    /// Units: milliseconds (ms).
    constexpr int32_t kTimeMs = 0;

    /// @brief Feedback amount. ID = 1.
    ///
    /// The fraction of the delayed signal fed back into the delay line.
    /// Higher values produce more echo repetitions. Clamped to
    /// [0.0, kMaxFeedback (0.95)] during processing to prevent
    /// infinite feedback buildup and instability.
    /// Range: 0.0 to 0.95.
    /// Default: 0.4.
    /// Units: normalized (0.0 to 1.0 scale, clamped to 0.95 max).
    constexpr int32_t kFeedback = 1;

    /// @brief Wet/dry mix ratio. ID = 2.
    ///
    /// Controls the balance between the original (dry) signal and the
    /// delayed (wet) signal. 0.0 = fully dry, 1.0 = fully wet.
    /// Range: 0.0 to 1.0.
    /// Default: 0.3.
    /// Units: normalized.
    constexpr int32_t kWetDryMix = 2;
}

/**
 * @brief Circular buffer delay with feedback and wet/dry mix.
 *
 * Produces an echo effect by storing audio in a circular buffer and
 * playing it back after a configurable delay time. Feedback routes the
 * delayed signal back into the delay line, creating multiple decaying
 * repetitions.
 *
 * @par DSP Algorithm
 * For each frame and channel:
 * 1. Read the delayed sample from the circular buffer using linear
 *    interpolation at the current delay time (in samples).
 * 2. Write the current input sample plus the feedback-scaled delayed
 *    sample back into the delay line:
 *    `delayLine.write(dry + delayed * feedback)`
 * 3. Mix the output: `output = dry * (1 - mix) + delayed * mix`
 *
 * @par Feedback Clamping
 * The feedback parameter is clamped to the range [0.0, 0.95] during
 * processing (via kMaxFeedback = 0.95). This prevents feedback values
 * at or above 1.0, which would cause the delayed signal to grow
 * indefinitely, leading to clipping and instability. At 0.95, the
 * echoes decay by about -0.45 dB per repetition.
 *
 * @par Maximum Delay
 * The delay line is allocated for a maximum of 2 seconds (kMaxDelaySeconds)
 * at the given sample rate. The delay time parameter is converted from
 * milliseconds to samples: `delaySamples = timeMs * 0.001 * sampleRate`.
 *
 * @par Mono/Stereo Handling
 * One circular buffer (delay line) is allocated per channel during
 * prepare(). Each channel is processed independently with its own
 * delay line state.
 *
 * @par Thread Safety
 * All parameters are stored in `std::atomic<float>` and can be safely
 * updated from any thread.
 */
class Delay : public AudioEffect {
public:
    /** @brief Constructs a Delay effect with default parameters. */
    Delay();

    /** @brief Default destructor. */
    ~Delay() override = default;

    /**
     * @brief Processes the audio buffer, applying delay with feedback.
     *
     * For each frame and channel, reads a delayed sample, writes the
     * input plus feedback into the delay line, and mixes dry and wet
     * signals for the output.
     *
     * @param buffer      Interleaved audio buffer (frames x channels).
     * @param numFrames   Number of audio frames in the buffer.
     * @param channelCount Number of interleaved channels per frame.
     */
    void process(float* buffer, int32_t numFrames, int32_t channelCount) override;

    /**
     * @brief Sets a parameter value by ID.
     * @param paramId  Parameter ID (see DelayParams).
     * @param value    The parameter value.
     */
    void setParameter(int32_t paramId, float value) override;

    /**
     * @brief Gets a parameter value by ID.
     * @param paramId  Parameter ID (see DelayParams).
     * @return The current parameter value, or 0.0f for unknown IDs.
     */
    float getParameter(int32_t paramId) const override;

    /**
     * @brief Prepares the delay for playback.
     *
     * Allocates one circular buffer per channel, each sized for the
     * maximum delay time (kMaxDelaySeconds * sampleRate + 1).
     *
     * @param sampleRate    The audio sample rate in Hz.
     * @param channelCount  The number of audio channels.
     */
    void prepare(int32_t sampleRate, int32_t channelCount) override;

    /**
     * @brief Resets all delay lines by clearing their buffers.
     */
    void reset() override;

private:
    /// @brief Maximum delay time in seconds. Determines delay line buffer size.
    static constexpr float kMaxDelaySeconds = 2.0f;

    /// @brief Maximum allowed feedback value to prevent infinite feedback buildup.
    static constexpr float kMaxFeedback = 0.95f;

    /// @brief Delay time in milliseconds. Default: 250.0 ms.
    std::atomic<float> timeMs_{250.0f};

    /// @brief Feedback amount, clamped to [0, kMaxFeedback]. Default: 0.4.
    std::atomic<float> feedback_{0.4f};

    /// @brief Wet/dry mix ratio (0 = fully dry, 1 = fully wet). Default: 0.3.
    std::atomic<float> wetDryMix_{0.3f};

    /// @brief One circular buffer delay line per audio channel.
    std::vector<CircularBuffer> delayLines_;
};

} // namespace klarinet
