//! Lock-free effect-chain hot swap.
//!
//! The control thread copies the active slot list, mutates the copy, and
//! swaps it in. The audio thread frees one retired list at the start of
//! `process`, drains the parameter ring, then calls each effect. `process`
//! does not allocate, lock, or panic. Overwriting a list that the audio
//! thread has not freed yet leaks that list, matching the C++ chain.

use crate::ring::Ring;
use std::ptr;
use std::sync::atomic::{AtomicPtr, Ordering};
use std::sync::Mutex;

pub type ProcessFn = unsafe extern "C" fn(*mut std::ffi::c_void, *mut f32, i32, i32);
pub type PrepareFn = unsafe extern "C" fn(*mut std::ffi::c_void, i32, i32);
pub type DestroyFn = unsafe extern "C" fn(*mut std::ffi::c_void);
pub type CloneFn = unsafe extern "C" fn(*mut std::ffi::c_void) -> *mut std::ffi::c_void;
pub type SetParamFn = unsafe extern "C" fn(*mut std::ffi::c_void, i32, f32);

#[repr(C)]
#[derive(Clone, Copy)]
struct ParamChange {
    effect: *mut std::ffi::c_void,
    set_param: Option<SetParamFn>,
    param_id: i32,
    value: f32,
}

struct Slot {
    key: *mut std::ffi::c_void,
    ctx: *mut std::ffi::c_void,
    process: Option<ProcessFn>,
    prepare: Option<PrepareFn>,
    destroy: Option<DestroyFn>,
    clone_ctx: Option<CloneFn>,
}

struct EffectList {
    slots: Vec<Slot>,
}

impl Drop for EffectList {
    fn drop(&mut self) {
        for slot in &self.slots {
            if let Some(destroy) = slot.destroy {
                unsafe { destroy(slot.ctx) };
            }
        }
    }
}

struct Control {
    sample_rate: i32,
    channel_count: i32,
}

pub struct Chain {
    active: AtomicPtr<EffectList>,
    pending: AtomicPtr<EffectList>,
    control: Mutex<Control>,
    params: Ring,
}

// Safety: the audio thread calls `process` only. Control threads take
// `control` and replace `active` by swapping a finished list into place.
// They never mutate a list after publishing it. `params` is a single-producer
// single-consumer ring.
unsafe impl Send for Chain {}
unsafe impl Sync for Chain {}

impl Chain {
    pub fn new() -> Option<Box<Self>> {
        let params = Ring::new(
            512,
            std::mem::size_of::<ParamChange>() as i32,
            std::mem::align_of::<ParamChange>() as i32,
        )?;
        let active = Box::into_raw(Box::new(EffectList { slots: Vec::new() }));
        Some(Box::new(Self {
            active: AtomicPtr::new(active),
            pending: AtomicPtr::new(ptr::null_mut()),
            control: Mutex::new(Control {
                sample_rate: 48000,
                channel_count: 1,
            }),
            params,
        }))
    }

    fn add(&self, slot: Slot) {
        let control = self.lock_control();
        if let Some(prepare) = slot.prepare {
            unsafe { prepare(slot.ctx, control.sample_rate, control.channel_count) };
        }
        let mut new_list = self.snapshot();
        new_list.slots.push(slot);
        self.install(new_list);
    }

    pub fn remove(&self, key: *mut std::ffi::c_void) {
        let _control = self.lock_control();
        let current = self.active.load(Ordering::Acquire);
        let mut new_list = EffectList { slots: Vec::new() };
        if !current.is_null() {
            let current = unsafe { &*current };
            new_list.slots.reserve(current.slots.len());
            for slot in &current.slots {
                if slot.key == key {
                    continue;
                }
                if let Some(copied) = duplicate(slot) {
                    new_list.slots.push(copied);
                }
            }
        }
        self.install(new_list);
    }

    pub fn reorder(&self, keys: &[*mut std::ffi::c_void]) {
        let _control = self.lock_control();
        let current = self.active.load(Ordering::Acquire);
        let mut new_list = EffectList {
            slots: Vec::with_capacity(keys.len()),
        };
        if !current.is_null() {
            let current = unsafe { &*current };
            for key in keys {
                if let Some(slot) = current.slots.iter().find(|slot| slot.key == *key) {
                    if let Some(copied) = duplicate(slot) {
                        new_list.slots.push(copied);
                    }
                }
            }
        }
        self.install(new_list);
    }

    pub fn clear(&self) {
        let _control = self.lock_control();
        self.install(EffectList { slots: Vec::new() });
    }

