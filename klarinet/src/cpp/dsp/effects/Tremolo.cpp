#include "Tremolo.h"

namespace klarinet {

Tremolo::Tremolo() : AudioEffect(EffectType::Tremolo) {}

void Tremolo::process(float* buffer, int32_t numFrames, int32_t channelCount) {
    if (!isEnabled()) return;

    float depth = depth_.load(std::memory_order_relaxed);

    for (int32_t frame = 0; frame < numFrames; ++frame) {
        // Advance the LFO and get the current sample [-1, +1].
        float lfoVal = lfo_.nextSample();

        // Compute the modulation gain.
        // mod = (1 - depth) + depth * (lfo * 0.5 + 0.5)
        // This oscillates between (1 - depth) and 1.0.
        // At depth = 0: mod = 1.0 (no effect).
        // At depth = 1: mod oscillates between 0.0 and 1.0 (full tremolo).
        float mod = (1.0f - depth) + depth * (lfoVal * 0.5f + 0.5f);

        // Apply the same modulation gain to all channels in this frame.
        for (int32_t ch = 0; ch < channelCount; ++ch) {
            buffer[frame * channelCount + ch] *= mod;
        }
    }
}

void Tremolo::setParameter(int32_t paramId, float value) {
    switch (paramId) {
        case TremoloParams::kRateHz:
            rateHz_.store(value, std::memory_order_relaxed);
            lfo_.setFrequency(value);
            break;
        case TremoloParams::kDepth:
            depth_.store(value, std::memory_order_relaxed);
            break;
        default:
            break;
    }
}

float Tremolo::getParameter(int32_t paramId) const {
    switch (paramId) {
        case TremoloParams::kRateHz:
            return rateHz_.load(std::memory_order_relaxed);
        case TremoloParams::kDepth:
            return depth_.load(std::memory_order_relaxed);
        default:
            return 0.0f;
    }
}

void Tremolo::prepare(int32_t sampleRate, int32_t channelCount) {
    sampleRate_ = sampleRate;
    channelCount_ = channelCount;
    lfo_.prepare(static_cast<float>(sampleRate));
    lfo_.setFrequency(rateHz_.load(std::memory_order_relaxed));
}

void Tremolo::reset() {
    lfo_.reset();
    rateHz_.store(5.0f, std::memory_order_relaxed);
    depth_.store(0.5f, std::memory_order_relaxed);
}

} // namespace klarinet
