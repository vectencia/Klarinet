#pragma once
#include <atomic>
#include <vector>
#include <cstdint>
#include "AudioEffect.h"
#include "primitives/CircularBuffer.h"

namespace klarinet {

namespace DelayParams {
    constexpr int32_t kTimeMs = 0;
    constexpr int32_t kFeedback = 1;
    constexpr int32_t kWetDryMix = 2;
}

class Delay : public AudioEffect {
public:
    Delay();
    ~Delay() override = default;

    void process(float* buffer, int32_t numFrames, int32_t channelCount) override;
    void setParameter(int32_t paramId, float value) override;
    float getParameter(int32_t paramId) const override;
    void prepare(int32_t sampleRate, int32_t channelCount) override;
    void reset() override;

private:
    static constexpr float kMaxDelaySeconds = 2.0f;
    static constexpr float kMaxFeedback = 0.95f;

    std::atomic<float> timeMs_{250.0f};
    std::atomic<float> feedback_{0.4f};
    std::atomic<float> wetDryMix_{0.3f};

    std::vector<CircularBuffer> delayLines_;
};

} // namespace klarinet
