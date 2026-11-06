/**
 * @file klarinet_fx.h
 * @brief Internal C ABI for Rust primitives, effects, and the effect chain.
 *
 * This header is not part of klarinet_dsp.h. Apple cinterop does not see it.
 * klarinet_fx_process and klarinet_echain_process run on the audio thread.
 * They do not allocate, lock, or call back into Kotlin.
 */
#pragma once

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

void* klarinet_fx_create(int32_t effect_type);
void klarinet_fx_destroy(void* effect);
void klarinet_fx_process(void* effect, float* buffer, int32_t num_frames, int32_t channel_count);
void klarinet_fx_set_parameter(void* effect, int32_t param_id, float value);
float klarinet_fx_get_parameter(void* effect, int32_t param_id);
void klarinet_fx_prepare(void* effect, int32_t sample_rate, int32_t channel_count);
void klarinet_fx_reset(void* effect);

void* klarinet_biquad_create(void);
void klarinet_biquad_destroy(void* biquad);
void klarinet_biquad_configure(void* biquad, int32_t kind, float frequency, float q, float gain_db, float sample_rate);
float klarinet_biquad_process(void* biquad, float input);
void klarinet_biquad_reset(void* biquad);

void* klarinet_circ_create(int32_t max_size);
void klarinet_circ_destroy(void* buffer);
void klarinet_circ_set_size(void* buffer, int32_t size);
void klarinet_circ_clear(void* buffer);
void klarinet_circ_write(void* buffer, float sample);
float klarinet_circ_read(void* buffer, float delay);
float klarinet_circ_read_linear(void* buffer, float delay);
int32_t klarinet_circ_size(void* buffer);

void* klarinet_lfo_create(void);
void klarinet_lfo_destroy(void* lfo);
void klarinet_lfo_prepare(void* lfo, float sample_rate);
void klarinet_lfo_set_frequency(void* lfo, float hz);
float klarinet_lfo_next(void* lfo);
void klarinet_lfo_reset(void* lfo);

void* klarinet_env_create(void);
void klarinet_env_destroy(void* env);
void klarinet_env_prepare(void* env, float sample_rate);
void klarinet_env_set_attack(void* env, float ms);
void klarinet_env_set_release(void* env, float ms);
float klarinet_env_process(void* env, float input);
void klarinet_env_reset(void* env);

typedef void (*klarinet_node_process_fn)(void* ctx, float* buffer, int32_t num_frames, int32_t channel_count);
typedef void (*klarinet_node_prepare_fn)(void* ctx, int32_t sample_rate, int32_t channel_count);
typedef void (*klarinet_node_destroy_fn)(void* ctx);
typedef void* (*klarinet_node_clone_fn)(void* ctx);
typedef void (*klarinet_effect_set_fn)(void* effect, int32_t param_id, float value);

void* klarinet_echain_create(void);
void klarinet_echain_destroy(void* chain);
void klarinet_echain_add(
    void* chain,
    void* key,
    void* ctx,
    klarinet_node_process_fn process,
    klarinet_node_prepare_fn prepare,
    klarinet_node_destroy_fn destroy,
    klarinet_node_clone_fn clone_ctx);
void klarinet_echain_remove(void* chain, void* key);
void klarinet_echain_reorder(void* chain, void* const* keys, int32_t count);
void klarinet_echain_clear(void* chain);
void klarinet_echain_enqueue(void* chain, void* effect, klarinet_effect_set_fn set_param, int32_t param_id, float value);
void klarinet_echain_prepare(void* chain, int32_t sample_rate, int32_t channel_count);
void klarinet_echain_process(void* chain, float* buffer, int32_t num_frames, int32_t channel_count);
int32_t klarinet_echain_count(void* chain);

#ifdef __cplusplus
}
#endif
