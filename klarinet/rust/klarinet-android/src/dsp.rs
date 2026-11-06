//! Public DSP C ABI from `klarinet_dsp.cpp`, plus the Rust FIFO.
//!
//! `klarinet_fifo_*` is the real lock-free FIFO in `klarinet_dsp_core`.
//! Effect and chain symbols are the C++ wrappers. Host tests stub those
//! wrappers so `cargo test` does not link Oboe or the effect registry.
//! The stubs do not dereference the handles.

#[cfg(test)]
use std::cell::Cell;
use std::os::raw::c_void;

unsafe extern "C" {
    fn klarinet_fifo_create(capacity_samples: i32) -> *mut c_void;
    fn klarinet_fifo_destroy(fifo: *mut c_void);
    fn klarinet_fifo_write(fifo: *mut c_void, src: *const f32, count: i32) -> i32;
    fn klarinet_fifo_read(fifo: *mut c_void, dst: *mut f32, count: i32) -> i32;
    fn klarinet_fifo_available_to_read(fifo: *const c_void) -> i32;
    fn klarinet_fifo_available_to_write(fifo: *const c_void) -> i32;
}

#[cfg(test)]
unsafe extern "C" {
    fn klarinet_fifo_capacity(fifo: *const c_void) -> i32;
}

#[cfg(not(test))]
unsafe extern "C" {
    fn klarinet_create_effect(effect_type: i32) -> *mut c_void;
    fn klarinet_effect_set_parameter(effect: *mut c_void, param_id: i32, value: f32);
    fn klarinet_effect_get_parameter(effect: *mut c_void, param_id: i32) -> f32;
    fn klarinet_effect_set_enabled(effect: *mut c_void, enabled: i32);
    fn klarinet_effect_is_enabled(effect: *mut c_void) -> i32;
    fn klarinet_effect_destroy(effect: *mut c_void);
    fn klarinet_chain_create() -> *mut c_void;
    fn klarinet_chain_add(chain: *mut c_void, effect: *mut c_void);
    fn klarinet_chain_remove(chain: *mut c_void, effect: *mut c_void);
    fn klarinet_chain_clear(chain: *mut c_void);
    fn klarinet_chain_prepare(chain: *mut c_void, sample_rate: i32, channel_count: i32);
    fn klarinet_chain_get_effect_count(chain: *mut c_void) -> i32;
    fn klarinet_chain_enqueue_param(
        chain: *mut c_void,
        effect: *mut c_void,
        param_id: i32,
        value: f32,
    );
    fn klarinet_chain_process(chain: *mut c_void, buffer: *mut f32, num_frames: i32, channels: i32);
    fn klarinet_chain_destroy(chain: *mut c_void);
}

#[cfg(test)]
thread_local! {
    static CHAIN_CALLS: Cell<u32> = const { Cell::new(0) };
    static CHAIN_MARK: Cell<bool> = const { Cell::new(false) };
    static FAIL_EFFECT: Cell<bool> = const { Cell::new(false) };
    static NEXT_EFFECT: Cell<u64> = const { Cell::new(0x1000) };
    static SET_CALLS: Cell<u32> = const { Cell::new(0) };
    static ADD_CALLS: Cell<u32> = const { Cell::new(0) };
    static DESTROY_EFFECTS: Cell<u32> = const { Cell::new(0) };
    static DESTROY_CHAINS: Cell<u32> = const { Cell::new(0) };
}

#[cfg(test)]
pub fn reset_chain_calls() {
    CHAIN_CALLS.with(|calls| calls.set(0));
    CHAIN_MARK.with(|mark| mark.set(false));
}

#[cfg(test)]
pub fn chain_calls() -> u32 {
    CHAIN_CALLS.with(|calls| calls.get())
}

#[cfg(test)]
pub fn set_chain_mark(enabled: bool) {
    CHAIN_MARK.with(|mark| mark.set(enabled));
}

