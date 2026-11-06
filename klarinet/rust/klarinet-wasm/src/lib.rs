//! `klarinet_dsp.h` effect and chain ABI for the JS worklet.
//!
//! Each wasm instance owns its registry. `klarinet_worklet_process` loads the
//! chain installed by `klarinet_worklet_use_chain` and does not allocate.
//! Create, destroy, and `klarinet_chain_add` take the registry mutex.
//! `klarinet_chain_process` does not. One retired effect list is freed at the
//! start of process, the same exception as `Chain::process`.

use std::collections::HashMap;
use std::ffi::c_void;
use std::ptr;
use std::sync::atomic::{AtomicBool, AtomicPtr, AtomicU32, Ordering};
use std::sync::{Arc, Mutex, OnceLock};

#[used]
static KLARINET_DSP_LINK: fn() = klarinet_dsp_core::link_effects;

#[used]
static KLARINET_DSP_ANALYSIS: fn() = klarinet_dsp_core::link_analysis;

extern "C" {
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

// The slot owns `fx`. Process runs only while the chain's Arc is alive.
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

/// # Safety
/// `effect_type` is an `AudioEffectType` ordinal. The returned handle is owned by the registry until `klarinet_effect_destroy`.
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

/// # Safety
/// `handle` is null or a pointer returned by `klarinet_create_effect` that has not been destroyed.
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

/// # Safety
/// `handle` is null or a live effect handle.
#[no_mangle]
pub unsafe extern "C" fn klarinet_effect_get_parameter(handle: *mut c_void, param_id: i32) -> f32 {
    if handle.is_null() {
        return 0.0;
    }
    let slot = unsafe { &*(handle.cast::<EffectSlot>()) };
    unsafe { klarinet_fx_get_parameter(slot.fx, param_id) }
}

/// # Safety
/// `handle` is null or a live effect handle.
#[no_mangle]
pub unsafe extern "C" fn klarinet_effect_set_enabled(handle: *mut c_void, enabled: i32) {
    if handle.is_null() {
        return;
    }
    let slot = unsafe { &*(handle.cast::<EffectSlot>()) };
    slot.enabled.store(enabled != 0, Ordering::Relaxed);
}

/// # Safety
/// `handle` is null or a live effect handle.
#[no_mangle]
pub unsafe extern "C" fn klarinet_effect_is_enabled(handle: *mut c_void) -> i32 {
    if handle.is_null() {
        return 0;
    }
    let slot = unsafe { &*(handle.cast::<EffectSlot>()) };
    i32::from(slot.enabled.load(Ordering::Relaxed))
}

/// # Safety
/// `handle` is null or a live effect handle.
#[no_mangle]
pub unsafe extern "C" fn klarinet_effect_get_type(handle: *mut c_void) -> i32 {
    if handle.is_null() {
        return -1;
    }
    unsafe { (*handle.cast::<EffectSlot>()).effect_type }
}

/// # Safety
/// `handle` is null or a live effect handle. Called from the worklet message handler, not from `process`.
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

/// # Safety
/// `handle` is null or a live effect handle.
#[no_mangle]
pub unsafe extern "C" fn klarinet_effect_reset(handle: *mut c_void) {
    if handle.is_null() {
        return;
    }
    let slot = unsafe { &*(handle.cast::<EffectSlot>()) };
    unsafe { klarinet_fx_reset(slot.fx) };
}

/// # Safety
/// `handle` is null or a live effect handle. A chain that still holds the effect keeps it alive.
#[no_mangle]
pub unsafe extern "C" fn klarinet_effect_destroy(handle: *mut c_void) {
    if handle.is_null() {
        return;
    }
    lock_registry().remove(&(handle as usize));
}

/// # Safety
/// The returned chain is destroyed with `klarinet_chain_destroy`.
#[no_mangle]
pub unsafe extern "C" fn klarinet_chain_create() -> *mut c_void {
    let rust = unsafe { klarinet_echain_create() };
    if rust.is_null() {
        return ptr::null_mut();
    }
    Box::into_raw(Box::new(ChainSlot { rust })).cast()
}

/// # Safety
/// `chain` is null or a live chain. `effect` is null or a registry handle.
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
}

/// # Safety
/// `chain` is null or a live chain. `effect` is the handle passed to `klarinet_chain_add`.
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

/// # Safety
/// `chain` is null or a live chain. `buffer` is null or covers `num_frames * channel_count` floats.
/// This is the audio-thread entry. It does not take the registry mutex.
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

/// # Safety
/// `chain` is null or a live chain. Called from the worklet message handler, not from `process`.
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

/// # Safety
/// `chain` is null or a live chain.
#[no_mangle]
pub unsafe extern "C" fn klarinet_chain_clear(chain: *mut c_void) {
    let Some(rust) = chain_rust(chain) else {
        return;
    };
    unsafe { klarinet_echain_clear(rust) };
}

/// # Safety
/// `chain` is null or a live chain.
#[no_mangle]
pub unsafe extern "C" fn klarinet_chain_get_effect_count(chain: *mut c_void) -> i32 {
    let Some(rust) = chain_rust(chain) else {
        return 0;
    };
    unsafe { klarinet_echain_count(rust) }
}

