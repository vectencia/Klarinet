#include "ParametricEQ.h"

namespace klarinet {

ParametricEQ::ParametricEQ() : AudioEffect(EffectType::ParametricEQ) {}

void ParametricEQ::process(float* buffer, int32_t numFrames, int32_t channelCount) {
    if (!isEnabled()) return;

    // Recompute biquad coefficients for any bands whose parameters changed.
    for (auto& band : bands_) {
        if (band.dirty.exchange(false, std::memory_order_relaxed)) {
            float freq = band.frequency.load(std::memory_order_relaxed);
            float gain = band.gain.load(std::memory_order_relaxed);
            float q = band.q.load(std::memory_order_relaxed);
            auto type = static_cast<BiquadType>(
                static_cast<int>(band.type.load(std::memory_order_relaxed)));
            for (auto& bq : band.biquads) {
                bq.configure(type, freq, q, gain,
                             static_cast<float>(sampleRate_));
            }
        }
    }

    int32_t totalSamples = numFrames * channelCount;

    // Process each band in series (cascaded EQ).
    for (auto& band : bands_) {
        // Optimization: skip Peak bands with 0 dB gain (they have no effect).
        float gain = band.gain.load(std::memory_order_relaxed);
        auto type = static_cast<BiquadType>(
            static_cast<int>(band.type.load(std::memory_order_relaxed)));
        if (type == BiquadType::Peak && gain == 0.0f) {
            continue;
        }

        // Process every sample through this band's biquad for the appropriate channel.
        for (int32_t i = 0; i < totalSamples; ++i) {
            int32_t ch = i % channelCount;
            buffer[i] = band.biquads[ch].process(buffer[i]);
        }
    }
}

void ParametricEQ::setParameter(int32_t paramId, float value) {
    // Decode band index and parameter offset from the flat parameter ID.
    // paramId = bandIndex * 4 + offset
    int32_t bandIndex = paramId / EQParams::kParamsPerBand;
    int32_t offset = paramId % EQParams::kParamsPerBand;

    if (bandIndex < 0 || bandIndex >= kMaxEQBands) return;

    auto& band = bands_[bandIndex];
    switch (offset) {
        case EQParams::kBandFrequency:
            band.frequency.store(value, std::memory_order_relaxed);
            break;
        case EQParams::kBandGain:
            band.gain.store(value, std::memory_order_relaxed);
            break;
        case EQParams::kBandQ:
            band.q.store(value, std::memory_order_relaxed);
            break;
        case EQParams::kBandType:
            band.type.store(value, std::memory_order_relaxed);
            break;
        default:
            return;
    }
    // Mark band as dirty so coefficients are recomputed on next process().
    band.dirty.store(true, std::memory_order_relaxed);
}

float ParametricEQ::getParameter(int32_t paramId) const {
    int32_t bandIndex = paramId / EQParams::kParamsPerBand;
    int32_t offset = paramId % EQParams::kParamsPerBand;

    if (bandIndex < 0 || bandIndex >= kMaxEQBands) return 0.0f;

    const auto& band = bands_[bandIndex];
    switch (offset) {
        case EQParams::kBandFrequency:
            return band.frequency.load(std::memory_order_relaxed);
        case EQParams::kBandGain:
            return band.gain.load(std::memory_order_relaxed);
        case EQParams::kBandQ:
            return band.q.load(std::memory_order_relaxed);
        case EQParams::kBandType:
            return band.type.load(std::memory_order_relaxed);
        default:
            return 0.0f;
    }
}

void ParametricEQ::prepare(int32_t sampleRate, int32_t channelCount) {
    sampleRate_ = sampleRate;
    channelCount_ = channelCount;
    // Allocate one biquad per channel per band and mark all bands dirty.
    for (auto& band : bands_) {
        band.biquads.resize(channelCount);
        band.dirty.store(true, std::memory_order_relaxed);
    }
}

void ParametricEQ::reset() {
    for (auto& band : bands_) {
        band.frequency.store(1000.0f, std::memory_order_relaxed);
        band.gain.store(0.0f, std::memory_order_relaxed);
        band.q.store(0.707f, std::memory_order_relaxed);
        band.type.store(static_cast<float>(BiquadType::Peak), std::memory_order_relaxed);
        for (auto& bq : band.biquads) {
            bq.reset();
        }
        band.dirty.store(true, std::memory_order_relaxed);
    }
}

} // namespace klarinet
