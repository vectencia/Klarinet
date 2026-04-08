#pragma once
#include <atomic>
#include <cstdint>
#include <vector>
#include "AudioEffect.h"
#include "primitives/Biquad.h"

namespace klarinet {

namespace BPFParams {
    constexpr int32_t kCenterHz  = 0;
    constexpr int32_t kBandwidth = 1;
}

class BandPassFilter : public AudioEffect {
public:
    BandPassFilter();
    ~BandPassFilter() override = default;

    void process(float* buffer, int32_t numFrames, int32_t channelCount) override;
    void setParameter(int32_t paramId, float value) override;
    float getParameter(int32_t paramId) const override;
    void prepare(int32_t sampleRate, int32_t channelCount) override;
    void reset() override;

private:
    std::atomic<float> centerHz_{1000.0f};
    std::atomic<float> bandwidth_{1.0f};
    std::atomic<bool> dirty_{true};
    std::vector<Biquad> biquads_;
};

} // namespace klarinet
