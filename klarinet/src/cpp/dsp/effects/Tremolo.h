#pragma once
#include <atomic>
#include <cstdint>
#include "AudioEffect.h"
#include "primitives/LFO.h"

namespace klarinet {

namespace TremoloParams {
    constexpr int32_t kRateHz = 0;
    constexpr int32_t kDepth  = 1;
}

class Tremolo : public AudioEffect {
public:
    Tremolo();
    ~Tremolo() override = default;

    void process(float* buffer, int32_t numFrames, int32_t channelCount) override;
    void setParameter(int32_t paramId, float value) override;
    float getParameter(int32_t paramId) const override;
    void prepare(int32_t sampleRate, int32_t channelCount) override;
    void reset() override;

private:
    LFO lfo_;
    std::atomic<float> rateHz_{5.0f};
    std::atomic<float> depth_{0.5f};
};

} // namespace klarinet
