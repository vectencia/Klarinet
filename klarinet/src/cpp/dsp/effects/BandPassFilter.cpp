#include "BandPassFilter.h"

namespace klarinet {

BandPassFilter::BandPassFilter() : AudioEffect(EffectType::BandPassFilter) {}

void BandPassFilter::process(float* buffer, int32_t numFrames, int32_t channelCount) {
    if (!isEnabled()) return;

    if (dirty_.exchange(false, std::memory_order_relaxed)) {
        float center = centerHz_.load(std::memory_order_relaxed);
        float bw = bandwidth_.load(std::memory_order_relaxed);
        for (auto& bq : biquads_) {
            bq.configure(BiquadType::BandPass, center, bw, 0.0f,
                         static_cast<float>(sampleRate_));
        }
    }

    int32_t totalSamples = numFrames * channelCount;
    for (int32_t i = 0; i < totalSamples; ++i) {
        int32_t ch = i % channelCount;
        buffer[i] = biquads_[ch].process(buffer[i]);
    }
}

void BandPassFilter::setParameter(int32_t paramId, float value) {
    switch (paramId) {
        case BPFParams::kCenterHz:
            centerHz_.store(value, std::memory_order_relaxed);
            dirty_.store(true, std::memory_order_relaxed);
            break;
        case BPFParams::kBandwidth:
            bandwidth_.store(value, std::memory_order_relaxed);
            dirty_.store(true, std::memory_order_relaxed);
            break;
        default:
            break;
    }
}

float BandPassFilter::getParameter(int32_t paramId) const {
    switch (paramId) {
        case BPFParams::kCenterHz:
            return centerHz_.load(std::memory_order_relaxed);
        case BPFParams::kBandwidth:
            return bandwidth_.load(std::memory_order_relaxed);
        default:
            return 0.0f;
    }
}

void BandPassFilter::prepare(int32_t sampleRate, int32_t channelCount) {
    sampleRate_ = sampleRate;
    channelCount_ = channelCount;
    biquads_.resize(channelCount);
    dirty_.store(true, std::memory_order_relaxed);
}

void BandPassFilter::reset() {
    centerHz_.store(1000.0f, std::memory_order_relaxed);
    bandwidth_.store(1.0f, std::memory_order_relaxed);
    for (auto& bq : biquads_) {
        bq.reset();
    }
    dirty_.store(true, std::memory_order_relaxed);
}

} // namespace klarinet
