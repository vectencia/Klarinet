//! C ABI for primitives, the sixteen effects, and the effect chain.
//!
//! These names are internal. They are not declared in `klarinet_dsp.h`.
//! `klarinet_fx_process` and `klarinet_echain_process` run on the audio thread.
//! They do not allocate, lock, or panic.

use crate::chain::{Chain, CloneFn, DestroyFn, NewSlot, PrepareFn, ProcessFn, SetParamFn};
use crate::fx::{DspEffect, Effect};
use crate::primitives::{Biquad, BiquadKind, CircularBuffer, EnvelopeFollower, Lfo};
use std::ffi::c_void;
use std::ptr;

fn sample_count(buffer: *mut f32, num_frames: i32, channel_count: i32) -> Option<usize> {
    if buffer.is_null() || num_frames <= 0 || channel_count <= 0 {
        return None;
    }
    (num_frames as usize).checked_mul(channel_count as usize)
}

#[no_mangle]
pub extern "C" fn klarinet_fx_create(effect_type: i32) -> *mut Effect {
    match Effect::new(effect_type) {
        Some(effect) => Box::into_raw(Box::new(effect)),
        None => ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "C" fn klarinet_fx_destroy(effect: *mut Effect) {
    unsafe {
        if !effect.is_null() {
            drop(Box::from_raw(effect));
        }
    }
}

#[no_mangle]
pub extern "C" fn klarinet_fx_process(
    effect: *mut Effect,
    buffer: *mut f32,
    num_frames: i32,
    channel_count: i32,
) {
    if effect.is_null() {
        return;
    }
    let Some(count) = sample_count(buffer, num_frames, channel_count) else {
        return;
    };
    let samples = unsafe { std::slice::from_raw_parts_mut(buffer, count) };
    unsafe { (*effect).process(samples, num_frames, channel_count) };
}

#[no_mangle]
pub extern "C" fn klarinet_fx_set_parameter(effect: *mut Effect, param_id: i32, value: f32) {
    unsafe {
        if effect.is_null() {
            return;
        }
        (*effect).set_parameter(param_id, value);
    }
}

#[no_mangle]
pub extern "C" fn klarinet_fx_get_parameter(effect: *const Effect, param_id: i32) -> f32 {
    unsafe {
        if effect.is_null() {
            return 0.0;
        }
        (*effect).get_parameter(param_id)
    }
}

#[no_mangle]
pub extern "C" fn klarinet_fx_prepare(effect: *mut Effect, sample_rate: i32, channel_count: i32) {
    unsafe {
        if effect.is_null() {
            return;
        }
        (*effect).prepare(sample_rate, channel_count);
    }
}

#[no_mangle]
pub extern "C" fn klarinet_fx_reset(effect: *mut Effect) {
    unsafe {
        if effect.is_null() {
            return;
        }
        (*effect).reset();
    }
}

#[no_mangle]
pub extern "C" fn klarinet_biquad_create() -> *mut Biquad {
    Box::into_raw(Box::new(Biquad::new()))
}

#[no_mangle]
pub extern "C" fn klarinet_biquad_destroy(biquad: *mut Biquad) {
    unsafe {
        if !biquad.is_null() {
            drop(Box::from_raw(biquad));
        }
    }
}

#[no_mangle]
pub extern "C" fn klarinet_biquad_configure(
    biquad: *mut Biquad,
    kind: i32,
    frequency: f32,
    q: f32,
    gain_db: f32,
    sample_rate: f32,
) {
    unsafe {
        if biquad.is_null() {
            return;
        }
        let Some(kind) = BiquadKind::from_i32(kind) else {
            return;
        };
        (*biquad).configure(kind, frequency, q, gain_db, sample_rate);
    }
}

#[no_mangle]
pub extern "C" fn klarinet_biquad_process(biquad: *mut Biquad, input: f32) -> f32 {
    unsafe {
        if biquad.is_null() {
            return 0.0;
        }
        (*biquad).process(input)
    }
}

#[no_mangle]
pub extern "C" fn klarinet_biquad_reset(biquad: *mut Biquad) {
    unsafe {
        if biquad.is_null() {
            return;
        }
        (*biquad).reset();
    }
}

#[no_mangle]
pub extern "C" fn klarinet_circ_create(max_size: i32) -> *mut CircularBuffer {
    Box::into_raw(Box::new(CircularBuffer::new(max_size)))
}

#[no_mangle]
pub extern "C" fn klarinet_circ_destroy(buffer: *mut CircularBuffer) {
    unsafe {
        if !buffer.is_null() {
            drop(Box::from_raw(buffer));
        }
    }
}

#[no_mangle]
pub extern "C" fn klarinet_circ_set_size(buffer: *mut CircularBuffer, size: i32) {
    unsafe {
        if buffer.is_null() {
            return;
        }
        (*buffer).set_size(size);
    }
}

#[no_mangle]
pub extern "C" fn klarinet_circ_clear(buffer: *mut CircularBuffer) {
    unsafe {
        if buffer.is_null() {
            return;
        }
        (*buffer).clear();
    }
}

#[no_mangle]
pub extern "C" fn klarinet_circ_write(buffer: *mut CircularBuffer, sample: f32) {
    unsafe {
        if buffer.is_null() {
            return;
        }
        (*buffer).write(sample);
    }
}

#[no_mangle]
pub extern "C" fn klarinet_circ_read(buffer: *mut CircularBuffer, delay: f32) -> f32 {
    unsafe {
        if buffer.is_null() {
            return 0.0;
        }
        (*buffer).read(delay)
    }
}

#[no_mangle]
pub extern "C" fn klarinet_circ_read_linear(buffer: *mut CircularBuffer, delay: f32) -> f32 {
    unsafe {
        if buffer.is_null() {
            return 0.0;
        }
        (*buffer).read_linear(delay)
    }
}

#[no_mangle]
pub extern "C" fn klarinet_circ_size(buffer: *const CircularBuffer) -> i32 {
    unsafe {
        if buffer.is_null() {
            return 0;
        }
        (*buffer).size()
    }
}

#[no_mangle]
pub extern "C" fn klarinet_lfo_create() -> *mut Lfo {
    Box::into_raw(Box::new(Lfo::new()))
}

#[no_mangle]
pub extern "C" fn klarinet_lfo_destroy(lfo: *mut Lfo) {
    unsafe {
        if !lfo.is_null() {
            drop(Box::from_raw(lfo));
        }
    }
}

#[no_mangle]
pub extern "C" fn klarinet_lfo_prepare(lfo: *mut Lfo, sample_rate: f32) {
    unsafe {
        if lfo.is_null() {
            return;
        }
        (*lfo).prepare(sample_rate);
    }
}

#[no_mangle]
pub extern "C" fn klarinet_lfo_set_frequency(lfo: *mut Lfo, hz: f32) {
    unsafe {
        if lfo.is_null() {
            return;
        }
        (*lfo).set_frequency(hz);
    }
}

#[no_mangle]
pub extern "C" fn klarinet_lfo_next(lfo: *mut Lfo) -> f32 {
    unsafe {
        if lfo.is_null() {
            return 0.0;
        }
        (*lfo).next_sample()
    }
}

#[no_mangle]
pub extern "C" fn klarinet_lfo_reset(lfo: *mut Lfo) {
    unsafe {
        if lfo.is_null() {
            return;
        }
        (*lfo).reset();
    }
}

#[no_mangle]
pub extern "C" fn klarinet_env_create() -> *mut EnvelopeFollower {
    Box::into_raw(Box::new(EnvelopeFollower::new()))
}

#[no_mangle]
pub extern "C" fn klarinet_env_destroy(env: *mut EnvelopeFollower) {
    unsafe {
        if !env.is_null() {
            drop(Box::from_raw(env));
        }
    }
}

#[no_mangle]
pub extern "C" fn klarinet_env_prepare(env: *mut EnvelopeFollower, sample_rate: f32) {
    unsafe {
        if env.is_null() {
            return;
        }
        (*env).prepare(sample_rate);
    }
}

#[no_mangle]
pub extern "C" fn klarinet_env_set_attack(env: *mut EnvelopeFollower, ms: f32) {
    unsafe {
        if env.is_null() {
            return;
        }
        (*env).set_attack_ms(ms);
    }
}

#[no_mangle]
pub extern "C" fn klarinet_env_set_release(env: *mut EnvelopeFollower, ms: f32) {
    unsafe {
        if env.is_null() {
            return;
        }
        (*env).set_release_ms(ms);
    }
}

#[no_mangle]
pub extern "C" fn klarinet_env_process(env: *mut EnvelopeFollower, input: f32) -> f32 {
    unsafe {
        if env.is_null() {
            return 0.0;
        }
        (*env).process(input)
    }
}

#[no_mangle]
pub extern "C" fn klarinet_env_reset(env: *mut EnvelopeFollower) {
    unsafe {
        if env.is_null() {
            return;
        }
        (*env).reset();
    }
}

#[no_mangle]
pub extern "C" fn klarinet_echain_create() -> *mut Chain {
    match Chain::new() {
        Some(chain) => Box::into_raw(chain),
        None => ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "C" fn klarinet_echain_destroy(chain: *mut Chain) {
    unsafe {
        if !chain.is_null() {
            drop(Box::from_raw(chain));
        }
    }
}

#[no_mangle]
pub extern "C" fn klarinet_echain_add(
    chain: *mut Chain,
    key: *mut c_void,
    ctx: *mut c_void,
    process: Option<ProcessFn>,
    prepare: Option<PrepareFn>,
    destroy: Option<DestroyFn>,
    clone_ctx: Option<CloneFn>,
) {
    unsafe {
        if chain.is_null() {
            return;
        }
        (*chain).add_slot(NewSlot {
            key,
            ctx,
            process,
            prepare,
            destroy,
            clone_ctx,
        });
    }
}

#[no_mangle]
pub extern "C" fn klarinet_echain_remove(chain: *mut Chain, key: *mut c_void) {
    unsafe {
        if chain.is_null() {
            return;
        }
        (*chain).remove(key);
    }
}

#[no_mangle]
pub extern "C" fn klarinet_echain_reorder(chain: *mut Chain, keys: *const *mut c_void, count: i32) {
    unsafe {
        if chain.is_null() {
            return;
        }
        if count <= 0 {
            (*chain).reorder(&[]);
            return;
        }
        if keys.is_null() {
            return;
        }
        let keys = std::slice::from_raw_parts(keys, count as usize);
        (*chain).reorder(keys);
    }
}

#[no_mangle]
pub extern "C" fn klarinet_echain_clear(chain: *mut Chain) {
    unsafe {
        if chain.is_null() {
            return;
        }
        (*chain).clear();
    }
}

#[no_mangle]
pub extern "C" fn klarinet_echain_enqueue(
    chain: *mut Chain,
    effect: *mut c_void,
    set_param: Option<SetParamFn>,
    param_id: i32,
    value: f32,
) {
    unsafe {
        if chain.is_null() {
            return;
        }
        (*chain).enqueue(effect, set_param, param_id, value);
    }
}

#[no_mangle]
pub extern "C" fn klarinet_echain_prepare(chain: *mut Chain, sample_rate: i32, channel_count: i32) {
    unsafe {
        if chain.is_null() {
            return;
        }
        (*chain).prepare(sample_rate, channel_count);
    }
}

#[no_mangle]
pub extern "C" fn klarinet_echain_process(
    chain: *mut Chain,
    buffer: *mut f32,
    num_frames: i32,
    channel_count: i32,
) {
    unsafe {
        if chain.is_null() {
            return;
        }
        (*chain).process(buffer, num_frames, channel_count);
    }
}

#[no_mangle]
pub extern "C" fn klarinet_echain_count(chain: *const Chain) -> i32 {
    unsafe {
        if chain.is_null() {
            return 0;
        }
        (*chain).count()
    }
}

pub(crate) fn keep_exports() {
    let exports: [*const (); 43] = [
        klarinet_fx_create as *const (),
        klarinet_fx_destroy as *const (),
        klarinet_fx_process as *const (),
        klarinet_fx_set_parameter as *const (),
        klarinet_fx_get_parameter as *const (),
        klarinet_fx_prepare as *const (),
        klarinet_fx_reset as *const (),
        klarinet_biquad_create as *const (),
        klarinet_biquad_destroy as *const (),
        klarinet_biquad_configure as *const (),
        klarinet_biquad_process as *const (),
        klarinet_biquad_reset as *const (),
        klarinet_circ_create as *const (),
        klarinet_circ_destroy as *const (),
        klarinet_circ_set_size as *const (),
        klarinet_circ_clear as *const (),
        klarinet_circ_write as *const (),
        klarinet_circ_read as *const (),
        klarinet_circ_read_linear as *const (),
        klarinet_circ_size as *const (),
        klarinet_lfo_create as *const (),
        klarinet_lfo_destroy as *const (),
        klarinet_lfo_prepare as *const (),
        klarinet_lfo_set_frequency as *const (),
        klarinet_lfo_next as *const (),
        klarinet_lfo_reset as *const (),
        klarinet_env_create as *const (),
        klarinet_env_destroy as *const (),
        klarinet_env_prepare as *const (),
        klarinet_env_set_attack as *const (),
        klarinet_env_set_release as *const (),
        klarinet_env_process as *const (),
        klarinet_env_reset as *const (),
        klarinet_echain_create as *const (),
        klarinet_echain_destroy as *const (),
        klarinet_echain_add as *const (),
        klarinet_echain_remove as *const (),
        klarinet_echain_reorder as *const (),
        klarinet_echain_clear as *const (),
        klarinet_echain_enqueue as *const (),
        klarinet_echain_prepare as *const (),
        klarinet_echain_process as *const (),
        klarinet_echain_count as *const (),
    ];
    let _ = std::hint::black_box(exports);
}
