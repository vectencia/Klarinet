#pragma once
#include <atomic>
#include <cmath>
#include <cstdint>
#include "AudioEffect.h"
#include "primitives/EnvelopeFollower.h"

namespace klarinet {

namespace CompressorParams {
    constexpr int32_t kThreshold  = 0;
    constexpr int32_t kRatio      = 1;
    constexpr int32_t kAttackMs   = 2;
    constexpr int32_t kReleaseMs  = 3;
    constexpr int32_t kMakeupGain = 4;
}

class Compressor : public AudioEffect {
public:
    Compressor();
    ~Compressor() override = default;

    void process(float* buffer, int32_t numFrames, int32_t channelCount) override;
    void setParameter(int32_t paramId, float value) override;
    float getParameter(int32_t paramId) const override;
    void prepare(int32_t sampleRate, int32_t channelCount) override;
    void reset() override;

private:
    std::atomic<float> threshold_{-20.0f};
    std::atomic<float> ratio_{4.0f};
    std::atomic<float> attackMs_{10.0f};
    std::atomic<float> releaseMs_{100.0f};
    std::atomic<float> makeupGain_{0.0f};

    EnvelopeFollower envFollower_;
};

} // namespace klarinet
