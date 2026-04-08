#include "HighPassFilter.h"

namespace klarinet {

HighPassFilter::HighPassFilter() : AudioEffect(EffectType::HighPassFilter) {}

void HighPassFilter::process(float* buffer, int32_t numFrames, int32_t channelCount) {
    if (!isEnabled()) return;

    // Recompute biquad coefficients if parameters have changed.
    if (dirty_.exchange(false, std::memory_order_relaxed)) {
        float cutoff = cutoffHz_.load(std::memory_order_relaxed);
        float q = resonance_.load(std::memory_order_relaxed);
        for (auto& bq : biquads_) {
            bq.configure(BiquadType::HighPass, cutoff, q, 0.0f,
                         static_cast<float>(sampleRate_));
        }
    }

    // Process every sample through the biquad for its respective channel.
    int32_t totalSamples = numFrames * channelCount;
    for (int32_t i = 0; i < totalSamples; ++i) {
        int32_t ch = i % channelCount;
        buffer[i] = biquads_[ch].process(buffer[i]);
    }
}

void HighPassFilter::setParameter(int32_t paramId, float value) {
    switch (paramId) {
        case HPFParams::kCutoffHz:
            cutoffHz_.store(value, std::memory_order_relaxed);
            dirty_.store(true, std::memory_order_relaxed);
            break;
        case HPFParams::kResonance:
            resonance_.store(value, std::memory_order_relaxed);
            dirty_.store(true, std::memory_order_relaxed);
            break;
        default:
            break;
    }
}

float HighPassFilter::getParameter(int32_t paramId) const {
    switch (paramId) {
        case HPFParams::kCutoffHz:
            return cutoffHz_.load(std::memory_order_relaxed);
        case HPFParams::kResonance:
            return resonance_.load(std::memory_order_relaxed);
        default:
            return 0.0f;
    }
}

void HighPassFilter::prepare(int32_t sampleRate, int32_t channelCount) {
    sampleRate_ = sampleRate;
    channelCount_ = channelCount;
    biquads_.resize(channelCount);
    dirty_.store(true, std::memory_order_relaxed);
}

void HighPassFilter::reset() {
    cutoffHz_.store(20000.0f, std::memory_order_relaxed);
    resonance_.store(0.707f, std::memory_order_relaxed);
    for (auto& bq : biquads_) {
        bq.reset();
    }
    dirty_.store(true, std::memory_order_relaxed);
}

} // namespace klarinet
