#pragma once
#include <atomic>
#include <array>
#include <cstdint>
#include "AudioEffect.h"
#include "primitives/CircularBuffer.h"

namespace klarinet {

/**
 * @brief Parameter IDs for the Reverb effect.
 */
namespace ReverbParams {
    /// @brief Room size parameter. ID = 0.
    ///
    /// Controls the length of the reverb tail by scaling the comb filter
    /// feedback. Higher values produce longer reverb times.
    /// Mapped internally: `feedback = roomSize * 0.28 + 0.7`, which gives
    /// a feedback range of approximately [0.7, 0.98].
    /// Range: 0.0 to 1.0.
    /// Default: 0.7.
    /// Units: normalized.
    constexpr int32_t kRoomSize = 0;

    /// @brief Damping parameter. ID = 1.
    ///
    /// Controls how quickly high frequencies decay in the reverb tail.
    /// Higher values produce more damping (darker reverb). Implemented
    /// as a one-pole low-pass filter in each comb filter's feedback loop:
    /// `filterStore = (1 - damping) * combOut + damping * filterStore`.
    /// Range: 0.0 (no damping, bright) to 1.0 (maximum damping, dark).
    /// Default: 0.5.
    /// Units: normalized.
    constexpr int32_t kDamping = 1;

    /// @brief Wet/dry mix ratio. ID = 2.
    ///
    /// Controls the balance between the original (dry) and reverberated
    /// (wet) signal. 0.0 = fully dry, 1.0 = fully wet.
    /// Range: 0.0 to 1.0.
    /// Default: 0.3.
    /// Units: normalized.
    constexpr int32_t kWetDryMix = 2;

    /// @brief Stereo width parameter. ID = 3.
    ///
    /// Controls the stereo spread of the reverb. At 1.0, the left and
    /// right reverb channels are fully independent. At 0.0, the reverb
    /// output is mono. Intermediate values crossfeed between channels.
    /// Implemented as: `wet1 = mix * (width * 0.5 + 0.5)`,
    /// `wet2 = mix * ((1 - width) * 0.5)`.
    /// Range: 0.0 (mono) to 1.0 (full stereo).
    /// Default: 1.0.
    /// Units: normalized.
    constexpr int32_t kWidth = 3;
}

/**
 * @brief Freeverb-based algorithmic reverb.
 *
 * Simulates room acoustics using the classic Freeverb algorithm, which
 * consists of 8 parallel comb filters feeding into 4 series allpass filters.
 * This produces a dense, natural-sounding reverb tail with adjustable room
 * size, damping, and stereo width.
 *
 * @par DSP Algorithm (Freeverb)
 *
 * **Signal Flow:**
 * 1. The input is mixed to mono and scaled by kFixedGain (0.015) to prevent
 *    internal clipping from the parallel comb filter summation.
 * 2. **8 parallel comb filters** (per channel): Each comb filter reads from
 *    a circular buffer at its fixed delay length, applies a one-pole low-pass
 *    damping filter to the feedback path, and writes `input + filtered * feedback`
 *    back into the buffer. The outputs of all 8 combs are summed.
 * 3. **4 series allpass filters** (per channel): The summed comb output is
 *    passed through 4 cascaded allpass filters with fixed feedback of 0.5.
 *    These diffuse the reverb tail, preventing metallic or ringing artifacts.
 * 4. **Output mixing**: The dry signal is mixed with the wet reverb output
 *    using the wet/dry mix and stereo width parameters.
 *
 * **Comb Filter Damping:**
 * Each comb filter has a one-pole low-pass filter in its feedback path:
 *
 *     filterStore = (1 - damping) * combOutput + damping * filterStore
 *
 * This causes high frequencies to decay faster, simulating the natural
 * absorption of high-frequency sound in real rooms.
 *
 * **Tuning Constants (at 44100 Hz reference rate):**
 * - Comb delays: 1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617 samples
 * - Allpass delays: 556, 441, 341, 225 samples
 * - These are prime-like numbers chosen to avoid resonant patterns.
 * - All delays are scaled proportionally for other sample rates.
 *
 * **Stereo Implementation:**
 * Separate left and right filter banks are maintained. The right channel's
 * delay lines are offset by kStereoSpread (23 samples) from the left
 * channel's, creating natural stereo decorrelation.
 *
 * **Feedback Calculation:**
 * `feedback = roomSize * 0.28 + 0.7` maps the [0, 1] room size parameter
 * to a feedback range of approximately [0.7, 0.98], ensuring stable
 * operation while allowing long reverb tails.
 *
 * @par Mono/Stereo Handling
 * - **Stereo (channelCount >= 2)**: Full stereo processing with independent
 *   left and right filter banks. Output uses width-based crossfeed:
 *   `outL = dryL * dry + reverbL * wet1 + reverbR * wet2`
 *   `outR = dryR * dry + reverbR * wet1 + reverbL * wet2`
 * - **Mono (channelCount < 2)**: Both L and R filter banks still process
 *   (maintaining the stereo spread internally), but only the left reverb
 *   output is used: `out = dry * dry + reverbL * mix`.
 *
 * @par Thread Safety
 * All parameters are stored in `std::atomic<float>` and can be safely
 * updated from any thread.
 */
class Reverb : public AudioEffect {
public:
    /** @brief Constructs a Reverb with default Freeverb parameters. */
    Reverb();

