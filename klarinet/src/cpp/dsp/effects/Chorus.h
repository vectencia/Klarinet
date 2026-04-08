#pragma once
#include <atomic>
#include <cstdint>
#include <vector>
#include "AudioEffect.h"
#include "primitives/LFO.h"
#include "primitives/CircularBuffer.h"

namespace klarinet {

namespace ChorusParams {
    constexpr int32_t kRateHz    = 0;
    constexpr int32_t kDepth     = 1;
    constexpr int32_t kWetDryMix = 2;
}

class Chorus : public AudioEffect {
public:
    Chorus();
    ~Chorus() override = default;

    void process(float* buffer, int32_t numFrames, int32_t channelCount) override;
    void setParameter(int32_t paramId, float value) override;
    float getParameter(int32_t paramId) const override;
    void prepare(int32_t sampleRate, int32_t channelCount) override;
    void reset() override;

private:
    static constexpr float kBaseDelayMs = 7.0f;
    static constexpr float kMaxCapacityMs = 50.0f;

    LFO lfo_;
    std::vector<CircularBuffer> delayLines_;

    std::atomic<float> rateHz_{1.0f};
    std::atomic<float> depth_{0.5f};
    std::atomic<float> wetDryMix_{0.5f};
};

} // namespace klarinet
