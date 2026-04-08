#include "Delay.h"
#include <algorithm>

namespace klarinet {

Delay::Delay() : AudioEffect(EffectType::Delay) {}

void Delay::process(float* buffer, int32_t numFrames, int32_t channelCount) {
    if (!isEnabled()) return;

    // Convert delay time from milliseconds to samples.
    float delaySamples = timeMs_.load(std::memory_order_relaxed) * 0.001f
                         * static_cast<float>(sampleRate_);

    // Clamp feedback to [0, kMaxFeedback] to prevent infinite buildup.
    float fb = std::clamp(feedback_.load(std::memory_order_relaxed), 0.0f, kMaxFeedback);

    // Clamp wet/dry mix to [0, 1].
    float mix = std::clamp(wetDryMix_.load(std::memory_order_relaxed), 0.0f, 1.0f);

    int32_t numChannels = std::min(channelCount, static_cast<int32_t>(delayLines_.size()));

    for (int32_t frame = 0; frame < numFrames; ++frame) {
        for (int32_t ch = 0; ch < numChannels; ++ch) {
            int32_t idx = frame * channelCount + ch;
            float dry = buffer[idx];

            // Read delayed sample using linear interpolation.
            float delayed = delayLines_[ch].readLinear(delaySamples);

            // Write input + feedback-scaled delayed signal into the delay line.
            delayLines_[ch].write(dry + delayed * fb);

            // Mix dry and wet signals for output.
            buffer[idx] = dry * (1.0f - mix) + delayed * mix;
        }
    }
}

void Delay::setParameter(int32_t paramId, float value) {
    switch (paramId) {
        case DelayParams::kTimeMs:
            timeMs_.store(value, std::memory_order_relaxed);
            break;
        case DelayParams::kFeedback:
            feedback_.store(value, std::memory_order_relaxed);
            break;
        case DelayParams::kWetDryMix:
            wetDryMix_.store(value, std::memory_order_relaxed);
            break;
        default:
            break;
    }
}

float Delay::getParameter(int32_t paramId) const {
    switch (paramId) {
        case DelayParams::kTimeMs:
            return timeMs_.load(std::memory_order_relaxed);
        case DelayParams::kFeedback:
            return feedback_.load(std::memory_order_relaxed);
        case DelayParams::kWetDryMix:
            return wetDryMix_.load(std::memory_order_relaxed);
        default:
            return 0.0f;
    }
}

void Delay::prepare(int32_t sampleRate, int32_t channelCount) {
    sampleRate_ = sampleRate;
    channelCount_ = channelCount;

    // Allocate delay lines for the maximum possible delay (2 seconds).
    int32_t maxSamples = static_cast<int32_t>(kMaxDelaySeconds
                                              * static_cast<float>(sampleRate)) + 1;
    delayLines_.clear();
    delayLines_.reserve(channelCount);
    for (int32_t ch = 0; ch < channelCount; ++ch) {
        delayLines_.emplace_back(maxSamples);
    }
}

void Delay::reset() {
    for (auto& dl : delayLines_) {
        dl.clear();
    }
}

} // namespace klarinet
