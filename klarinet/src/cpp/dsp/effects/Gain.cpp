#include "Gain.h"

#include <cstdint>

namespace klarinet {

namespace {

float dbToLinear(float db) {
    return std::pow(10.0f, db / 20.0f);
}

int32_t fadeLengthFrames(float fadeMs, int32_t sampleRate) {
    if (fadeMs <= 0.0f || sampleRate <= 0) {
        return 0;
    }
    const double frames = static_cast<double>(fadeMs) * 0.001 * static_cast<double>(sampleRate);
    if (frames >= static_cast<double>(INT32_MAX)) {
        return INT32_MAX;
    }
    const int32_t length = static_cast<int32_t>(frames);
    return length < 1 ? 0 : length;
}

} // namespace

Gain::Gain() : AudioEffect(EffectType::Gain) {}

void Gain::process(float* buffer, int32_t numFrames, int32_t channelCount) {
    if (!isEnabled()) return;
    if (numFrames <= 0 || channelCount <= 0) return;

    const uint32_t gen = generation_.load(std::memory_order_acquire);
    if (gen != seenGeneration_) {
        seenGeneration_ = gen;
        const float fadeMs = fadeMs_.load(std::memory_order_relaxed);
        rampStartLinear_ = currentLinear_;
        rampTargetLinear_ = targetLinear_.load(std::memory_order_relaxed);
        rampLength_ = fadeLengthFrames(fadeMs, sampleRate_);
        if (rampLength_ < 1) {
            rampLength_ = 0;
            rampPosition_ = 0;
            currentLinear_ = rampTargetLinear_;
        } else {
            rampPosition_ = 0;
        }
    }

    if (rampLength_ <= 0 || rampPosition_ >= rampLength_) {
        const float linear = currentLinear_;
        const int32_t totalSamples = numFrames * channelCount;
        for (int32_t i = 0; i < totalSamples; ++i) {
            buffer[i] *= linear;
        }
        return;
    }

    for (int32_t frame = 0; frame < numFrames; ++frame) {
        float linear;
        if (rampPosition_ >= rampLength_) {
            linear = rampTargetLinear_;
        } else {
            rampPosition_ += 1;
            const float t = static_cast<float>(rampPosition_) / static_cast<float>(rampLength_);
            linear = rampStartLinear_ + (rampTargetLinear_ - rampStartLinear_) * t;
        }
        currentLinear_ = linear;
        const int32_t base = frame * channelCount;
        for (int32_t ch = 0; ch < channelCount; ++ch) {
            buffer[base + ch] *= linear;
        }
    }
}

void Gain::setParameter(int32_t paramId, float value) {
    if (paramId == GainParams::kGainDb) {
        gainDb_.store(value, std::memory_order_relaxed);
        targetLinear_.store(dbToLinear(value), std::memory_order_relaxed);
        generation_.fetch_add(1, std::memory_order_release);
    } else if (paramId == GainParams::kFadeMs) {
        const float ms = value > 0.0f ? value : 0.0f;
        fadeMs_.store(ms, std::memory_order_relaxed);
    }
}

float Gain::getParameter(int32_t paramId) const {
    if (paramId == GainParams::kGainDb) {
        return gainDb_.load(std::memory_order_relaxed);
    }
    if (paramId == GainParams::kFadeMs) {
        return fadeMs_.load(std::memory_order_relaxed);
    }
    return 0.0f;
}

void Gain::prepare(int32_t sampleRate, int32_t channelCount) {
    sampleRate_ = sampleRate;
    channelCount_ = channelCount;
}

void Gain::reset() {
    gainDb_.store(0.0f, std::memory_order_relaxed);
    targetLinear_.store(1.0f, std::memory_order_relaxed);
    fadeMs_.store(0.0f, std::memory_order_relaxed);
    generation_.fetch_add(1, std::memory_order_release);
}

} // namespace klarinet
