#include "Pan.h"

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

namespace klarinet {

Pan::Pan() : AudioEffect(EffectType::Pan) {}

void Pan::process(float* buffer, int32_t numFrames, int32_t channelCount) {
    if (!isEnabled()) return;

    // Pan only applies to stereo or higher
    if (channelCount < 2) return;

    float p = pan_.load(std::memory_order_relaxed);
    float angle = (p + 1.0f) * static_cast<float>(M_PI) / 4.0f;
    float gainL = std::cos(angle);
    float gainR = std::sin(angle);

    for (int32_t frame = 0; frame < numFrames; ++frame) {
        int32_t idx = frame * channelCount;
        buffer[idx]     *= gainL;
        buffer[idx + 1] *= gainR;
        // Channels beyond 2 are left unchanged
    }
}

void Pan::setParameter(int32_t paramId, float value) {
    if (paramId == PanParams::kPan) {
        pan_.store(value, std::memory_order_relaxed);
    }
}

float Pan::getParameter(int32_t paramId) const {
    if (paramId == PanParams::kPan) {
        return pan_.load(std::memory_order_relaxed);
    }
    return 0.0f;
}

void Pan::prepare(int32_t sampleRate, int32_t channelCount) {
    sampleRate_ = sampleRate;
    channelCount_ = channelCount;
}

void Pan::reset() {
    pan_.store(0.0f, std::memory_order_relaxed);
}

} // namespace klarinet
