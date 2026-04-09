#pragma once
#include "AudioEffect.h"
#include "RingBuffer.h"
#include <vector>
#include <memory>
#include <atomic>
#include <mutex>

namespace klarinet {

struct ParameterChange {
    AudioEffect* effect;
    int32_t paramId;
    float value;
};

class EffectChain {
public:
    EffectChain();
    ~EffectChain();

    // Audio thread
    void process(float* buffer, int32_t numFrames, int32_t channelCount);

    // Control thread
    void addEffect(std::shared_ptr<AudioEffect> effect);
    void removeEffect(AudioEffect* effect);
    void reorderEffects(const std::vector<AudioEffect*>& newOrder);
    void clear();
    void enqueueParameterChange(AudioEffect* effect, int32_t paramId, float value);
    void prepare(int32_t sampleRate, int32_t channelCount);
    int32_t getEffectCount() const;

private:
    using EffectList = std::vector<std::shared_ptr<AudioEffect>>;
    void swapChain(EffectList* newChain);
    void applyPendingChanges();

    std::atomic<EffectList*> activeChain_;
    EffectList* pendingDelete_ = nullptr;
    std::mutex controlMutex_;
    RingBuffer<ParameterChange, 512> paramQueue_;
    int32_t sampleRate_ = 48000;
    int32_t channelCount_ = 1;
};

} // namespace klarinet
