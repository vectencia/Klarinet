/**
 * @file klarinet_dsp.cpp
 * @brief Implementation of the Klarinet DSP C API.
 *
 * This file bridges the flat C API (klarinet_dsp.h) to the C++ EffectFactory and
 * EffectChain classes. A global registry (`gRegistry`) maps opaque void* handles
 * back to std::shared_ptr<AudioEffect> so that:
 *
 *   - Effects remain alive as long as the C caller holds a handle (even after
 *     removal from a chain).
 *   - EffectChain can obtain a shared_ptr from a raw handle when adding effects.
 *
 * ## Thread safety of the global registry
 *
 * The registry is protected by `gRegistryMutex`. This mutex is only locked
 * during create / destroy / chain_add operations — **never on the audio thread**.
 * Audio-thread functions (klarinet_chain_process) bypass the registry entirely.
 */
#include "klarinet_dsp.h"
#include "EffectFactory.h"
#include "EffectChain.h"
#include <unordered_map>
#include <mutex>
#include <memory>

// ---------------------------------------------------------------------------
// Global registry: keeps shared_ptrs alive so raw-pointer handles stay valid.
// ---------------------------------------------------------------------------

/// Mutex protecting the global effect registry. Never held on the audio thread.
static std::mutex gRegistryMutex;

/// Maps raw AudioEffect pointers (used as opaque handles) to their shared_ptrs.
static std::unordered_map<void*, std::shared_ptr<klarinet::AudioEffect>> gRegistry;

/**
 * @brief Look up a shared_ptr from a raw handle.
 *
 * @pre Caller must hold gRegistryMutex.
 *
 * @param handle Opaque effect handle.
 * @return The shared_ptr owning the effect, or nullptr if not found.
 */
static std::shared_ptr<klarinet::AudioEffect> lookupLocked(KlarinetEffectHandle handle) {
    auto it = gRegistry.find(handle);
    if (it != gRegistry.end()) return it->second;
    return nullptr;
}

// ---------------------------------------------------------------------------
// Effect lifecycle
// ---------------------------------------------------------------------------

KlarinetEffectHandle klarinet_create_effect(int effectType) {
    auto effect = klarinet::EffectFactory::create(static_cast<klarinet::EffectType>(effectType));
    if (!effect) return nullptr;
    void* handle = effect.get();
    std::lock_guard<std::mutex> lock(gRegistryMutex);
    gRegistry[handle] = std::move(effect);
    return handle;
}

void klarinet_effect_set_parameter(KlarinetEffectHandle handle, int paramId, float value) {
    if (!handle) return;
    auto* effect = static_cast<klarinet::AudioEffect*>(handle);
    effect->setParameter(static_cast<int32_t>(paramId), value);
}

float klarinet_effect_get_parameter(KlarinetEffectHandle handle, int paramId) {
    if (!handle) return 0.0f;
    auto* effect = static_cast<klarinet::AudioEffect*>(handle);
    return effect->getParameter(static_cast<int32_t>(paramId));
}

void klarinet_effect_set_enabled(KlarinetEffectHandle handle, int enabled) {
    if (!handle) return;
    auto* effect = static_cast<klarinet::AudioEffect*>(handle);
    effect->setEnabled(enabled != 0);
}

int klarinet_effect_is_enabled(KlarinetEffectHandle handle) {
    if (!handle) return 0;
    auto* effect = static_cast<klarinet::AudioEffect*>(handle);
    return effect->isEnabled() ? 1 : 0;
}

int klarinet_effect_get_type(KlarinetEffectHandle handle) {
    if (!handle) return -1;
    auto* effect = static_cast<klarinet::AudioEffect*>(handle);
    return static_cast<int>(effect->getType());
}

void klarinet_effect_prepare(KlarinetEffectHandle handle, int sampleRate, int channelCount) {
    if (!handle) return;
    auto* effect = static_cast<klarinet::AudioEffect*>(handle);
    effect->prepare(static_cast<int32_t>(sampleRate), static_cast<int32_t>(channelCount));
}

void klarinet_effect_reset(KlarinetEffectHandle handle) {
    if (!handle) return;
    auto* effect = static_cast<klarinet::AudioEffect*>(handle);
    effect->reset();
}

void klarinet_effect_destroy(KlarinetEffectHandle handle) {
    if (!handle) return;
    std::lock_guard<std::mutex> lock(gRegistryMutex);
    gRegistry.erase(handle);
}

// ---------------------------------------------------------------------------
// EffectChain lifecycle
// ---------------------------------------------------------------------------

KlarinetEffectChainHandle klarinet_chain_create(void) {
    return static_cast<void*>(new klarinet::EffectChain());
}

void klarinet_chain_add(KlarinetEffectChainHandle chain, KlarinetEffectHandle effect) {
    if (!chain || !effect) return;
    auto* c = static_cast<klarinet::EffectChain*>(chain);
    std::lock_guard<std::mutex> lock(gRegistryMutex);
    auto sp = lookupLocked(effect);
    if (sp) c->addEffect(sp);
}

void klarinet_chain_remove(KlarinetEffectChainHandle chain, KlarinetEffectHandle effect) {
    if (!chain || !effect) return;
    auto* c = static_cast<klarinet::EffectChain*>(chain);
    c->removeEffect(static_cast<klarinet::AudioEffect*>(effect));
}

void klarinet_chain_process(KlarinetEffectChainHandle chain, float* buffer, int numFrames, int channelCount) {
    if (!chain) return;
    auto* c = static_cast<klarinet::EffectChain*>(chain);
    c->process(buffer, static_cast<int32_t>(numFrames), static_cast<int32_t>(channelCount));
}

void klarinet_chain_prepare(KlarinetEffectChainHandle chain, int sampleRate, int channelCount) {
    if (!chain) return;
    auto* c = static_cast<klarinet::EffectChain*>(chain);
    c->prepare(static_cast<int32_t>(sampleRate), static_cast<int32_t>(channelCount));
}

void klarinet_chain_clear(KlarinetEffectChainHandle chain) {
    if (!chain) return;
    auto* c = static_cast<klarinet::EffectChain*>(chain);
    c->clear();
}

int klarinet_chain_get_effect_count(KlarinetEffectChainHandle chain) {
    if (!chain) return 0;
    auto* c = static_cast<klarinet::EffectChain*>(chain);
    return static_cast<int>(c->getEffectCount());
}

void klarinet_chain_enqueue_param(KlarinetEffectChainHandle chain, KlarinetEffectHandle effect, int paramId, float value) {
    if (!chain || !effect) return;
    auto* c = static_cast<klarinet::EffectChain*>(chain);
    c->enqueueParameterChange(static_cast<klarinet::AudioEffect*>(effect),
                              static_cast<int32_t>(paramId), value);
}

void klarinet_chain_destroy(KlarinetEffectChainHandle chain) {
    if (!chain) return;
    delete static_cast<klarinet::EffectChain*>(chain);
}
