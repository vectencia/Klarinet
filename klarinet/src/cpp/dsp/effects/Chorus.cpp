#include "Chorus.h"

namespace klarinet {

Chorus::Chorus() : AudioEffect(EffectType::Chorus) {}

void Chorus::process(float* buffer, int32_t numFrames, int32_t channelCount) {
    if (!isEnabled()) return;

    float depth = depth_.load(std::memory_order_relaxed);
    float mix   = wetDryMix_.load(std::memory_order_relaxed);
    float sr    = static_cast<float>(sampleRate_);

    for (int32_t frame = 0; frame < numFrames; ++frame) {
        // Advance the LFO and get the current sample [-1, +1].
        float lfoVal = lfo_.nextSample();

        // Compute modulated delay time in samples.
        // LFO is mapped from [-1, +1] to [0, 1] via (lfo * 0.5 + 0.5).
        // Delay sweeps from kBaseDelayMs to (1 + depth) * kBaseDelayMs.
        float delaySamples = (kBaseDelayMs + depth * kBaseDelayMs * (lfoVal * 0.5f + 0.5f))
                             * sr / 1000.0f;

        for (int32_t ch = 0; ch < channelCount; ++ch) {
            int32_t idx = frame * channelCount + ch;
            float dry = buffer[idx];

            // Write the dry sample into the delay line.
            delayLines_[ch].write(dry);

            // Read the delayed (wet) sample with linear interpolation.
            float wet = delayLines_[ch].readLinear(delaySamples);

            // Mix dry and wet signals.
            buffer[idx] = dry * (1.0f - mix) + wet * mix;
        }
    }
}

void Chorus::setParameter(int32_t paramId, float value) {
    switch (paramId) {
        case ChorusParams::kRateHz:
            rateHz_.store(value, std::memory_order_relaxed);
            lfo_.setFrequency(value);
            break;
        case ChorusParams::kDepth:
            depth_.store(value, std::memory_order_relaxed);
            break;
        case ChorusParams::kWetDryMix:
            wetDryMix_.store(value, std::memory_order_relaxed);
            break;
        default:
            break;
    }
}

float Chorus::getParameter(int32_t paramId) const {
    switch (paramId) {
        case ChorusParams::kRateHz:
            return rateHz_.load(std::memory_order_relaxed);
        case ChorusParams::kDepth:
            return depth_.load(std::memory_order_relaxed);
        case ChorusParams::kWetDryMix:
            return wetDryMix_.load(std::memory_order_relaxed);
        default:
            return 0.0f;
    }
}

void Chorus::prepare(int32_t sampleRate, int32_t channelCount) {
    sampleRate_ = sampleRate;
    channelCount_ = channelCount;

    // Allocate delay lines with capacity for kMaxCapacityMs of audio.
    int32_t maxCapacity = static_cast<int32_t>(kMaxCapacityMs * sampleRate / 1000.0f);

    delayLines_.resize(channelCount);
    for (auto& dl : delayLines_) {
        dl.setSize(maxCapacity);
        dl.clear();
    }

    lfo_.prepare(static_cast<float>(sampleRate));
    lfo_.setFrequency(rateHz_.load(std::memory_order_relaxed));
}

void Chorus::reset() {
    lfo_.reset();
    for (auto& dl : delayLines_) {
        dl.clear();
    }
    rateHz_.store(1.0f, std::memory_order_relaxed);
    depth_.store(0.5f, std::memory_order_relaxed);
    wetDryMix_.store(0.5f, std::memory_order_relaxed);
}

} // namespace klarinet
