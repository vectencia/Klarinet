#include "Flanger.h"

namespace klarinet {

Flanger::Flanger() : AudioEffect(EffectType::Flanger) {}

void Flanger::process(float* buffer, int32_t numFrames, int32_t channelCount) {
    if (!isEnabled()) return;

    float depth    = depth_.load(std::memory_order_relaxed);
    float feedback = feedback_.load(std::memory_order_relaxed);
    float mix      = wetDryMix_.load(std::memory_order_relaxed);
    float sr       = static_cast<float>(sampleRate_);

    for (int32_t frame = 0; frame < numFrames; ++frame) {
        // Advance the LFO and get the current sample [-1, +1].
        float lfoVal = lfo_.nextSample();

        // Compute modulated delay time in samples.
        // LFO is mapped from [-1, +1] to [0, 1] via (lfo * 0.5 + 0.5).
        // Delay sweeps from 0 to depth * kModDelayMs (up to 5 ms).
        float delaySamples = depth * kModDelayMs * (lfoVal * 0.5f + 0.5f) * sr / 1000.0f;

        for (int32_t ch = 0; ch < channelCount; ++ch) {
            int32_t idx = frame * channelCount + ch;
            float dry = buffer[idx];

            // Read delayed sample with linear interpolation.
            float delayed = delayLines_[ch].readLinear(delaySamples);

            // Write input + feedback-scaled delayed signal into the delay line.
            // The feedback creates comb-filter resonances that sweep with the LFO.
            delayLines_[ch].write(dry + delayed * feedback);

            // Mix dry and wet signals.
            buffer[idx] = dry * (1.0f - mix) + delayed * mix;
        }
    }
}

void Flanger::setParameter(int32_t paramId, float value) {
    switch (paramId) {
        case FlangerParams::kRateHz:
            rateHz_.store(value, std::memory_order_relaxed);
            lfo_.setFrequency(value);
            break;
        case FlangerParams::kDepth:
            depth_.store(value, std::memory_order_relaxed);
            break;
        case FlangerParams::kFeedback:
            feedback_.store(value, std::memory_order_relaxed);
            break;
        case FlangerParams::kWetDryMix:
            wetDryMix_.store(value, std::memory_order_relaxed);
            break;
        default:
            break;
    }
}

float Flanger::getParameter(int32_t paramId) const {
    switch (paramId) {
        case FlangerParams::kRateHz:
            return rateHz_.load(std::memory_order_relaxed);
        case FlangerParams::kDepth:
            return depth_.load(std::memory_order_relaxed);
        case FlangerParams::kFeedback:
            return feedback_.load(std::memory_order_relaxed);
        case FlangerParams::kWetDryMix:
            return wetDryMix_.load(std::memory_order_relaxed);
        default:
            return 0.0f;
    }
}

void Flanger::prepare(int32_t sampleRate, int32_t channelCount) {
    sampleRate_ = sampleRate;
    channelCount_ = channelCount;

    // Allocate delay lines with capacity for kMaxCapacityMs of audio.
    int32_t maxCapacity = static_cast<int32_t>(kMaxCapacityMs * sampleRate / 1000.0f);

    delayLines_.resize(channelCount);
    for (auto& dl : delayLines_) {
        dl.setSize(maxCapacity);
        dl.clear();
    }

    feedbackState_.resize(channelCount, 0.0f);

    lfo_.prepare(static_cast<float>(sampleRate));
    lfo_.setFrequency(rateHz_.load(std::memory_order_relaxed));
}

void Flanger::reset() {
    lfo_.reset();
    for (auto& dl : delayLines_) {
        dl.clear();
    }
    std::fill(feedbackState_.begin(), feedbackState_.end(), 0.0f);
    rateHz_.store(0.5f, std::memory_order_relaxed);
    depth_.store(0.7f, std::memory_order_relaxed);
    feedback_.store(0.5f, std::memory_order_relaxed);
    wetDryMix_.store(0.5f, std::memory_order_relaxed);
}

} // namespace klarinet