    pub fn enqueue(
        &self,
        effect: *mut std::ffi::c_void,
        set_param: Option<SetParamFn>,
        param_id: i32,
        value: f32,
    ) {
        let change = ParamChange {
            effect,
            set_param,
            param_id,
            value,
        };
        let _ = self.params.push(ptr::from_ref(&change).cast::<u8>());
    }

    pub fn prepare(&self, sample_rate: i32, channel_count: i32) {
        let mut control = self.lock_control();
        control.sample_rate = sample_rate;
        control.channel_count = channel_count;
        let current = self.active.load(Ordering::Acquire);
        if current.is_null() {
            return;
        }
        let list = unsafe { &*current };
        for slot in &list.slots {
            if let Some(prepare) = slot.prepare {
                unsafe { prepare(slot.ctx, sample_rate, channel_count) };
            }
        }
    }

    /// Audio thread. Frees at most one retired list, then runs the effects.
    pub fn process(&self, buffer: *mut f32, num_frames: i32, channel_count: i32) {
        let retired = self.pending.swap(ptr::null_mut(), Ordering::AcqRel);
        if !retired.is_null() {
            drop(unsafe { Box::from_raw(retired) });
        }

        loop {
            let mut change = ParamChange {
                effect: ptr::null_mut(),
                set_param: None,
                param_id: 0,
                value: 0.0,
            };
            if !self.params.pop(ptr::from_mut(&mut change).cast::<u8>()) {
                break;
            }
            if change.effect.is_null() {
                continue;
            }
            if let Some(set_param) = change.set_param {
                unsafe { set_param(change.effect, change.param_id, change.value) };
            }
        }

        let current = self.active.load(Ordering::Acquire);
        if current.is_null() {
            return;
        }
        let list = unsafe { &*current };
        for slot in &list.slots {
            if let Some(process) = slot.process {
                unsafe { process(slot.ctx, buffer, num_frames, channel_count) };
            }
        }
    }

    pub fn count(&self) -> i32 {
        let current = self.active.load(Ordering::Acquire);
        if current.is_null() {
            return 0;
        }
        let len = unsafe { &*current }.slots.len();
        i32::try_from(len).unwrap_or(i32::MAX)
    }

    fn lock_control(&self) -> std::sync::MutexGuard<'_, Control> {
        self.control.lock().unwrap_or_else(|err| err.into_inner())
    }

    fn snapshot(&self) -> EffectList {
        let current = self.active.load(Ordering::Acquire);
        if current.is_null() {
            return EffectList { slots: Vec::new() };
        }
        let current = unsafe { &*current };
        let mut slots = Vec::with_capacity(current.slots.len());
        for slot in &current.slots {
            if let Some(copied) = duplicate(slot) {
                slots.push(copied);
            }
        }
        EffectList { slots }
    }

    fn install(&self, new_list: EffectList) {
        let new_ptr = Box::into_raw(Box::new(new_list));
        let old = self.active.swap(new_ptr, Ordering::AcqRel);
        let overwritten = self.pending.swap(old, Ordering::AcqRel);
        // The C++ chain assigns pendingDelete_ without freeing the previous
        // pointer. A second control-thread swap before process() leaks it.
        if !overwritten.is_null() {
            let _leaked: &mut EffectList = unsafe { Box::leak(Box::from_raw(overwritten)) };
        }
    }
}

impl Drop for Chain {
    fn drop(&mut self) {
        let active = self.active.swap(ptr::null_mut(), Ordering::Relaxed);
        if !active.is_null() {
            drop(unsafe { Box::from_raw(active) });
        }
        let pending = self.pending.swap(ptr::null_mut(), Ordering::Relaxed);
        if !pending.is_null() {
            drop(unsafe { Box::from_raw(pending) });
        }
    }
}

fn duplicate(slot: &Slot) -> Option<Slot> {
    let clone_ctx = slot.clone_ctx?;
    let ctx = unsafe { clone_ctx(slot.ctx) };
    if ctx.is_null() {
        return None;
    }
    Some(Slot {
        key: slot.key,
        ctx,
        process: slot.process,
        prepare: slot.prepare,
        destroy: slot.destroy,
        clone_ctx: slot.clone_ctx,
    })
}

pub struct NewSlot {
    pub key: *mut std::ffi::c_void,
    pub ctx: *mut std::ffi::c_void,
    pub process: Option<ProcessFn>,
    pub prepare: Option<PrepareFn>,
    pub destroy: Option<DestroyFn>,
    pub clone_ctx: Option<CloneFn>,
}

impl Chain {
    pub fn add_slot(&self, slot: NewSlot) {
        self.add(Slot {
            key: slot.key,
            ctx: slot.ctx,
            process: slot.process,
            prepare: slot.prepare,
            destroy: slot.destroy,
            clone_ctx: slot.clone_ctx,
        });
    }
}