#[cfg(test)]
pub fn fail_next_effect(fail: bool) {
    FAIL_EFFECT.with(|flag| flag.set(fail));
}

#[cfg(test)]
pub fn set_calls() -> u32 {
    SET_CALLS.with(|calls| calls.get())
}

#[cfg(test)]
pub fn add_calls() -> u32 {
    ADD_CALLS.with(|calls| calls.get())
}

#[cfg(test)]
pub fn destroyed_effects() -> u32 {
    DESTROY_EFFECTS.with(|calls| calls.get())
}

#[cfg(test)]
pub fn destroyed_chains() -> u32 {
    DESTROY_CHAINS.with(|calls| calls.get())
}

#[cfg(test)]
pub fn reset_effect_calls() {
    SET_CALLS.with(|calls| calls.set(0));
    ADD_CALLS.with(|calls| calls.set(0));
    DESTROY_EFFECTS.with(|calls| calls.set(0));
    DESTROY_CHAINS.with(|calls| calls.set(0));
    FAIL_EFFECT.with(|flag| flag.set(false));
}

#[cfg(test)]
unsafe fn klarinet_create_effect(effect_type: i32) -> *mut c_void {
    let _ = effect_type;
    if FAIL_EFFECT.with(|flag| flag.get()) {
        return std::ptr::null_mut();
    }
    NEXT_EFFECT.with(|next| {
        let value = next.get();
        next.set(value.wrapping_add(1));
        value as *mut c_void
    })
}

#[cfg(test)]
unsafe fn klarinet_effect_set_parameter(effect: *mut c_void, _param_id: i32, _value: f32) {
    let _ = effect;
    SET_CALLS.with(|calls| calls.set(calls.get().saturating_add(1)));
}

#[cfg(test)]
unsafe fn klarinet_effect_get_parameter(effect: *mut c_void, _param_id: i32) -> f32 {
    let _ = effect;
    0.0
}

#[cfg(test)]
unsafe fn klarinet_effect_set_enabled(effect: *mut c_void, _enabled: i32) {
    let _ = effect;
}

#[cfg(test)]
unsafe fn klarinet_effect_is_enabled(effect: *mut c_void) -> i32 {
    let _ = effect;
    1
}

#[cfg(test)]
unsafe fn klarinet_effect_destroy(effect: *mut c_void) {
    let _ = effect;
    DESTROY_EFFECTS.with(|calls| calls.set(calls.get().saturating_add(1)));
}

#[cfg(test)]
unsafe fn klarinet_chain_create() -> *mut c_void {
    NEXT_EFFECT.with(|next| {
        let value = next.get();
        next.set(value.wrapping_add(1));
        value as *mut c_void
    })
}

#[cfg(test)]
unsafe fn klarinet_chain_add(chain: *mut c_void, effect: *mut c_void) {
    let _ = (chain, effect);
    ADD_CALLS.with(|calls| calls.set(calls.get().saturating_add(1)));
}

#[cfg(test)]
unsafe fn klarinet_chain_remove(chain: *mut c_void, effect: *mut c_void) {
    let _ = (chain, effect);
}

#[cfg(test)]
unsafe fn klarinet_chain_clear(chain: *mut c_void) {
    let _ = chain;
}

#[cfg(test)]
unsafe fn klarinet_chain_prepare(chain: *mut c_void, _sample_rate: i32, _channel_count: i32) {
    let _ = chain;
}

#[cfg(test)]
unsafe fn klarinet_chain_get_effect_count(chain: *mut c_void) -> i32 {
    let _ = chain;
    0
}

#[cfg(test)]
unsafe fn klarinet_chain_enqueue_param(
    chain: *mut c_void,
    effect: *mut c_void,
    _param_id: i32,
    _value: f32,
) {
    let _ = (chain, effect);
}

