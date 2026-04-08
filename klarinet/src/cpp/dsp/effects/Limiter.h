#pragma once
#include <atomic>
#include <cmath>
#include <cstdint>
#include "AudioEffect.h"
#include "primitives/EnvelopeFollower.h"

namespace klarinet {

namespace LimiterParams {
    constexpr int32_t kThreshold  = 0;
    constexpr int32_t kReleaseMs  = 1;
}

class Limiter : public AudioEffect {
public:
    Limiter();
    ~Limiter() override = default;

    void process(float* buffer, int32_t numFrames, int32_t channelCount) override;
    void setParameter(int32_t paramId, float value) override;
    float getParameter(int32_t paramId) const override;
    void prepare(int32_t sampleRate, int32_t channelCount) override;
    void reset() override;

private:
    std::atomic<float> threshold_{-1.0f};
    std::atomic<float> releaseMs_{50.0f};

    EnvelopeFollower envFollower_;
};

} // namespace klarinet
