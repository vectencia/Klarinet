#include "NoiseGate.h"
#include <algorithm>

namespace klarinet {

NoiseGate::NoiseGate() : AudioEffect(EffectType::NoiseGate) {}

void NoiseGate::process(float* buffer, int32_t numFrames, int32_t channelCount) {
    if (!isEnabled()) return;

    float threshDb = threshold_.load(std::memory_order_relaxed);

    // Convert threshold from dB to linear: 10^(dB / 20)
    float threshLinear = std::pow(10.0f, threshDb / 20.0f);

    for (int32_t frame = 0; frame < numFrames; ++frame) {
        // Step 1: Detect peak across all channels for this frame.
        float peak = 0.0f;
        for (int32_t ch = 0; ch < channelCount; ++ch) {
            float absSample = std::fabs(buffer[frame * channelCount + ch]);
            if (absSample > peak) {
                peak = absSample;
            }
        }

        // Step 2: Track envelope (smoothed by attack/release).
        float envLevel = envFollower_.process(peak);

        // Step 3: Gate state machine.
        if (envLevel > threshLinear) {
            // Signal above threshold: open gate immediately.
            gateGain_ = 1.0f;
            holdCounter_ = holdSamples_;
        } else if (holdCounter_ > 0) {
            // In hold phase: keep gate open, decrement counter.
            holdCounter_--;
            gateGain_ = 1.0f;
        } else {
            // Below threshold and hold expired: smoothly close gate.
            // Multiply by 0.999 each sample for exponential decay.
            // Reaches -60 dB in ~6900 samples (~144 ms at 48 kHz).
            gateGain_ *= 0.999f;
        }

        // Step 4: Apply gate gain to all channels in this frame.
        for (int32_t ch = 0; ch < channelCount; ++ch) {
            buffer[frame * channelCount + ch] *= gateGain_;
        }
    }
}

void NoiseGate::setParameter(int32_t paramId, float value) {
    switch (paramId) {
        case NoiseGateParams::kThreshold:
            threshold_.store(value, std::memory_order_relaxed);
            break;
        case NoiseGateParams::kAttackMs:
            attackMs_.store(value, std::memory_order_relaxed);
            envFollower_.setAttackMs(value);
            break;
        case NoiseGateParams::kReleaseMs:
            releaseMs_.store(value, std::memory_order_relaxed);
            envFollower_.setReleaseMs(value);
            break;
        case NoiseGateParams::kHoldMs:
            holdMs_.store(value, std::memory_order_relaxed);
            // Convert hold time from milliseconds to samples.
            holdSamples_ = static_cast<int32_t>(value * static_cast<float>(sampleRate_) / 1000.0f);
            break;
        default:
            break;
    }
}

float NoiseGate::getParameter(int32_t paramId) const {
    switch (paramId) {
        case NoiseGateParams::kThreshold:
            return threshold_.load(std::memory_order_relaxed);
        case NoiseGateParams::kAttackMs:
            return attackMs_.load(std::memory_order_relaxed);
        case NoiseGateParams::kReleaseMs:
            return releaseMs_.load(std::memory_order_relaxed);
        case NoiseGateParams::kHoldMs:
            return holdMs_.load(std::memory_order_relaxed);
        default:
            return 0.0f;
    }
}

void NoiseGate::prepare(int32_t sampleRate, int32_t channelCount) {
    sampleRate_ = sampleRate;
    channelCount_ = channelCount;
    envFollower_.prepare(static_cast<float>(sampleRate));
    envFollower_.setAttackMs(attackMs_.load(std::memory_order_relaxed));
    envFollower_.setReleaseMs(releaseMs_.load(std::memory_order_relaxed));
    // Convert hold time from milliseconds to samples: samples = ms * sampleRate / 1000.
    holdSamples_ = static_cast<int32_t>(
        holdMs_.load(std::memory_order_relaxed) * static_cast<float>(sampleRate) / 1000.0f
    );
}

void NoiseGate::reset() {
    envFollower_.reset();
    gateGain_ = 0.0f;
    holdCounter_ = 0;
}

} // namespace klarinet
