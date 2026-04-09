#include "Reverb.h"
#include <algorithm>
#include <cmath>

namespace klarinet {

Reverb::Reverb() : AudioEffect(EffectType::Reverb) {}

void Reverb::process(float* buffer, int32_t numFrames, int32_t channelCount) {
    if (!isEnabled()) return;

    float roomSize = roomSize_.load(std::memory_order_relaxed);
    float damp = damping_.load(std::memory_order_relaxed);
    float mix = std::clamp(wetDryMix_.load(std::memory_order_relaxed), 0.0f, 1.0f);
    float width = std::clamp(width_.load(std::memory_order_relaxed), 0.0f, 1.0f);

    float feedback = roomSize * 0.28f + 0.7f;
    float damp1 = damp;
    float damp2 = 1.0f - damp;

    float wet1 = mix * (width * 0.5f + 0.5f);
    float wet2 = mix * ((1.0f - width) * 0.5f);
    float dry = 1.0f - mix;

    bool stereo = (channelCount >= 2);

    for (int32_t frame = 0; frame < numFrames; ++frame) {
        float inputL, inputR;

        if (stereo) {
            inputL = buffer[frame * channelCount + 0];
            inputR = buffer[frame * channelCount + 1];
        } else {
            inputL = buffer[frame * channelCount + 0];
            inputR = inputL;
        }

        // Mix to mono and scale
        float inputMono = (inputL + inputR) * 0.5f * kFixedGain;

        float outL = 0.0f;
        float outR = 0.0f;

        // Parallel comb filters
        for (int i = 0; i < kNumCombs; ++i) {
            // Left channel combs
            float combOutL = combBuffersL_[i].read(
                static_cast<float>(combBuffersL_[i].getSize() - 1));
            filterStoreL_[i] = damp2 * combOutL + damp1 * filterStoreL_[i];
            combBuffersL_[i].write(inputMono + filterStoreL_[i] * feedback);
            outL += combOutL;

            // Right channel combs
            float combOutR = combBuffersR_[i].read(
                static_cast<float>(combBuffersR_[i].getSize() - 1));
            filterStoreR_[i] = damp2 * combOutR + damp1 * filterStoreR_[i];
            combBuffersR_[i].write(inputMono + filterStoreR_[i] * feedback);
            outR += combOutR;
        }

        // Series allpass filters
        for (int i = 0; i < kNumAllpasses; ++i) {
            float bufOutL = allpassBuffersL_[i].read(
                static_cast<float>(allpassBuffersL_[i].getSize() - 1));
            allpassBuffersL_[i].write(outL + bufOutL * kAllpassFeedback);
            outL = bufOutL - outL;

            float bufOutR = allpassBuffersR_[i].read(
                static_cast<float>(allpassBuffersR_[i].getSize() - 1));
            allpassBuffersR_[i].write(outR + bufOutR * kAllpassFeedback);
            outR = bufOutR - outR;
        }

        if (stereo) {
            buffer[frame * channelCount + 0] = inputL * dry + outL * wet1 + outR * wet2;
            buffer[frame * channelCount + 1] = inputR * dry + outR * wet1 + outL * wet2;
        } else {
            buffer[frame * channelCount + 0] = inputL * dry + outL * mix;
        }
    }
}

void Reverb::setParameter(int32_t paramId, float value) {
    switch (paramId) {
        case ReverbParams::kRoomSize:
            roomSize_.store(value, std::memory_order_relaxed);
            break;
        case ReverbParams::kDamping:
            damping_.store(value, std::memory_order_relaxed);
            break;
        case ReverbParams::kWetDryMix:
            wetDryMix_.store(value, std::memory_order_relaxed);
            break;
        case ReverbParams::kWidth:
            width_.store(value, std::memory_order_relaxed);
            break;
        default:
            break;
    }
}

float Reverb::getParameter(int32_t paramId) const {
    switch (paramId) {
        case ReverbParams::kRoomSize:
            return roomSize_.load(std::memory_order_relaxed);
        case ReverbParams::kDamping:
            return damping_.load(std::memory_order_relaxed);
        case ReverbParams::kWetDryMix:
            return wetDryMix_.load(std::memory_order_relaxed);
        case ReverbParams::kWidth:
            return width_.load(std::memory_order_relaxed);
        default:
            return 0.0f;
    }
}

void Reverb::prepare(int32_t sampleRate, int32_t channelCount) {
    sampleRate_ = sampleRate;
    channelCount_ = channelCount;

    float rateScale = static_cast<float>(sampleRate) / kReferenceRate;

    for (int i = 0; i < kNumCombs; ++i) {
        int32_t sizeL = static_cast<int32_t>(
            static_cast<float>(kCombTunings[i]) * rateScale);
        int32_t sizeR = static_cast<int32_t>(
            static_cast<float>(kCombTunings[i] + kStereoSpread) * rateScale);

        combBuffersL_[i].setSize(sizeL);
        combBuffersR_[i].setSize(sizeR);
        combBuffersL_[i].clear();
        combBuffersR_[i].clear();
    }

    for (int i = 0; i < kNumAllpasses; ++i) {
        int32_t sizeL = static_cast<int32_t>(
            static_cast<float>(kAllpassTunings[i]) * rateScale);
        int32_t sizeR = static_cast<int32_t>(
            static_cast<float>(kAllpassTunings[i] + kStereoSpread) * rateScale);

        allpassBuffersL_[i].setSize(sizeL);
        allpassBuffersR_[i].setSize(sizeR);
        allpassBuffersL_[i].clear();
        allpassBuffersR_[i].clear();
    }

    filterStoreL_.fill(0.0f);
    filterStoreR_.fill(0.0f);
}

void Reverb::reset() {
    for (int i = 0; i < kNumCombs; ++i) {
        combBuffersL_[i].clear();
        combBuffersR_[i].clear();
    }
    for (int i = 0; i < kNumAllpasses; ++i) {
        allpassBuffersL_[i].clear();
        allpassBuffersR_[i].clear();
    }
    filterStoreL_.fill(0.0f);
    filterStoreR_.fill(0.0f);
}

} // namespace klarinet