/// # Safety
/// `chain` is null or a live chain. `effect` is null or a live effect handle.
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

/// # Safety
/// `chain` is null or a live chain. Effects stay in the registry.
#[no_mangle]
pub unsafe extern "C" fn klarinet_chain_destroy(chain: *mut c_void) {
    if chain.is_null() {
        return;
    }
    drop(unsafe { Box::from_raw(chain.cast::<ChainSlot>()) });
}

static WORKLET_CHAIN: AtomicPtr<c_void> = AtomicPtr::new(ptr::null_mut());
static SCRATCH: AtomicPtr<f32> = AtomicPtr::new(ptr::null_mut());
static SCRATCH_LEN: AtomicU32 = AtomicU32::new(0);

/// # Safety
/// Called from the worklet constructor or message handler, before the matching `process`.
#[no_mangle]
pub extern "C" fn klarinet_worklet_scratch(samples: i32) -> *mut f32 {
    let existing = SCRATCH.load(Ordering::Acquire);
    if !existing.is_null() {
        return existing;
    }
    let count = if samples > 0 { samples as usize } else { 4096 };
    let mut data = vec![0.0f32; count];
    let ptr = data.as_mut_ptr();
    std::mem::forget(data);
    SCRATCH_LEN.store(count as u32, Ordering::Release);
    SCRATCH.store(ptr, Ordering::Release);
    ptr
}

/// # Safety
/// `chain` is null or a live chain from this wasm instance.
#[no_mangle]
pub extern "C" fn klarinet_worklet_use_chain(chain: *mut c_void) {
    WORKLET_CHAIN.store(chain, Ordering::Release);
}

/// # Safety
/// `ptr` is null or the scratch pointer from `klarinet_worklet_scratch`, covering `frames * channels` floats.
/// Audio thread: no allocation, no registry lock, no panic path beyond the chain's one retired-list free.
#[no_mangle]
pub unsafe extern "C" fn klarinet_worklet_process(ptr: *mut f32, frames: i32, channels: i32) {
    if ptr.is_null() || frames <= 0 || channels <= 0 {
        return;
    }
    let chain = WORKLET_CHAIN.load(Ordering::Acquire);
    if chain.is_null() {
        return;
    }
    unsafe { klarinet_chain_process(chain, ptr, frames, channels) };
}

#[cfg(target_arch = "wasm32")]
#[link(wasm_import_module = "env")]
extern "C" {
    fn klarinet_js_resampler_pull(user: *mut c_void, dst: *mut f32, max_frames: i32) -> i32;
}

#[cfg(not(target_arch = "wasm32"))]
unsafe extern "C" fn klarinet_js_resampler_pull(
    _user: *mut c_void,
    _dst: *mut f32,
    _max_frames: i32,
) -> i32 {
    0
}

/// Function index of the resampler read import. The JS file worker passes it to
/// `klarinet_resample_linear`. The worklet supplies a stub and does not call it.
#[no_mangle]
pub extern "C" fn klarinet_js_pull_fn() -> u32 {
    klarinet_js_resampler_pull as *const () as usize as u32
}

/// # Safety
/// `size` is the byte length later passed to [`klarinet_wasm_free`]. Not the audio thread.
#[no_mangle]
pub extern "C" fn klarinet_wasm_alloc(size: i32) -> *mut u8 {
    if size <= 0 {
        return std::ptr::null_mut();
    }
    let Ok(layout) = std::alloc::Layout::from_size_align(size as usize, 8) else {
        return std::ptr::null_mut();
    };
    unsafe { std::alloc::alloc(layout) }
}

/// # Safety
/// `ptr` is null or a pointer returned by [`klarinet_wasm_alloc`] for the same `size`.
#[no_mangle]
pub unsafe extern "C" fn klarinet_wasm_free(ptr: *mut u8, size: i32) {
    if ptr.is_null() || size <= 0 {
        return;
    }
    let Ok(layout) = std::alloc::Layout::from_size_align(size as usize, 8) else {
        return;
    };
    unsafe { std::alloc::dealloc(ptr, layout) };
}

/// # Safety
/// `samples` covers `sample_count` floats. `out` covers `out_cap` bytes. Not the audio thread.
#[no_mangle]
pub unsafe extern "C" fn encode_wav16(
    samples: *const f32,
    sample_count: i32,
    sample_rate: i32,
    channel_count: i32,
    out: *mut u8,
    out_cap: i32,
) -> i32 {
    if sample_count < 0 || out.is_null() || out_cap < 0 || (sample_count > 0 && samples.is_null()) {
        return -1;
    }
    let slice = if sample_count == 0 {
        &[]
    } else {
        unsafe { std::slice::from_raw_parts(samples, sample_count as usize) }
    };
    let encoded = match klarinet_wav::encode_wav16(slice, sample_rate, channel_count) {
        Ok(bytes) => bytes,
        Err(_) => return -1,
    };
    if encoded.len() > out_cap as usize {
        return -1;
    }
    unsafe { std::ptr::copy_nonoverlapping(encoded.as_ptr(), out, encoded.len()) };
    encoded.len() as i32
}

