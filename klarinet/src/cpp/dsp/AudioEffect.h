#pragma once
#include <atomic>
#include <cstdint>

namespace klarinet {

enum class EffectType : int32_t {
    Gain = 0, Pan, MuteSolo,
    Compressor, Limiter, NoiseGate,
    ParametricEQ, LowPassFilter, HighPassFilter, BandPassFilter,
    Delay, Reverb,
    Chorus, Flanger, Phaser, Tremolo,
    Count,
};

class AudioEffect {
public:
    explicit AudioEffect(EffectType type) : type_(type) {}
    virtual ~AudioEffect() = default;

    virtual void process(float* buffer, int32_t numFrames, int32_t channelCount) = 0;
    virtual void setParameter(int32_t paramId, float value) = 0;
    virtual float getParameter(int32_t paramId) const = 0;
    virtual void prepare(int32_t sampleRate, int32_t channelCount) = 0;
    virtual void reset() = 0;

    void setEnabled(bool enabled) { enabled_.store(enabled, std::memory_order_relaxed); }
    bool isEnabled() const { return enabled_.load(std::memory_order_relaxed); }
    EffectType getType() const { return type_; }

protected:
    int32_t sampleRate_ = 48000;
    int32_t channelCount_ = 1;

private:
    EffectType type_;
    std::atomic<bool> enabled_{true};
};

} // namespace klarinet
