#pragma once
#include <atomic>
#include <cmath>
#include <cstdint>
#include "AudioEffect.h"
#include "primitives/EnvelopeFollower.h"

namespace klarinet {

namespace NoiseGateParams {
    constexpr int32_t kThreshold = 0;
    constexpr int32_t kAttackMs  = 1;
    constexpr int32_t kReleaseMs = 2;
    constexpr int32_t kHoldMs    = 3;
}

class NoiseGate : public AudioEffect {
public:
    NoiseGate();
    ~NoiseGate() override = default;

    void process(float* buffer, int32_t numFrames, int32_t channelCount) override;
    void setParameter(int32_t paramId, float value) override;
    float getParameter(int32_t paramId) const override;
    void prepare(int32_t sampleRate, int32_t channelCount) override;
    void reset() override;

private:
    std::atomic<float> threshold_{-40.0f};
    std::atomic<float> attackMs_{1.0f};
    std::atomic<float> releaseMs_{50.0f};
    std::atomic<float> holdMs_{10.0f};

    EnvelopeFollower envFollower_;
    float gateGain_ = 0.0f;      // Current gate gain [0, 1]
    int32_t holdCounter_ = 0;     // Samples remaining in hold phase
    int32_t holdSamples_ = 0;     // Hold duration in samples
};

} // namespace klarinet
