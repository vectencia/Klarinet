#include "Phaser.h"
#include <algorithm>
#include <cmath>

namespace klarinet {

Phaser::Phaser() : AudioEffect(EffectType::Phaser) {}

void Phaser::process(float* buffer, int32_t numFrames, int32_t channelCount) {
    if (!isEnabled()) return;

    float depth    = depth_.load(std::memory_order_relaxed);
    float feedback = feedback_.load(std::memory_order_relaxed);

    // Read and clamp the number of allpass stages to [1, kMaxStages].
    int32_t stages = static_cast<int32_t>(stages_.load(std::memory_order_relaxed));
    stages = std::min(stages, kMaxStages);
    stages = std::max(stages, 1);

    float sr = static_cast<float>(sampleRate_);

    for (int32_t frame = 0; frame < numFrames; ++frame) {
        // Advance the LFO and get the current sample [-1, +1].
        float lfoVal = lfo_.nextSample();

        // Map LFO [-1,1] to the allpass center frequency.
        // lfoUnipolar maps to [0, 1], then scaled by depth.
        // centerFreq sweeps from kMinFreqHz (200 Hz) to
        // kMinFreqHz + (kMaxFreqHz - kMinFreqHz) * depth (up to 4000 Hz).
        float lfoUnipolar = lfoVal * 0.5f + 0.5f;
        float centerFreq = kMinFreqHz + (kMaxFreqHz - kMinFreqHz) * lfoUnipolar * depth;

        for (int32_t ch = 0; ch < channelCount; ++ch) {
            int32_t idx = frame * channelCount + ch;

            // Add feedback-scaled previous allpass output to the input.
            float input = buffer[idx] + feedbackState_[ch] * feedback;

            // Process through the cascaded allpass chain.
            // Each stage is reconfigured to the current LFO-modulated frequency.
            float allpassOut = input;
            for (int32_t s = 0; s < stages; ++s) {
                allpasses_[ch][s].configure(BiquadType::AllPass, centerFreq, 0.707f, 0.0f, sr);
                allpassOut = allpasses_[ch][s].process(allpassOut);
            }

            // Store the allpass output for the next frame's feedback.
            feedbackState_[ch] = allpassOut;

            // Output: sum of input (with feedback) and allpass output.
            // This creates the characteristic notch pattern from phase cancellation.
            buffer[idx] = input + allpassOut;
        }
    }
}

void Phaser::setParameter(int32_t paramId, float value) {
    switch (paramId) {
        case PhaserParams::kRateHz:
            rateHz_.store(value, std::memory_order_relaxed);
            lfo_.setFrequency(value);
            break;
        case PhaserParams::kDepth:
            depth_.store(value, std::memory_order_relaxed);
            break;
        case PhaserParams::kStages:
            stages_.store(value, std::memory_order_relaxed);
            break;
        case PhaserParams::kFeedback:
            feedback_.store(value, std::memory_order_relaxed);
            break;
        default:
            break;
    }
}

float Phaser::getParameter(int32_t paramId) const {
    switch (paramId) {
        case PhaserParams::kRateHz:
            return rateHz_.load(std::memory_order_relaxed);
        case PhaserParams::kDepth:
            return depth_.load(std::memory_order_relaxed);
        case PhaserParams::kStages:
            return stages_.load(std::memory_order_relaxed);
        case PhaserParams::kFeedback:
            return feedback_.load(std::memory_order_relaxed);
        default:
            return 0.0f;
    }
}

void Phaser::prepare(int32_t sampleRate, int32_t channelCount) {
    sampleRate_ = sampleRate;
    channelCount_ = channelCount;

    // Allocate one array of kMaxStages allpass filters per channel.
    allpasses_.resize(channelCount);
    for (auto& channelAllpasses : allpasses_) {
        for (auto& bq : channelAllpasses) {
            bq.reset();
        }
    }

    feedbackState_.resize(channelCount, 0.0f);
    std::fill(feedbackState_.begin(), feedbackState_.end(), 0.0f);

    lfo_.prepare(static_cast<float>(sampleRate));
    lfo_.setFrequency(rateHz_.load(std::memory_order_relaxed));
}

void Phaser::reset() {
    lfo_.reset();
    for (auto& channelAllpasses : allpasses_) {
        for (auto& bq : channelAllpasses) {
            bq.reset();
        }
    }
    std::fill(feedbackState_.begin(), feedbackState_.end(), 0.0f);
    rateHz_.store(0.5f, std::memory_order_relaxed);
    depth_.store(0.5f, std::memory_order_relaxed);
    stages_.store(4.0f, std::memory_order_relaxed);
    feedback_.store(0.3f, std::memory_order_relaxed);
}

} // namespace klarinet
