#pragma once
#include <atomic>
#include <cstdint>
#include <vector>
#include "AudioEffect.h"
#include "primitives/Biquad.h"

namespace klarinet {

namespace HPFParams {
    constexpr int32_t kCutoffHz  = 0;
    constexpr int32_t kResonance = 1;
}

class HighPassFilter : public AudioEffect {
public:
    HighPassFilter();
    ~HighPassFilter() override = default;

    void process(float* buffer, int32_t numFrames, int32_t channelCount) override;
    void setParameter(int32_t paramId, float value) override;
    float getParameter(int32_t paramId) const override;
    void prepare(int32_t sampleRate, int32_t channelCount) override;
    void reset() override;

private:
    std::atomic<float> cutoffHz_{20000.0f};
    std::atomic<float> resonance_{0.707f};
    std::atomic<bool> dirty_{true};
    std::vector<Biquad> biquads_;
};

} // namespace klarinet
