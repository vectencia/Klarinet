#include "Gain.h"

namespace klarinet {

Gain::Gain() : AudioEffect(EffectType::Gain) {}

void Gain::process(float* buffer, int32_t numFrames, int32_t channelCount) {
    if (!isEnabled()) return;

    float db = gainDb_.load(std::memory_order_relaxed);

    // Convert decibels to a linear amplitude multiplier.
    // Formula: linear = 10^(dB / 20)
    // At 0 dB this yields 1.0 (unity gain).
    float linear = std::pow(10.0f, db / 20.0f);

    int32_t totalSamples = numFrames * channelCount;
    for (int32_t i = 0; i < totalSamples; ++i) {
        buffer[i] *= linear;
    }
}

void Gain::setParameter(int32_t paramId, float value) {
    if (paramId == GainParams::kGainDb) {
        gainDb_.store(value, std::memory_order_relaxed);
    }
}

float Gain::getParameter(int32_t paramId) const {
    if (paramId == GainParams::kGainDb) {
        return gainDb_.load(std::memory_order_relaxed);
    }
    return 0.0f;
}

void Gain::prepare(int32_t sampleRate, int32_t channelCount) {
    sampleRate_ = sampleRate;
    channelCount_ = channelCount;
}

void Gain::reset() {
    gainDb_.store(0.0f, std::memory_order_relaxed);
}

} // namespace klarinet
