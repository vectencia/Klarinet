//! Public `klarinet_dsp.h` effect and chain symbols for the linuxX64 staticlib.
//!
//! `nativeDesktopMain` passes the pointer from `klarinet_chain_create` into
//! `klarinet_device_set_chain`. The audio callback then calls
//! `klarinet_chain_process` on that same pointer. Both live in this archive so
//! linuxX64 does not link a second Rust staticlib. Effect bodies stay in
//! `klarinet_dsp_core` (`klarinet_fx_*` and `klarinet_echain_*`).
//!
//! The registry matches `klarinet_dsp.cpp`: create, destroy, and chain_add take
//! the mutex. `klarinet_chain_process` and the node callbacks do not. Destroy
//! drops the registry entry only. A chain that still holds the effect keeps it
//! alive. The audio thread frees one retired node list inside
//! `klarinet_echain_process`, which is the existing exception.

use std::collections::HashMap;
use std::ffi::c_void;
use std::ptr;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, OnceLock};

unsafe extern "C" {
    fn klarinet_fx_create(effect_type: i32) -> *mut c_void;
    fn klarinet_fx_destroy(effect: *mut c_void);
    fn klarinet_fx_process(
        effect: *mut c_void,
        buffer: *mut f32,
        num_frames: i32,
        channel_count: i32,
    );
    fn klarinet_fx_set_parameter(effect: *mut c_void, param_id: i32, value: f32);
    fn klarinet_fx_get_parameter(effect: *mut c_void, param_id: i32) -> f32;
    fn klarinet_fx_prepare(effect: *mut c_void, sample_rate: i32, channel_count: i32);
    fn klarinet_fx_reset(effect: *mut c_void);
    fn klarinet_echain_create() -> *mut c_void;
    fn klarinet_echain_destroy(chain: *mut c_void);
    fn klarinet_echain_add(
        chain: *mut c_void,
        key: *mut c_void,
        ctx: *mut c_void,
        process: Option<ProcessFn>,
        prepare: Option<PrepareFn>,
        destroy: Option<DestroyFn>,
        clone_ctx: Option<CloneFn>,
    );
    fn klarinet_echain_remove(chain: *mut c_void, key: *mut c_void);
    fn klarinet_echain_clear(chain: *mut c_void);
    fn klarinet_echain_enqueue(
        chain: *mut c_void,
        effect: *mut c_void,
        set_param: Option<SetFn>,
        param_id: i32,
        value: f32,
    );
    fn klarinet_echain_prepare(chain: *mut c_void, sample_rate: i32, channel_count: i32);
    fn klarinet_echain_process(
        chain: *mut c_void,
        buffer: *mut f32,
        num_frames: i32,
        channel_count: i32,
    );
    fn klarinet_echain_count(chain: *mut c_void) -> i32;
}

type ProcessFn = unsafe extern "C" fn(*mut c_void, *mut f32, i32, i32);
type PrepareFn = unsafe extern "C" fn(*mut c_void, i32, i32);
type DestroyFn = unsafe extern "C" fn(*mut c_void);
type CloneFn = unsafe extern "C" fn(*mut c_void) -> *mut c_void;
type SetFn = unsafe extern "C" fn(*mut c_void, i32, f32);

struct EffectSlot {
    fx: *mut c_void,
    effect_type: i32,
    enabled: AtomicBool,
}

unsafe impl Send for EffectSlot {}
unsafe impl Sync for EffectSlot {}

impl Drop for EffectSlot {
    fn drop(&mut self) {
        unsafe { klarinet_fx_destroy(self.fx) };
    }
}

struct Node {
    effect: Arc<EffectSlot>,
}

struct ChainSlot {
    rust: *mut c_void,
}

impl Drop for ChainSlot {
    fn drop(&mut self) {
        unsafe { klarinet_echain_destroy(self.rust) };
    }
}

#[used]
static KEEP_DSP_EXPORTS: fn() = keep_dsp_exports;

fn keep_dsp_exports() {
    klarinet_dsp_core::link_effects();
    klarinet_dsp_core::link_offload();
}

fn registry() -> &'static Mutex<HashMap<usize, Arc<EffectSlot>>> {
    static REGISTRY: OnceLock<Mutex<HashMap<usize, Arc<EffectSlot>>>> = OnceLock::new();
    REGISTRY.get_or_init(|| Mutex::new(HashMap::new()))
}

fn lock_registry() -> std::sync::MutexGuard<'static, HashMap<usize, Arc<EffectSlot>>> {
    registry().lock().unwrap_or_else(|err| err.into_inner())
}

fn chain_rust(chain: *mut c_void) -> Option<*mut c_void> {
    if chain.is_null() {
        None
    } else {
        Some(unsafe { (*chain.cast::<ChainSlot>()).rust })
    }
}

