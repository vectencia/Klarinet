#pragma once
#include <atomic>
#include <cstdint>
#include <cstring>
#include "AudioEffect.h"

namespace klarinet {

/**
 * @brief Parameter IDs for the MuteSolo effect.
 */
namespace MuteSoloParams {
    /// @brief Mute state as a boolean-like float. ID = 0.
    ///
    /// Values: 0.0 = unmuted, > 0.5 = muted.
    /// Default: 0.0 (unmuted).
    constexpr int32_t kMuted = 0;

    /// @brief Solo state as a boolean-like float. ID = 1.
    ///
    /// Values: 0.0 = not soloed, > 0.5 = soloed.
    /// Default: 0.0 (not soloed).
    ///
    /// @note Solo is stored per-effect but is a chain-level concept.
    /// The solo flag is not enforced at the individual effect level;
    /// the audio processing chain is responsible for reading this
    /// value and muting other non-soloed channels accordingly.
    constexpr int32_t kSoloed = 1;
}

/**
 * @brief Mute and solo control effect.
 *
 * Provides mute and solo functionality for an audio channel or bus.
 *
 * **Mute** zeroes the entire audio buffer using `memset`, completely
 * silencing the signal. This is the most efficient way to silence a
 * channel since it avoids per-sample multiplication.
 *
 * **Solo** is stored as a flag but is not enforced at the effect level.
 * Solo is inherently a chain-level concept: when one channel is soloed,
 * all other non-soloed channels should be muted. The processing chain
 * reads the solo flag from each MuteSolo effect and implements the
 * muting logic externally.
 *
 * @par DSP Algorithm
 * When muted (kMuted > 0.5): `memset(buffer, 0, totalSamples * sizeof(float))`
 * When unmuted: buffer passes through unmodified.
 *
 * @par Mono/Stereo Handling
 * Works identically for any channel count since mute zeroes the entire buffer.
 *
 * @par Thread Safety
 * Both parameters are stored in `std::atomic<float>` and can be safely
 * updated from any thread while the audio thread is processing.
 */
class MuteSolo : public AudioEffect {
public:
    /** @brief Constructs a MuteSolo effect with both mute and solo disabled. */
    MuteSolo();

    /** @brief Default destructor. */
    ~MuteSolo() override = default;

    /**
     * @brief Processes the audio buffer, zeroing it if muted.
     *
     * When the muted flag is set (value > 0.5), the entire buffer is
     * filled with zeros. Otherwise, the buffer passes through unmodified.
     * The solo flag is not acted upon here.
     *
     * @param buffer      Interleaved audio buffer (frames x channels).
     * @param numFrames   Number of audio frames in the buffer.
     * @param channelCount Number of interleaved channels per frame.
     */
    void process(float* buffer, int32_t numFrames, int32_t channelCount) override;

    /**
     * @brief Sets a parameter value by ID.
     * @param paramId  Parameter ID (MuteSoloParams::kMuted or kSoloed).
     * @param value    Boolean-like float: 0.0 = off, > 0.5 = on.
     */
    void setParameter(int32_t paramId, float value) override;

    /**
     * @brief Gets a parameter value by ID.
     * @param paramId  Parameter ID (MuteSoloParams::kMuted or kSoloed).
     * @return The current state as a float, or 0.0f for unknown IDs.
     */
    float getParameter(int32_t paramId) const override;

    /**
     * @brief Prepares the effect for playback.
     * @param sampleRate    The audio sample rate in Hz.
     * @param channelCount  The number of audio channels.
     */
    void prepare(int32_t sampleRate, int32_t channelCount) override;

    /**
     * @brief Resets both mute and solo states to off (0.0).
     */
    void reset() override;

private:
    /// @brief Mute state. Values > 0.5 mean muted. Default: 0.0 (unmuted).
    std::atomic<float> muted_{0.0f};

    /// @brief Solo state. Values > 0.5 mean soloed. Default: 0.0 (not soloed).
    std::atomic<float> soloed_{0.0f};
};

} // namespace klarinet
