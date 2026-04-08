#include "Compressor.h"
#include <algorithm>

namespace klarinet {

Compressor::Compressor() : AudioEffect(EffectType::Compressor) {}

void Compressor::process(float* buffer, int32_t numFrames, int32_t channelCount) {
    if (!isEnabled()) return;

    float thresh  = threshold_.load(std::memory_order_relaxed);
    float ratio   = ratio_.load(std::memory_order_relaxed);
    float makeup  = makeupGain_.load(std::memory_order_relaxed);
    float makeupLinear = std::pow(10.0f, makeup / 20.0f);

    for (int32_t frame = 0; frame < numFrames; ++frame) {
        // Detect peak across all channels for this frame
        float peak = 0.0f;
        for (int32_t ch = 0; ch < channelCount; ++ch) {
            float sample = buffer[frame * channelCount + ch];
            float absSample = std::fabs(sample);
            if (absSample > peak) {
                peak = absSample;
            }
        }

        // Track the peak with the envelope follower
        float envLevel = envFollower_.process(peak);

        // Convert envelope to dB
        float envLevelDb = (envLevel > 1e-10f)
            ? 20.0f * std::log10(envLevel)
            : -200.0f;

        // Compute gain reduction
        float gainLinear = 1.0f;
        if (envLevelDb > thresh) {
            float overDb = envLevelDb - thresh;
            float compressedDb = thresh + overDb / ratio;
            float gainReductionDb = compressedDb - envLevelDb;
            gainLinear = std::pow(10.0f, gainReductionDb / 20.0f);
        }

        // Apply gain reduction and makeup gain
        for (int32_t ch = 0; ch < channelCount; ++ch) {
            buffer[frame * channelCount + ch] *= gainLinear * makeupLinear;
        }
    }
}

void Compressor::setParameter(int32_t paramId, float value) {
    switch (paramId) {
        case CompressorParams::kThreshold:
            threshold_.store(value, std::memory_order_relaxed);
            break;
        case CompressorParams::kRatio:
            ratio_.store(std::max(1.0f, value), std::memory_order_relaxed);
            break;
        case CompressorParams::kAttackMs:
            attackMs_.store(value, std::memory_order_relaxed);
            envFollower_.setAttackMs(value);
            break;
        case CompressorParams::kReleaseMs:
            releaseMs_.store(value, std::memory_order_relaxed);
            envFollower_.setReleaseMs(value);
            break;
        case CompressorParams::kMakeupGain:
            makeupGain_.store(value, std::memory_order_relaxed);
            break;
        default:
            break;
    }
}

float Compressor::getParameter(int32_t paramId) const {
    switch (paramId) {
        case CompressorParams::kThreshold:
            return threshold_.load(std::memory_order_relaxed);
        case CompressorParams::kRatio:
            return ratio_.load(std::memory_order_relaxed);
        case CompressorParams::kAttackMs:
            return attackMs_.load(std::memory_order_relaxed);
        case CompressorParams::kReleaseMs:
            return releaseMs_.load(std::memory_order_relaxed);
        case CompressorParams::kMakeupGain:
            return makeupGain_.load(std::memory_order_relaxed);
        default:
            return 0.0f;
    }
}

void Compressor::prepare(int32_t sampleRate, int32_t channelCount) {
    sampleRate_ = sampleRate;
    channelCount_ = channelCount;
    envFollower_.prepare(static_cast<float>(sampleRate));
    envFollower_.setAttackMs(attackMs_.load(std::memory_order_relaxed));
    envFollower_.setReleaseMs(releaseMs_.load(std::memory_order_relaxed));
}

void Compressor::reset() {
    envFollower_.reset();
}

} // namespace klarinet