extern "C" fn node_process(ctx: *mut c_void, buffer: *mut f32, frames: i32, channels: i32) {
    if ctx.is_null() {
        return;
    }
    let node = unsafe { &*(ctx.cast::<Node>()) };
    if !node.effect.enabled.load(Ordering::Relaxed) {
        return;
    }
    unsafe { klarinet_fx_process(node.effect.fx, buffer, frames, channels) };
}

extern "C" fn node_prepare(ctx: *mut c_void, sample_rate: i32, channel_count: i32) {
    if ctx.is_null() {
        return;
    }
    let node = unsafe { &*(ctx.cast::<Node>()) };
    unsafe { klarinet_fx_prepare(node.effect.fx, sample_rate, channel_count) };
}

extern "C" fn node_destroy(ctx: *mut c_void) {
    if ctx.is_null() {
        return;
    }
    drop(unsafe { Box::from_raw(ctx.cast::<Node>()) });
}

extern "C" fn node_clone(ctx: *mut c_void) -> *mut c_void {
    if ctx.is_null() {
        return ptr::null_mut();
    }
    let node = unsafe { &*(ctx.cast::<Node>()) };
    Box::into_raw(Box::new(Node {
        effect: Arc::clone(&node.effect),
    }))
    .cast()
}

extern "C" fn node_set_parameter(effect: *mut c_void, param_id: i32, value: f32) {
    if effect.is_null() {
        return;
    }
    let slot = unsafe { &*(effect.cast::<EffectSlot>()) };
    unsafe { klarinet_fx_set_parameter(slot.fx, param_id, value) };
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_create_effect(effect_type: i32) -> *mut c_void {
    let fx = unsafe { klarinet_fx_create(effect_type) };
    if fx.is_null() {
        return ptr::null_mut();
    }
    let slot = Arc::new(EffectSlot {
        fx,
        effect_type,
        enabled: AtomicBool::new(true),
    });
    let handle = Arc::as_ptr(&slot).cast_mut().cast::<c_void>();
    lock_registry().insert(handle as usize, slot);
    handle
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_effect_set_parameter(
    handle: *mut c_void,
    param_id: i32,
    value: f32,
) {
    if handle.is_null() {
        return;
    }
    let slot = unsafe { &*(handle.cast::<EffectSlot>()) };
    unsafe { klarinet_fx_set_parameter(slot.fx, param_id, value) };
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_effect_get_parameter(handle: *mut c_void, param_id: i32) -> f32 {
    if handle.is_null() {
        return 0.0;
    }
    let slot = unsafe { &*(handle.cast::<EffectSlot>()) };
    unsafe { klarinet_fx_get_parameter(slot.fx, param_id) }
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_effect_set_enabled(handle: *mut c_void, enabled: i32) {
    if handle.is_null() {
        return;
    }
    let slot = unsafe { &*(handle.cast::<EffectSlot>()) };
    slot.enabled.store(enabled != 0, Ordering::Relaxed);
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_effect_is_enabled(handle: *mut c_void) -> i32 {
    if handle.is_null() {
        return 0;
    }
    let slot = unsafe { &*(handle.cast::<EffectSlot>()) };
    i32::from(slot.enabled.load(Ordering::Relaxed))
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_effect_get_type(handle: *mut c_void) -> i32 {
    if handle.is_null() {
        return -1;
    }
    unsafe { (*handle.cast::<EffectSlot>()).effect_type }
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_effect_prepare(
    handle: *mut c_void,
    sample_rate: i32,
    channel_count: i32,
) {
    if handle.is_null() {
        return;
    }
    let slot = unsafe { &*(handle.cast::<EffectSlot>()) };
    unsafe { klarinet_fx_prepare(slot.fx, sample_rate, channel_count) };
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_effect_reset(handle: *mut c_void) {
    if handle.is_null() {
        return;
    }
    let slot = unsafe { &*(handle.cast::<EffectSlot>()) };
    unsafe { klarinet_fx_reset(slot.fx) };
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_effect_destroy(handle: *mut c_void) {
    if handle.is_null() {
        return;
    }
    lock_registry().remove(&(handle as usize));
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_chain_create() -> *mut c_void {
    let rust = unsafe { klarinet_echain_create() };
    if rust.is_null() {
        return ptr::null_mut();
    }
    Box::into_raw(Box::new(ChainSlot { rust })).cast()
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_chain_add(chain: *mut c_void, effect: *mut c_void) {
    let Some(rust) = chain_rust(chain) else {
        return;
    };
    if effect.is_null() {
        return;
    }
    let registry = lock_registry();
    let Some(slot) = registry.get(&(effect as usize)).cloned() else {
        return;
    };
    let node = Box::into_raw(Box::new(Node { effect: slot }));
    unsafe {
        klarinet_echain_add(
            rust,
            effect,
            node.cast(),
            Some(node_process),
            Some(node_prepare),
            Some(node_destroy),
            Some(node_clone),
        );
    }
    drop(registry);
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_chain_remove(chain: *mut c_void, effect: *mut c_void) {
    let Some(rust) = chain_rust(chain) else {
        return;
    };
    if effect.is_null() {
        return;
    }
    unsafe { klarinet_echain_remove(rust, effect) };
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_chain_process(
    chain: *mut c_void,
    buffer: *mut f32,
    num_frames: i32,
    channel_count: i32,
) {
    let Some(rust) = chain_rust(chain) else {
        return;
    };
    unsafe { klarinet_echain_process(rust, buffer, num_frames, channel_count) };
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_chain_prepare(
    chain: *mut c_void,
    sample_rate: i32,
    channel_count: i32,
) {
    let Some(rust) = chain_rust(chain) else {
        return;
    };
    unsafe { klarinet_echain_prepare(rust, sample_rate, channel_count) };
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_chain_clear(chain: *mut c_void) {
    let Some(rust) = chain_rust(chain) else {
        return;
    };
    unsafe { klarinet_echain_clear(rust) };
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_chain_get_effect_count(chain: *mut c_void) -> i32 {
    let Some(rust) = chain_rust(chain) else {
        return 0;
    };
    unsafe { klarinet_echain_count(rust) }
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_chain_enqueue_param(
    chain: *mut c_void,
    effect: *mut c_void,
    param_id: i32,
    value: f32,
) {
    let Some(rust) = chain_rust(chain) else {
        return;
    };
    if effect.is_null() {
        return;
    }
    unsafe { klarinet_echain_enqueue(rust, effect, Some(node_set_parameter), param_id, value) };
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_chain_destroy(chain: *mut c_void) {
    if chain.is_null() {
        return;
    }
    drop(unsafe { Box::from_raw(chain.cast::<ChainSlot>()) });
}

#[cfg(test)]
mod tests {
    use super::{
        klarinet_chain_add, klarinet_chain_clear, klarinet_chain_create, klarinet_chain_destroy,
        klarinet_chain_get_effect_count, klarinet_chain_prepare, klarinet_chain_process,
        klarinet_create_effect, klarinet_effect_destroy, klarinet_effect_get_parameter,
        klarinet_effect_get_type, klarinet_effect_is_enabled, klarinet_effect_set_enabled,
        klarinet_effect_set_parameter,
    };

    #[test]
    fn null_handles_match_the_c_api() {
        unsafe {
            assert!(klarinet_create_effect(99).is_null());
            assert!(klarinet_effect_get_parameter(std::ptr::null_mut(), 0).abs() < f32::EPSILON);
            assert_eq!(klarinet_effect_is_enabled(std::ptr::null_mut()), 0);
            assert_eq!(klarinet_effect_get_type(std::ptr::null_mut()), -1);
            assert_eq!(klarinet_chain_get_effect_count(std::ptr::null_mut()), 0);
            let mut sample = 1.0f32;
            klarinet_chain_process(std::ptr::null_mut(), &mut sample, 1, 1);
            assert!((sample - 1.0).abs() < f32::EPSILON);
        }
    }

    #[test]
    fn gain_stays_alive_after_destroy_while_the_chain_holds_it() {
        unsafe {
            let effect = klarinet_create_effect(0);
            assert!(!effect.is_null());
            assert_eq!(klarinet_effect_get_type(effect), 0);
            assert_eq!(klarinet_effect_is_enabled(effect), 1);
            klarinet_effect_set_parameter(effect, 0, -20.0);
            assert!((klarinet_effect_get_parameter(effect, 0) + 20.0).abs() < 0.001);

            let chain = klarinet_chain_create();
            assert!(!chain.is_null());
            klarinet_chain_add(chain, effect);
            assert_eq!(klarinet_chain_get_effect_count(chain), 1);
            klarinet_chain_prepare(chain, 48_000, 1);

            klarinet_effect_set_enabled(effect, 0);
            let mut bypass = [1.0f32];
            klarinet_chain_process(chain, bypass.as_mut_ptr(), 1, 1);
            assert!((bypass[0] - 1.0).abs() < 0.001);

            klarinet_effect_set_enabled(effect, 1);
            klarinet_effect_destroy(effect);
            let mut attenuated = [1.0f32];
            klarinet_chain_process(chain, attenuated.as_mut_ptr(), 1, 1);
            assert!(
                (attenuated[0] - 0.1).abs() < 0.001,
                "gain was {}",
                attenuated[0]
            );

            klarinet_chain_clear(chain);
            klarinet_chain_process(chain, attenuated.as_mut_ptr(), 1, 1);
            klarinet_chain_destroy(chain);
        }
    }
}
