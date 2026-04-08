#pragma once
#include <atomic>
#include <array>
#include <cstdint>
#include <vector>
#include "AudioEffect.h"
#include "primitives/Biquad.h"

namespace klarinet {

constexpr int32_t kMaxEQBands = 8;

namespace EQParams {
    constexpr int32_t kParamsPerBand = 4;
    constexpr int32_t kBandFrequency = 0;
    constexpr int32_t kBandGain      = 1;
    constexpr int32_t kBandQ         = 2;
    constexpr int32_t kBandType      = 3;
}

class ParametricEQ : public AudioEffect {
public:
    ParametricEQ();
    ~ParametricEQ() override = default;

    void process(float* buffer, int32_t numFrames, int32_t channelCount) override;
    void setParameter(int32_t paramId, float value) override;
    float getParameter(int32_t paramId) const override;
    void prepare(int32_t sampleRate, int32_t channelCount) override;
    void reset() override;

private:
    struct Band {
        std::atomic<float> frequency{1000.0f};
        std::atomic<float> gain{0.0f};
        std::atomic<float> q{0.707f};
        std::atomic<float> type{static_cast<float>(BiquadType::Peak)};
        std::atomic<bool> dirty{true};
        std::vector<Biquad> biquads; // one per channel
    };

    std::array<Band, kMaxEQBands> bands_;
};

} // namespace klarinet
