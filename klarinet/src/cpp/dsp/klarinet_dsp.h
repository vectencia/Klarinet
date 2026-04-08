#pragma once

#ifdef __cplusplus
extern "C" {
#endif

typedef void* KlarinetEffectHandle;
typedef void* KlarinetEffectChainHandle;

// Effect lifecycle
KlarinetEffectHandle klarinet_create_effect(int effectType);
void klarinet_effect_set_parameter(KlarinetEffectHandle handle, int paramId, float value);
float klarinet_effect_get_parameter(KlarinetEffectHandle handle, int paramId);
void klarinet_effect_set_enabled(KlarinetEffectHandle handle, int enabled);
int klarinet_effect_is_enabled(KlarinetEffectHandle handle);
int klarinet_effect_get_type(KlarinetEffectHandle handle);
void klarinet_effect_prepare(KlarinetEffectHandle handle, int sampleRate, int channelCount);
void klarinet_effect_reset(KlarinetEffectHandle handle);
void klarinet_effect_destroy(KlarinetEffectHandle handle);

// EffectChain lifecycle
KlarinetEffectChainHandle klarinet_chain_create(void);
void klarinet_chain_add(KlarinetEffectChainHandle chain, KlarinetEffectHandle effect);
void klarinet_chain_remove(KlarinetEffectChainHandle chain, KlarinetEffectHandle effect);
void klarinet_chain_process(KlarinetEffectChainHandle chain, float* buffer, int numFrames, int channelCount);
void klarinet_chain_prepare(KlarinetEffectChainHandle chain, int sampleRate, int channelCount);
void klarinet_chain_clear(KlarinetEffectChainHandle chain);
int klarinet_chain_get_effect_count(KlarinetEffectChainHandle chain);
void klarinet_chain_enqueue_param(KlarinetEffectChainHandle chain, KlarinetEffectHandle effect, int paramId, float value);
void klarinet_chain_destroy(KlarinetEffectChainHandle chain);

#ifdef __cplusplus
}
#endif
