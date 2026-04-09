#pragma once
#include <atomic>
#include <cstdint>
#include <cstring>
#include "AudioEffect.h"

namespace klarinet {

namespace MuteSoloParams {
    constexpr int32_t kMuted = 0;
    constexpr int32_t kSoloed = 1;
}

class MuteSolo : public AudioEffect {
public:
    MuteSolo();
    ~MuteSolo() override = default;

    void process(float* buffer, int32_t numFrames, int32_t channelCount) override;
    void setParameter(int32_t paramId, float value) override;
    float getParameter(int32_t paramId) const override;
    void prepare(int32_t sampleRate, int32_t channelCount) override;
    void reset() override;

private:
    std::atomic<float> muted_{0.0f};
    std::atomic<float> soloed_{0.0f};
};

} // namespace klarinet