    /** @brief Default destructor. */
    ~Reverb() override = default;

    /**
     * @brief Processes the audio buffer through the Freeverb algorithm.
     *
     * Mixes input to mono, processes through 8 parallel comb filters and
     * 4 series allpass filters for each stereo channel, then mixes the
     * wet and dry signals.
     *
     * @param buffer      Interleaved audio buffer (frames x channels).
     * @param numFrames   Number of audio frames in the buffer.
     * @param channelCount Number of interleaved channels per frame.
     */
    void process(float* buffer, int32_t numFrames, int32_t channelCount) override;

    /**
     * @brief Sets a parameter value by ID.
     * @param paramId  Parameter ID (see ReverbParams).
     * @param value    The parameter value.
     */
    void setParameter(int32_t paramId, float value) override;

    /**
     * @brief Gets a parameter value by ID.
     * @param paramId  Parameter ID (see ReverbParams).
     * @return The current parameter value, or 0.0f for unknown IDs.
     */
    float getParameter(int32_t paramId) const override;

    /**
     * @brief Prepares the reverb for playback.
     *
     * Allocates and sizes all comb and allpass circular buffers, scaling
     * the Freeverb tuning constants from the 44100 Hz reference rate to
     * the actual sample rate. Right channel buffers are offset by
     * kStereoSpread (23 samples) for stereo decorrelation.
     *
     * @param sampleRate    The audio sample rate in Hz.
     * @param channelCount  The number of audio channels.
     */
    void prepare(int32_t sampleRate, int32_t channelCount) override;

    /**
     * @brief Resets all internal filter state.
     *
     * Clears all comb and allpass circular buffers and zeroes the
     * damping filter stores for both channels.
     */
    void reset() override;

private:
    /// @brief Number of parallel comb filters per channel.
    static constexpr int kNumCombs = 8;

    /// @brief Number of series allpass filters per channel.
    static constexpr int kNumAllpasses = 4;

    /// @brief Sample offset between left and right channel delay lines for stereo decorrelation.
    static constexpr int kStereoSpread = 23;

    /// @brief Input scaling gain to prevent clipping from parallel comb summation.
    static constexpr float kFixedGain = 0.015f;

    /// @brief Fixed feedback coefficient for all allpass filters.
    static constexpr float kAllpassFeedback = 0.5f;

    /// @brief Reference sample rate for the Freeverb tuning constants.
    static constexpr float kReferenceRate = 44100.0f;

    /// @brief Comb filter delay lengths in samples at 44100 Hz (prime-like values).
    static constexpr int kCombTunings[kNumCombs] = {
        1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617
    };

    /// @brief Allpass filter delay lengths in samples at 44100 Hz.
    static constexpr int kAllpassTunings[kNumAllpasses] = {
        556, 441, 341, 225
    };

    /// @brief Room size parameter [0, 1]. Controls feedback: `fb = roomSize * 0.28 + 0.7`. Default: 0.7.
    std::atomic<float> roomSize_{0.7f};

    /// @brief Damping parameter [0, 1]. Controls high-frequency decay. Default: 0.5.
    std::atomic<float> damping_{0.5f};

    /// @brief Wet/dry mix [0, 1]. 0 = fully dry, 1 = fully wet. Default: 0.3.
    std::atomic<float> wetDryMix_{0.3f};

    /// @brief Stereo width [0, 1]. 0 = mono reverb, 1 = full stereo. Default: 1.0.
    std::atomic<float> width_{1.0f};

    /// @brief Left channel comb filter delay buffers (8 parallel combs).
    std::array<CircularBuffer, kNumCombs> combBuffersL_;

    /// @brief Right channel comb filter delay buffers (8 parallel combs, offset by kStereoSpread).
    std::array<CircularBuffer, kNumCombs> combBuffersR_;

    /// @brief Left channel comb filter damping state (one-pole LPF state per comb).
    std::array<float, kNumCombs> filterStoreL_{};

    /// @brief Right channel comb filter damping state (one-pole LPF state per comb).
    std::array<float, kNumCombs> filterStoreR_{};

    /// @brief Left channel allpass filter delay buffers (4 series allpasses).
    std::array<CircularBuffer, kNumAllpasses> allpassBuffersL_;

    /// @brief Right channel allpass filter delay buffers (4 series allpasses, offset by kStereoSpread).
    std::array<CircularBuffer, kNumAllpasses> allpassBuffersR_;
};

} // namespace klarinet
