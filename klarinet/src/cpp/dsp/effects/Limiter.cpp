#include "Limiter.h"
#include <algorithm>

namespace klarinet {

Limiter::Limiter() : AudioEffect(EffectType::Limiter) {}

void Limiter::process(float* buffer, int32_t numFrames, int32_t channelCount) {
    if (!isEnabled()) return;

    float threshDb = threshold_.load(std::memory_order_relaxed);
    float threshLinear = std::pow(10.0f, threshDb / 20.0f);

    for (int32_t frame = 0; frame < numFrames; ++frame) {
        // Detect peak across all channels for this frame
        float peak = 0.0f;
        for (int32_t ch = 0; ch < channelCount; ++ch) {
            float absSample = std::fabs(buffer[frame * channelCount + ch]);
            if (absSample > peak) {
                peak = absSample;
            }
        }

        // Track envelope
        float envLevel = envFollower_.process(peak);

        // Brick-wall limiting: if envelope exceeds threshold, reduce gain
        float gainLinear = 1.0f;
        if (envLevel > threshLinear) {
            gainLinear = threshLinear / envLevel;
        }

        // Apply gain
        for (int32_t ch = 0; ch < channelCount; ++ch) {
            buffer[frame * channelCount + ch] *= gainLinear;
        }
    }
}

void Limiter::setParameter(int32_t paramId, float value) {
    switch (paramId) {
        case LimiterParams::kThreshold:
            threshold_.store(value, std::memory_order_relaxed);
            break;
        case LimiterParams::kReleaseMs:
            releaseMs_.store(value, std::memory_order_relaxed);
            envFollower_.setReleaseMs(value);
            break;
        default:
            break;
    }
}

float Limiter::getParameter(int32_t paramId) const {
    switch (paramId) {
        case LimiterParams::kThreshold:
            return threshold_.load(std::memory_order_relaxed);
        case LimiterParams::kReleaseMs:
            return releaseMs_.load(std::memory_order_relaxed);
        default:
            return 0.0f;
    }
}

void Limiter::prepare(int32_t sampleRate, int32_t channelCount) {
    sampleRate_ = sampleRate;
    channelCount_ = channelCount;
    envFollower_.prepare(static_cast<float>(sampleRate));
    envFollower_.setAttackMs(0.1f);  // Near-instant attack for brick-wall limiting
    envFollower_.setReleaseMs(releaseMs_.load(std::memory_order_relaxed));
}

void Limiter::reset() {
    envFollower_.reset();
}

} // namespace klarinet
