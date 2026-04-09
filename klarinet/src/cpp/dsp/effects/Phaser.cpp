#include "Phaser.h"
#include <algorithm>
#include <cmath>

namespace klarinet {

Phaser::Phaser() : AudioEffect(EffectType::Phaser) {}

void Phaser::process(float* buffer, int32_t numFrames, int32_t channelCount) {
    if (!isEnabled()) return;

    float depth    = depth_.load(std::memory_order_relaxed);
    float feedback = feedback_.load(std::memory_order_relaxed);
    int32_t stages = static_cast<int32_t>(stages_.load(std::memory_order_relaxed));
    stages = std::min(stages, kMaxStages);
    stages = std::max(stages, 1);

    float sr = static_cast<float>(sampleRate_);

    for (int32_t frame = 0; frame < numFrames; ++frame) {
        float lfoVal = lfo_.nextSample();

        // Map LFO [-1,1] to frequency range [minFreq, maxFreq] using depth
        float lfoUnipolar = lfoVal * 0.5f + 0.5f;
        float centerFreq = kMinFreqHz + (kMaxFreqHz - kMinFreqHz) * lfoUnipolar * depth;

        for (int32_t ch = 0; ch < channelCount; ++ch) {
            int32_t idx = frame * channelCount + ch;
            float input = buffer[idx] + feedbackState_[ch] * feedback;

            // Configure and process through the allpass chain
            float allpassOut = input;
            for (int32_t s = 0; s < stages; ++s) {
                allpasses_[ch][s].configure(BiquadType::AllPass, centerFreq, 0.707f, 0.0f, sr);
                allpassOut = allpasses_[ch][s].process(allpassOut);
            }

            feedbackState_[ch] = allpassOut;
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
