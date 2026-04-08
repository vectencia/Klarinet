#pragma once
#include <atomic>
#include <array>
#include <cstdint>
#include "AudioEffect.h"
#include "primitives/CircularBuffer.h"

namespace klarinet {

namespace ReverbParams {
    constexpr int32_t kRoomSize = 0;
    constexpr int32_t kDamping = 1;
    constexpr int32_t kWetDryMix = 2;
    constexpr int32_t kWidth = 3;
}

class Reverb : public AudioEffect {
public:
    Reverb();
    ~Reverb() override = default;

    void process(float* buffer, int32_t numFrames, int32_t channelCount) override;
    void setParameter(int32_t paramId, float value) override;
    float getParameter(int32_t paramId) const override;
    void prepare(int32_t sampleRate, int32_t channelCount) override;
    void reset() override;

private:
    static constexpr int kNumCombs = 8;
    static constexpr int kNumAllpasses = 4;
    static constexpr int kStereoSpread = 23;
    static constexpr float kFixedGain = 0.015f;
    static constexpr float kAllpassFeedback = 0.5f;
    static constexpr float kReferenceRate = 44100.0f;

    static constexpr int kCombTunings[kNumCombs] = {
        1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617
    };
    static constexpr int kAllpassTunings[kNumAllpasses] = {
        556, 441, 341, 225
    };

    std::atomic<float> roomSize_{0.7f};
    std::atomic<float> damping_{0.5f};
    std::atomic<float> wetDryMix_{0.3f};
    std::atomic<float> width_{1.0f};

    // Per-channel comb and allpass filters (index 0 = left, 1 = right)
    std::array<CircularBuffer, kNumCombs> combBuffersL_;
    std::array<CircularBuffer, kNumCombs> combBuffersR_;
    std::array<float, kNumCombs> filterStoreL_{};
    std::array<float, kNumCombs> filterStoreR_{};

    std::array<CircularBuffer, kNumAllpasses> allpassBuffersL_;
    std::array<CircularBuffer, kNumAllpasses> allpassBuffersR_;
};

} // namespace klarinet
