#include "EffectChain.h"
#include <algorithm>

namespace klarinet {

EffectChain::EffectChain() {
    activeChain_.store(new EffectList(), std::memory_order_relaxed);
}

EffectChain::~EffectChain() {
    delete activeChain_.load(std::memory_order_relaxed);
    delete pendingDelete_;
}

void EffectChain::process(float* buffer, int32_t numFrames, int32_t channelCount) {
    applyPendingChanges();

    EffectList* chain = activeChain_.load(std::memory_order_acquire);
    if (!chain) return;

    for (auto& effect : *chain) {
        if (effect && effect->isEnabled()) {
            effect->process(buffer, numFrames, channelCount);
        }
    }
}

void EffectChain::applyPendingChanges() {
    // Delete the old chain that was swapped out. This is safe because the
    // audio thread is the only thread that reads activeChain_, and we already
    // swapped to a new chain before this point.
    delete pendingDelete_;
    pendingDelete_ = nullptr;

    ParameterChange change;
    while (paramQueue_.pop(change)) {
        if (change.effect) {
            change.effect->setParameter(change.paramId, change.value);
        }
    }
}

void EffectChain::addEffect(std::shared_ptr<AudioEffect> effect) {
    std::lock_guard<std::mutex> lock(controlMutex_);

    effect->prepare(sampleRate_, channelCount_);

    EffectList* current = activeChain_.load(std::memory_order_acquire);
    auto* newChain = new EffectList(*current);
    newChain->push_back(std::move(effect));
    swapChain(newChain);
}

void EffectChain::removeEffect(AudioEffect* effect) {
    std::lock_guard<std::mutex> lock(controlMutex_);

    EffectList* current = activeChain_.load(std::memory_order_acquire);
    auto* newChain = new EffectList();
    newChain->reserve(current->size());

    for (auto& e : *current) {
        if (e.get() != effect) {
            newChain->push_back(e);
        }
    }
    swapChain(newChain);
}

void EffectChain::reorderEffects(const std::vector<AudioEffect*>& newOrder) {
    std::lock_guard<std::mutex> lock(controlMutex_);

    EffectList* current = activeChain_.load(std::memory_order_acquire);
    auto* newChain = new EffectList();
    newChain->reserve(newOrder.size());

    for (auto* raw : newOrder) {
        for (auto& e : *current) {
            if (e.get() == raw) {
                newChain->push_back(e);
                break;
            }
        }
    }
    swapChain(newChain);
}

void EffectChain::clear() {
    std::lock_guard<std::mutex> lock(controlMutex_);
    swapChain(new EffectList());
}

void EffectChain::enqueueParameterChange(AudioEffect* effect, int32_t paramId, float value) {
    paramQueue_.push({effect, paramId, value});
}

void EffectChain::prepare(int32_t sampleRate, int32_t channelCount) {
    std::lock_guard<std::mutex> lock(controlMutex_);
    sampleRate_ = sampleRate;
    channelCount_ = channelCount;

    EffectList* current = activeChain_.load(std::memory_order_acquire);
    if (current) {
        for (auto& effect : *current) {
            if (effect) {
                effect->prepare(sampleRate, channelCount);
            }
        }
    }
}

int32_t EffectChain::getEffectCount() const {
    EffectList* current = activeChain_.load(std::memory_order_acquire);
    return current ? static_cast<int32_t>(current->size()) : 0;
}

void EffectChain::swapChain(EffectList* newChain) {
    EffectList* old = activeChain_.exchange(newChain, std::memory_order_acq_rel);
    pendingDelete_ = old;
}

} // namespace klarinet
