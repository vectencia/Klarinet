#pragma once
#include <atomic>
#include <cmath>
#include <cstdint>
#include "AudioEffect.h"

namespace klarinet {

namespace PanParams {
    constexpr int32_t kPan = 0;
}

class Pan : public AudioEffect {
public:
    Pan();
    ~Pan() override = default;

    void process(float* buffer, int32_t numFrames, int32_t channelCount) override;
    void setParameter(int32_t paramId, float value) override;
    float getParameter(int32_t paramId) const override;
    void prepare(int32_t sampleRate, int32_t channelCount) override;
    void reset() override;

private:
    std::atomic<float> pan_{0.0f};
};

} // namespace klarinet