/// # Safety
/// `bytes` covers `byte_len` bytes. `out`, when non-null, covers `out_cap` floats.
/// The `i32` pointers are null or point at writable integers. Not the audio thread.
/// Returns the sample count, or a negative [`klarinet_wav::WavError`] code.
#[no_mangle]
pub unsafe extern "C" fn decode_wav(
    bytes: *const u8,
    byte_len: i32,
    out: *mut f32,
    out_cap: i32,
    sample_rate: *mut i32,
    channels: *mut i32,
    error_format: *mut i32,
    error_bits: *mut i32,
) -> i32 {
    if byte_len < 0 || bytes.is_null() {
        return -1;
    }
    let slice = unsafe { std::slice::from_raw_parts(bytes, byte_len as usize) };
    match klarinet_wav::decode_wav(slice) {
        Ok(decoded) => {
            if decoded.samples.len() > out_cap.max(0) as usize
                || (out.is_null() && !decoded.samples.is_empty())
            {
                return -1;
            }
            if !decoded.samples.is_empty() {
                unsafe {
                    std::ptr::copy_nonoverlapping(
                        decoded.samples.as_ptr(),
                        out,
                        decoded.samples.len(),
                    );
                }
            }
            write_i32_at(sample_rate, decoded.sample_rate);
            write_i32_at(channels, decoded.channel_count);
            decoded.samples.len() as i32
        }
        Err(klarinet_wav::WavError::NotWav) => -1,
        Err(klarinet_wav::WavError::MissingData) => -2,
        Err(klarinet_wav::WavError::InvalidFmt) | Err(klarinet_wav::WavError::Io(_)) => -3,
        Err(klarinet_wav::WavError::Unsupported { format, bits }) => {
            write_i32_at(error_format, format);
            write_i32_at(error_bits, bits);
            -4
        }
    }
}

fn write_i32_at(ptr: *mut i32, value: i32) {
    if !ptr.is_null() {
        unsafe { *ptr = value };
    }
}

#[cfg(test)]
mod tests {
    use super::{
        klarinet_chain_add, klarinet_chain_create, klarinet_chain_destroy,
        klarinet_chain_get_effect_count, klarinet_chain_prepare, klarinet_chain_process,
        klarinet_chain_remove, klarinet_create_effect, klarinet_effect_destroy,
        klarinet_effect_get_parameter, klarinet_effect_get_type, klarinet_effect_is_enabled,
        klarinet_effect_prepare, klarinet_effect_set_enabled, klarinet_effect_set_parameter,
        klarinet_worklet_process, klarinet_worklet_scratch, klarinet_worklet_use_chain,
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
    fn two_gains_keep_separate_parameters_and_use_prepare_rate() {
        unsafe {
            let first = klarinet_create_effect(0);
            let second = klarinet_create_effect(0);
            assert!(!first.is_null() && !second.is_null());
            klarinet_effect_prepare(first, 44_100, 1);
            klarinet_effect_prepare(second, 44_100, 1);
            klarinet_effect_set_parameter(first, 1, 1_000.0);
            klarinet_effect_set_parameter(first, 0, -20.0);
            klarinet_effect_set_parameter(second, 0, -6.0);
            assert!((klarinet_effect_get_parameter(first, 0) + 20.0).abs() < 0.001);
            assert!((klarinet_effect_get_parameter(second, 0) + 6.0).abs() < 0.001);

            let chain = klarinet_chain_create();
            klarinet_chain_add(chain, first);
            klarinet_chain_add(chain, second);
            assert_eq!(klarinet_chain_get_effect_count(chain), 2);
            klarinet_chain_prepare(chain, 44_100, 1);
            klarinet_worklet_use_chain(chain);

            let mut fading = [1.0f32];
            klarinet_worklet_process(fading.as_mut_ptr(), 1, 1);
            let step = 1.0 / 44_100.0;
            let expected = 1.0 + (0.1 - 1.0) * step;
            let second_linear = 10.0_f32.powf(-6.0 / 20.0);
            assert!(
                (fading[0] - expected * second_linear).abs() < 1.0e-5,
                "fading {}",
                fading[0]
            );

            klarinet_effect_set_enabled(first, 0);
            let mut bypassed = [1.0f32];
            klarinet_chain_process(chain, bypassed.as_mut_ptr(), 1, 1);
            assert!((bypassed[0] - second_linear).abs() < 0.001);

            klarinet_chain_remove(chain, second);
            klarinet_effect_destroy(second);
            bypassed[0] = 1.0;
            klarinet_chain_process(chain, bypassed.as_mut_ptr(), 1, 1);
            assert!(
                (bypassed[0] - 1.0).abs() < 0.001,
                "removed gain still ran {}",
                bypassed[0]
            );

            let scratch = klarinet_worklet_scratch(4);
            assert!(!scratch.is_null());
            klarinet_worklet_use_chain(std::ptr::null_mut());
            klarinet_chain_destroy(chain);
            klarinet_effect_destroy(first);
        }
    }
}
