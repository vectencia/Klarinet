#include "MuteSolo.h"

#ifdef KLARINET_DSP_RUST_EFFECTS
#include "klarinet_fx.h"

namespace klarinet {

MuteSolo::MuteSolo() : AudioEffect(EffectType::MuteSolo) {
    rust_ = klarinet_fx_create(static_cast<int32_t>(EffectType::MuteSolo));
}

void MuteSolo::process(float* buffer, int32_t numFrames, int32_t channelCount) {
    if (!isEnabled()) return;
    klarinet_fx_process(rust_, buffer, numFrames, channelCount);
}

void MuteSolo::setParameter(int32_t paramId, float value) {
    klarinet_fx_set_parameter(rust_, paramId, value);
}

float MuteSolo::getParameter(int32_t paramId) const {
    return klarinet_fx_get_parameter(rust_, paramId);
}

void MuteSolo::prepare(int32_t sampleRate, int32_t channelCount) {
    sampleRate_ = sampleRate;
    channelCount_ = channelCount;
    klarinet_fx_prepare(rust_, sampleRate, channelCount);
}

void MuteSolo::reset() {
    klarinet_fx_reset(rust_);
}

} // namespace klarinet
#else
namespace klarinet {

MuteSolo::MuteSolo() : AudioEffect(EffectType::MuteSolo) {}

void MuteSolo::process(float* buffer, int32_t numFrames, int32_t channelCount) {
    if (!isEnabled()) return;

    float m = muted_.load(std::memory_order_relaxed);
    if (m > 0.5f) {
        // Mute: zero the entire buffer efficiently with memset.
        std::memset(buffer, 0, static_cast<size_t>(numFrames * channelCount) * sizeof(float));
    }
    // Solo is a chain-level concept; stored here but not enforced at effect level.
}

void MuteSolo::setParameter(int32_t paramId, float value) {
    if (paramId == MuteSoloParams::kMuted) {
        muted_.store(value, std::memory_order_relaxed);
    } else if (paramId == MuteSoloParams::kSoloed) {
        soloed_.store(value, std::memory_order_relaxed);
    }
}

float MuteSolo::getParameter(int32_t paramId) const {
    if (paramId == MuteSoloParams::kMuted) {
        return muted_.load(std::memory_order_relaxed);
    } else if (paramId == MuteSoloParams::kSoloed) {
        return soloed_.load(std::memory_order_relaxed);
    }
    return 0.0f;
}

void MuteSolo::prepare(int32_t sampleRate, int32_t channelCount) {
    sampleRate_ = sampleRate;
    channelCount_ = channelCount;
}

void MuteSolo::reset() {
    muted_.store(0.0f, std::memory_order_relaxed);
    soloed_.store(0.0f, std::memory_order_relaxed);
}

} // namespace klarinet

#endif