#[cfg(test)]
unsafe fn klarinet_chain_process(
    chain: *mut c_void,
    buffer: *mut f32,
    num_frames: i32,
    channel_count: i32,
) {
    let _ = chain;
    CHAIN_CALLS.with(|calls| calls.set(calls.get().saturating_add(1)));
    let mark = CHAIN_MARK.with(|flag| flag.get());
    if mark && !buffer.is_null() && num_frames > 0 && channel_count > 0 {
        *buffer = 9.0;
    }
}

#[cfg(test)]
unsafe fn klarinet_chain_destroy(chain: *mut c_void) {
    let _ = chain;
    DESTROY_CHAINS.with(|calls| calls.set(calls.get().saturating_add(1)));
}

pub fn fifo_create(capacity_samples: i32) -> *mut c_void {
    unsafe { klarinet_fifo_create(capacity_samples) }
}

pub fn fifo_destroy(fifo: *mut c_void) {
    unsafe { klarinet_fifo_destroy(fifo) }
}

pub fn fifo_write(fifo: *mut c_void, src: *const f32, count: i32) -> i32 {
    unsafe { klarinet_fifo_write(fifo, src, count) }
}

pub fn fifo_read(fifo: *mut c_void, dst: *mut f32, count: i32) -> i32 {
    unsafe { klarinet_fifo_read(fifo, dst, count) }
}

pub fn fifo_available_to_read(fifo: *const c_void) -> i32 {
    unsafe { klarinet_fifo_available_to_read(fifo) }
}

pub fn fifo_available_to_write(fifo: *const c_void) -> i32 {
    unsafe { klarinet_fifo_available_to_write(fifo) }
}

#[cfg(test)]
pub fn fifo_capacity(fifo: *const c_void) -> i32 {
    unsafe { klarinet_fifo_capacity(fifo) }
}

pub fn create_effect(effect_type: i32) -> *mut c_void {
    unsafe { klarinet_create_effect(effect_type) }
}

pub fn effect_set_parameter(effect: *mut c_void, param_id: i32, value: f32) {
    unsafe { klarinet_effect_set_parameter(effect, param_id, value) }
}

pub fn effect_get_parameter(effect: *mut c_void, param_id: i32) -> f32 {
    unsafe { klarinet_effect_get_parameter(effect, param_id) }
}

pub fn effect_set_enabled(effect: *mut c_void, enabled: bool) {
    unsafe { klarinet_effect_set_enabled(effect, i32::from(enabled)) }
}

pub fn effect_is_enabled(effect: *mut c_void) -> bool {
    unsafe { klarinet_effect_is_enabled(effect) != 0 }
}

pub fn effect_destroy(effect: *mut c_void) {
    unsafe { klarinet_effect_destroy(effect) }
}

pub fn chain_create() -> *mut c_void {
    unsafe { klarinet_chain_create() }
}

pub fn chain_add(chain: *mut c_void, effect: *mut c_void) {
    unsafe { klarinet_chain_add(chain, effect) }
}

pub fn chain_remove(chain: *mut c_void, effect: *mut c_void) {
    unsafe { klarinet_chain_remove(chain, effect) }
}

pub fn chain_clear(chain: *mut c_void) {
    unsafe { klarinet_chain_clear(chain) }
}

pub fn chain_prepare(chain: *mut c_void, sample_rate: i32, channel_count: i32) {
    unsafe { klarinet_chain_prepare(chain, sample_rate, channel_count) }
}

pub fn chain_count(chain: *mut c_void) -> i32 {
    unsafe { klarinet_chain_get_effect_count(chain) }
}

pub fn chain_enqueue(chain: *mut c_void, effect: *mut c_void, param_id: i32, value: f32) {
    unsafe { klarinet_chain_enqueue_param(chain, effect, param_id, value) }
}

pub fn chain_process(chain: *mut c_void, buffer: *mut f32, num_frames: i32, channels: i32) {
    if chain.is_null() {
        return;
    }
    unsafe { klarinet_chain_process(chain, buffer, num_frames, channels) }
}

pub fn chain_destroy(chain: *mut c_void) {
    unsafe { klarinet_chain_destroy(chain) }
}
