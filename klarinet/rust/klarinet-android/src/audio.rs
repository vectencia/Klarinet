//! Oboe data callback, worker, and routing dispatch.
//!
//! `on_audio` is the data callback. It does not allocate, lock, panic, log,
//! or enter the JVM. Samples move through `klarinet_fifo_*`. The effect chain
//! runs on that callback. The worker and the routing callback may enter the
//! JVM. They do not take a lock `on_audio` needs.

use std::ffi::CStr;
use std::os::raw::c_void;
use std::ptr;
#[cfg(test)]
use std::ptr::NonNull;
use std::sync::atomic::{AtomicBool, AtomicI32, AtomicPtr, Ordering};
use std::sync::{Mutex, Once};
use std::thread::{self, JoinHandle, Thread};
use std::time::Duration;

use crate::dsp;
use crate::jni::{self, JniObject};
use crate::lock;
use crate::oboe;

const TAG_CALLBACK: &CStr = c"KlarinetCallback";
const ROUTE_CLASS: &CStr = c"com/vectencia/klarinet/AndroidRouteEvents";
const ROUTE_METHOD: &CStr = c"dispatch";
const ROUTE_SIG: &CStr = c"([I[I)V";
const READY_NAME: &CStr = c"onAudioReady";
const READY_SIG: &CStr = c"([FI)I";
const XRUN_NAME: &CStr = c"notifyXrun";
const XRUN_SIG: &CStr = c"(I)V";

static ROUTE_ONCE: Once = Once::new();
static ROUTE_CLASS_REF: AtomicPtr<c_void> = AtomicPtr::new(ptr::null_mut());
static ROUTE_METHOD_ID: AtomicPtr<c_void> = AtomicPtr::new(ptr::null_mut());

/// A parked `Thread` at a stable address. The audio callback holds `*mut Thread`.
struct StableParker(Box<Thread>);

impl StableParker {
    fn into_thread(self) -> Thread {
        *self.0
    }
}

struct SendState(*const AudioState);

// The worker copies this pointer. `stop_worker` joins that thread before the
// state is freed, and `on_audio` never locks the worker handle.
unsafe impl Send for SendState {}

impl SendState {
    /// Moves the whole wrapper into the worker. Projecting `.0` in the
    /// `thread::spawn` closure would capture the raw pointer, which is not `Send`.
    unsafe fn run(self) {
        unsafe { (*self.0).worker_loop() }
    }
}

pub struct AudioState {
    jvm: AtomicPtr<c_void>,
    callback: AtomicPtr<c_void>,
    callback_owned: AtomicBool,
    on_ready: AtomicPtr<c_void>,
    on_xrun: AtomicPtr<c_void>,
    direction: AtomicI32,
    frames_per_burst: AtomicI32,
    channel_count: AtomicI32,
    fifo: AtomicPtr<c_void>,
    chain: AtomicPtr<c_void>,
    xruns: AtomicI32,
    running: AtomicBool,
    worker: Mutex<Option<JoinHandle<()>>>,
    parker: AtomicPtr<Thread>,
    retired_parkers: Mutex<Vec<StableParker>>,
    routed: Mutex<Vec<i32>>,
}

/// Sample count for a playback block that fits in `i32`.
/// A non-positive count is `None`. An overflowing positive pair is `None`.
pub fn playback_samples(frames: i32, channels: i32) -> Option<i32> {
    positive_product(frames, channels)
}

/// Sample count for a capture block that fits in `i32`.
/// A non-positive count is `None`. An overflowing positive pair is `None`.
pub fn samples_to_write(frames: i32, channels: i32) -> Option<i32> {
    positive_product(frames, channels)
}

fn positive_product(frames: i32, channels: i32) -> Option<i32> {
    if frames <= 0 || channels <= 0 {
        None
    } else {
        frames.checked_mul(channels)
    }
}

impl AudioState {
    fn empty() -> Self {
        Self {
            jvm: AtomicPtr::new(ptr::null_mut()),
            callback: AtomicPtr::new(ptr::null_mut()),
            callback_owned: AtomicBool::new(false),
            on_ready: AtomicPtr::new(ptr::null_mut()),
            on_xrun: AtomicPtr::new(ptr::null_mut()),
            direction: AtomicI32::new(0),
            frames_per_burst: AtomicI32::new(0),
            channel_count: AtomicI32::new(0),
            fifo: AtomicPtr::new(ptr::null_mut()),
            chain: AtomicPtr::new(ptr::null_mut()),
            xruns: AtomicI32::new(0),
            running: AtomicBool::new(false),
            worker: Mutex::new(None),
            parker: AtomicPtr::new(ptr::null_mut()),
            retired_parkers: Mutex::new(Vec::new()),
            routed: Mutex::new(Vec::new()),
        }
    }

    /// Caches the route class on this Java thread, then the callback methods.
    /// `GetMethodID` for `onAudioReady` does not clear a pending exception.
    /// `notifyXrun` does, matching `KlarinetCallback::KlarinetCallback`.
    pub unsafe fn create(env: *mut jni::JniEnv, callback: JniObject) -> Box<Self> {
        let state = Box::new(Self::empty());
        if !env.is_null() {
            let mut vm = ptr::null_mut();
            jni::vm_from_env(env, &mut vm);
            state.jvm.store(vm, Ordering::Release);
            ensure_route(env);
        }
        if callback.is_null() || env.is_null() {
            return state;
        }
        let global = jni::new_global_ref(env, callback);
        if global.is_null() {
            return state;
        }
        state.callback.store(global, Ordering::Release);
        state.callback_owned.store(true, Ordering::Release);
        let class = jni::get_object_class(env, callback);
        let ready = jni::get_method_id(env, class, READY_NAME.as_ptr(), READY_SIG.as_ptr());
        if ready.is_null() {
            oboe::log_error_lit(
                TAG_CALLBACK,
                c"Could not find onAudioReady method on callback",
            );
        } else {
            state.on_ready.store(ready, Ordering::Release);
        }
        let xrun = jni::get_method_id(env, class, XRUN_NAME.as_ptr(), XRUN_SIG.as_ptr());
        if xrun.is_null() {
            jni::exception_clear(env);
            oboe::log_error_lit(
                TAG_CALLBACK,
                c"Could not find notifyXrun method on callback",
            );
        } else {
            state.on_xrun.store(xrun, Ordering::Release);
        }
        if !class.is_null() {
            jni::delete_local_ref(env, class);
        }
        state
    }

    pub fn prepare(&self, frames_per_burst: i32, channel_count: i32, is_input: i32) {
        self.stop_worker();
        let frames = if frames_per_burst > 0 {
            frames_per_burst
        } else {
            192
        };
        let channels = if channel_count > 0 { channel_count } else { 1 };
        self.frames_per_burst.store(frames, Ordering::Release);
        self.channel_count.store(channels, Ordering::Release);
        self.direction
            .store(i32::from(is_input != 0), Ordering::Release);
        self.xruns.store(0, Ordering::Relaxed);
        self.destroy_fifo();
        if let Some(capacity) = frames.checked_mul(channels).and_then(|n| n.checked_mul(8)) {
            let fifo = dsp::fifo_create(capacity);
            self.fifo.store(fifo, Ordering::Release);
        }
        if self.has_callback() && self.has_ready_method() && self.has_jvm() {
            self.running.store(true, Ordering::Release);
            self.start_worker();
        }
    }

    pub fn note_routed(&self, device_ids: &[i32]) {
        let mut routed = lock(&self.routed);
        routed.clear();
        routed.extend_from_slice(device_ids);
    }

    pub fn set_chain(&self, chain: *mut c_void) {
        self.chain.store(chain, Ordering::Release);
    }

    #[cfg(test)]
    pub fn chain(&self) -> *mut c_void {
        self.chain.load(Ordering::Acquire)
    }

    #[cfg(test)]
    pub fn xrun_count(&self) -> i32 {
        self.xruns.load(Ordering::Acquire)
    }

    /// Data callback. `is_input` is 1 for capture. Frame and channel counts
    /// come from the stream at this call, not from `prepare`.
    pub fn on_audio(&self, is_input: i32, samples: *mut f32, num_frames: i32, channel_count: i32) {
        self.exchange(is_input, samples, num_frames, channel_count);
        self.wake();
    }

    fn exchange(&self, is_input: i32, samples: *mut f32, num_frames: i32, channel_count: i32) {
        if num_frames < 0 || channel_count < 0 {
            return;
        }
        let total = if is_input != 0 {
            samples_to_write(num_frames, channel_count)
        } else {
            playback_samples(num_frames, channel_count)
        };
        let Some(total) = total else {
            if num_frames == 0 || channel_count == 0 {
                self.run_chain(samples, num_frames, channel_count);
            }
            return;
        };
        if samples.is_null() {
            return;
        }
        let fifo = self.fifo.load(Ordering::Acquire);
        let has_callback = self.has_callback();
        if is_input != 0 {
            self.run_chain(samples, num_frames, channel_count);
            if !fifo.is_null() && has_callback {
                let written = dsp::fifo_write(fifo, samples, total);
                if written < total {
                    self.xruns.fetch_add(1, Ordering::Release);
                }
            }
        } else if !fifo.is_null() && has_callback {
            let got = dsp::fifo_read(fifo, samples, total);
            if got < total {
                let start = got.max(0) as usize;
                let end = total as usize;
                if start < end {
                    unsafe { ptr::write_bytes(samples.add(start), 0, end - start) };
                }
                self.xruns.fetch_add(1, Ordering::Release);
            }
            self.run_chain(samples, num_frames, channel_count);
        } else {
            unsafe { ptr::write_bytes(samples, 0, total as usize) };
            self.run_chain(samples, num_frames, channel_count);
        }
    }

    fn run_chain(&self, samples: *mut f32, num_frames: i32, channel_count: i32) {
        let chain = self.chain.load(Ordering::Acquire);
        if chain.is_null() || samples.is_null() {
            return;
        }
        dsp::chain_process(chain, samples, num_frames, channel_count);
    }

    /// Routing thread. Copies the ids, drops the route lock, then enters JNI.
    pub fn on_route(&self, device_ids: *const i32, num_devices: i32) {
        if !self.has_jvm() {
            return;
        }
        let current = copy_ids(device_ids, num_devices);
        let previous = {
            let mut routed = lock(&self.routed);
            let previous = routed.clone();
            *routed = current.clone();
            previous
        };
        let vm = self.jvm.load(Ordering::Acquire);
        let Some((env, attached)) = (unsafe { jni::attach_or_get(vm) }) else {
            return;
        };
        dispatch_route(env, &previous, &current);
        if attached {
            unsafe { jni::detach(vm) };
        }
    }

    pub fn shutdown(&self) {
        self.stop_worker();
        self.destroy_fifo();
        self.release_callback();
        self.drop_parkers();
    }

    fn has_callback(&self) -> bool {
        !self.callback.load(Ordering::Acquire).is_null()
    }

    fn has_ready_method(&self) -> bool {
        !self.on_ready.load(Ordering::Acquire).is_null()
    }

    fn has_jvm(&self) -> bool {
        !self.jvm.load(Ordering::Acquire).is_null()
    }

    fn stop_worker(&self) {
        self.running.store(false, Ordering::Release);
        self.wake();
        if let Some(handle) = lock(&self.worker).take() {
            let _ = handle.join();
        }
    }

    fn start_worker(&self) {
        let ptr = SendState(self as *const AudioState);
        let handle = thread::spawn(move || unsafe { ptr.run() });
        *lock(&self.worker) = Some(handle);
    }

    fn wake(&self) {
        let parker = self.parker.load(Ordering::Acquire);
        if !parker.is_null() {
            unsafe { (*parker).unpark() };
        }
    }

    fn destroy_fifo(&self) {
        let fifo = self.fifo.swap(ptr::null_mut(), Ordering::AcqRel);
        if !fifo.is_null() {
            dsp::fifo_destroy(fifo);
        }
    }

    fn release_callback(&self) {
        if !self.callback_owned.swap(false, Ordering::AcqRel) {
            return;
        }
        let callback = self.callback.swap(ptr::null_mut(), Ordering::AcqRel);
        let vm = self.jvm.load(Ordering::Acquire);
        if callback.is_null() || vm.is_null() {
            return;
        }
        let Some((env, attached)) = (unsafe { jni::attach_or_get(vm) }) else {
            return;
        };
        unsafe { jni::delete_global_ref(env, callback) };
        if attached {
            unsafe { jni::detach(vm) };
        }
    }

    fn drop_parkers(&self) {
        let current = self.parker.swap(ptr::null_mut(), Ordering::AcqRel);
        if !current.is_null() {
            drop(unsafe { Box::from_raw(current) });
        }
        for parker in std::mem::take(&mut *lock(&self.retired_parkers)) {
            drop(parker.into_thread());
        }
    }

    fn worker_loop(&self) {
        self.publish_parker();
        #[cfg(target_os = "android")]
        name_worker();
        let vm = self.jvm.load(Ordering::Acquire);
        if vm.is_null() {
            return;
        }
        let Some(env) = (unsafe { jni::attach_current(vm) }) else {
            oboe::log_error_lit(TAG_CALLBACK, c"Failed to attach Kotlin audio worker");
            return;
        };
        let frames = self.frames_per_burst.load(Ordering::Acquire);
        let channels = self.channel_count.load(Ordering::Acquire);
        let Some(total) = frames.checked_mul(channels).filter(|count| *count > 0) else {
            unsafe { jni::detach(vm) };
            return;
        };
        let local = unsafe { jni::new_float_array(env, total) };
        if local.is_null() {
            oboe::log_error_lit(TAG_CALLBACK, c"Failed to allocate reusable JNI float array");
            unsafe { jni::detach(vm) };
            return;
        }
        let global = unsafe { jni::new_global_ref(env, local) };
        unsafe { jni::delete_local_ref(env, local) };
        if global.is_null() {
            unsafe { jni::detach(vm) };
            return;
        }
        let mut scratch = vec![0.0f32; total as usize];
        let mut last_reported = 0i32;
        let callback = self.callback.load(Ordering::Acquire);
        let ready = self.on_ready.load(Ordering::Acquire);
        let xrun_method = self.on_xrun.load(Ordering::Acquire);
        while self.running.load(Ordering::Acquire) {
            let xruns = self.xruns.load(Ordering::Acquire);
            if xruns != last_reported && !xrun_method.is_null() {
                last_reported = xruns;
                let args = [jni::j_int(xruns)];
                unsafe { jni::call_void_method_a(env, callback, xrun_method, args.as_ptr()) };
                if unsafe { jni::exception_check(env) } != 0 {
                    unsafe {
                        jni::exception_describe(env);
                        jni::exception_clear(env);
                    }
                    oboe::log_error_lit(TAG_CALLBACK, c"Exception in notifyXrun callback");
                }
            }
            if !self.has_work(total) {
                self.wait_for_work(total);
                continue;
            }
            let mut call = WorkerCall {
                env,
                callback,
                ready,
                buffer: global,
                scratch: &mut scratch,
                frames,
                total,
            };
            if self.direction.load(Ordering::Acquire) == 0 {
                self.worker_output(&mut call);
            } else {
                self.worker_input(&mut call);
            }
        }
        unsafe {
            jni::delete_global_ref(env, global);
            jni::detach(vm);
        }
    }

    fn publish_parker(&self) {
        let parker = Box::into_raw(Box::new(thread::current()));
        let previous = self.parker.swap(parker, Ordering::Release);
        if !previous.is_null() {
            lock(&self.retired_parkers).push(StableParker(unsafe { Box::from_raw(previous) }));
        }
    }

    fn has_work(&self, needed: i32) -> bool {
        let fifo = self.fifo.load(Ordering::Acquire);
        if fifo.is_null() || needed <= 0 {
            return false;
        }
        if self.direction.load(Ordering::Acquire) == 0 {
            dsp::fifo_available_to_write(fifo) >= needed
        } else {
            dsp::fifo_available_to_read(fifo) >= needed
        }
    }

    fn wait_for_work(&self, needed: i32) {
        if !self.running.load(Ordering::Acquire) || self.has_work(needed) {
            return;
        }
        thread::park_timeout(Duration::from_millis(2));
    }

    fn worker_output(&self, call: &mut WorkerCall<'_>) {
        let args = [jni::j_obj(call.buffer), jni::j_int(call.frames)];
        let frames_processed =
            unsafe { jni::call_int_method_a(call.env, call.callback, call.ready, args.as_ptr()) };
        if unsafe { jni::exception_check(call.env) } != 0 {
            unsafe {
                jni::exception_describe(call.env);
                jni::exception_clear(call.env);
            }
            oboe::log_error_lit(TAG_CALLBACK, c"Exception in onAudioReady callback");
            return;
        }
        // `KlarinetCallback.cpp` continues without waiting when the callback
        // reports no frames. That busy loop is the existing contract.
        if frames_processed <= 0 {
            return;
        }
        let clamped = frames_processed.min(call.frames);
        let Some(samples) = clamped.checked_mul(self.channel_count.load(Ordering::Acquire)) else {
            return;
        };
        if samples <= 0 || samples > call.total {
            return;
        }
        unsafe {
            jni::get_float_array_region(
                call.env,
                call.buffer,
                0,
                samples,
                call.scratch.as_mut_ptr(),
            )
        };
        let fifo = self.fifo.load(Ordering::Acquire);
        if fifo.is_null() {
            return;
        }
        let mut written = 0i32;
        while written < samples && self.running.load(Ordering::Acquire) {
            let n = dsp::fifo_write(
                fifo,
                unsafe { call.scratch.as_ptr().add(written as usize) },
                samples - written,
            );
            if n == 0 {
                self.wait_for_work(call.total);
            } else {
                written += n;
            }
        }
    }

    fn worker_input(&self, call: &mut WorkerCall<'_>) {
        let fifo = self.fifo.load(Ordering::Acquire);
        if fifo.is_null() {
            return;
        }
        let got = dsp::fifo_read(fifo, call.scratch.as_mut_ptr(), call.total);
        if got < call.total {
            self.wait_for_work(call.total);
            return;
        }
        unsafe {
            jni::set_float_array_region(call.env, call.buffer, 0, call.total, call.scratch.as_ptr())
        };
        let args = [jni::j_obj(call.buffer), jni::j_int(call.frames)];
        unsafe { jni::call_int_method_a(call.env, call.callback, call.ready, args.as_ptr()) };
        if unsafe { jni::exception_check(call.env) } != 0 {
            unsafe {
                jni::exception_describe(call.env);
                jni::exception_clear(call.env);
            }
            oboe::log_error_lit(TAG_CALLBACK, c"Exception in onAudioReady callback");
        }
    }
}

impl Drop for AudioState {
    fn drop(&mut self) {
        self.shutdown();
    }
}

#[cfg(target_os = "android")]
fn name_worker() {
    unsafe extern "C" {
        fn pthread_self() -> usize;
        fn pthread_setname_np(thread: usize, name: *const std::os::raw::c_char) -> i32;
    }
    unsafe {
        let _ = pthread_setname_np(pthread_self(), c"klarinet-jni".as_ptr());
    }
}

unsafe fn ensure_route(env: *mut jni::JniEnv) {
    if env.is_null() {
        return;
    }
    ROUTE_ONCE.call_once(|| {
        let local = jni::find_class(env, ROUTE_CLASS.as_ptr());
        if local.is_null() {
            jni::exception_clear(env);
            oboe::log_error_lit(TAG_CALLBACK, c"Could not find AndroidRouteEvents");
            return;
        }
        let global = jni::new_global_ref(env, local);
        let method =
            jni::get_static_method_id(env, local, ROUTE_METHOD.as_ptr(), ROUTE_SIG.as_ptr());
        if method.is_null() {
            jni::exception_clear(env);
            oboe::log_error_lit(TAG_CALLBACK, c"Could not find AndroidRouteEvents.dispatch");
        }
        jni::delete_local_ref(env, local);
        if !global.is_null() {
            ROUTE_CLASS_REF.store(global, Ordering::Release);
        }
        if !method.is_null() {
            ROUTE_METHOD_ID.store(method, Ordering::Release);
        }
    });
}

fn dispatch_route(env: *mut jni::JniEnv, previous: &[i32], current: &[i32]) {
    let class = ROUTE_CLASS_REF.load(Ordering::Acquire);
    let method = ROUTE_METHOD_ID.load(Ordering::Acquire);
    if class.is_null() || method.is_null() {
        return;
    }
    let previous_array = new_id_array(env, previous);
    let current_array = new_id_array(env, current);
    if !previous_array.is_null() && !current_array.is_null() {
        let args = [jni::j_obj(previous_array), jni::j_obj(current_array)];
        unsafe { jni::call_static_void_method_a(env, class, method, args.as_ptr()) };
        if unsafe { jni::exception_check(env) } != 0 {
            unsafe {
                jni::exception_describe(env);
                jni::exception_clear(env);
            }
            oboe::log_error_lit(TAG_CALLBACK, c"Exception in routing changed dispatch");
        }
    }
    if !previous_array.is_null() {
        unsafe { jni::delete_local_ref(env, previous_array) };
    }
    if !current_array.is_null() {
        unsafe { jni::delete_local_ref(env, current_array) };
    }
}

fn new_id_array(env: *mut jni::JniEnv, ids: &[i32]) -> JniObject {
    let Ok(count) = i32::try_from(ids.len()) else {
        return ptr::null_mut();
    };
    let array = unsafe { jni::new_int_array(env, count) };
    if array.is_null() || ids.is_empty() {
        return array;
    }
    unsafe { jni::set_int_array_region(env, array, 0, count, ids.as_ptr()) };
    array
}

fn copy_ids(device_ids: *const i32, num_devices: i32) -> Vec<i32> {
    if device_ids.is_null() || num_devices <= 0 {
        Vec::new()
    } else {
        unsafe { std::slice::from_raw_parts(device_ids, num_devices as usize) }.to_vec()
    }
}

struct WorkerCall<'a> {
    env: *mut jni::JniEnv,
    callback: JniObject,
    ready: JniObject,
    buffer: JniObject,
    scratch: &'a mut [f32],
    frames: i32,
    total: i32,
}

#[cfg(test)]
pub fn silent_for_test() -> Box<AudioState> {
    Box::new(AudioState::empty())
}

pub extern "C" fn on_audio_thunk(
    user: *mut c_void,
    is_input: i32,
    samples: *mut f32,
    num_frames: i32,
    channel_count: i32,
) {
    if user.is_null() {
        return;
    }
    unsafe { &*(user as *const AudioState) }.on_audio(is_input, samples, num_frames, channel_count);
}

pub extern "C" fn on_route_thunk(user: *mut c_void, device_ids: *const i32, num_devices: i32) {
    if user.is_null() {
        return;
    }
    unsafe { &*(user as *const AudioState) }.on_route(device_ids, num_devices);
}

#[cfg(test)]
fn test_state(has_callback: bool, has_chain: bool) -> AudioState {
    let state = AudioState::empty();
    if has_callback {
        state
            .callback
            .store(NonNull::<c_void>::dangling().as_ptr(), Ordering::Release);
    }
    if has_chain {
        state
            .chain
            .store(NonNull::<c_void>::dangling().as_ptr(), Ordering::Release);
    }
    state
}

#[cfg(test)]
fn write_fifo(state: &AudioState, samples: &[f32]) {
    let fifo = state.fifo.load(Ordering::Acquire);
    dsp::fifo_write(fifo, samples.as_ptr(), samples.len() as i32);
}

#[cfg(test)]
fn read_fifo(state: &AudioState, count: i32) -> Vec<f32> {
    let mut out = vec![0.0f32; count as usize];
    let fifo = state.fifo.load(Ordering::Acquire);
    dsp::fifo_read(fifo, out.as_mut_ptr(), count);
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    fn begin() {
        dsp::reset_chain_calls();
        dsp::set_chain_mark(false);
    }

    #[test]
    fn sample_counts_reject_non_positive_and_overflow() {
        assert_eq!(playback_samples(i32::MAX, i32::MAX), None);
        assert_eq!(playback_samples(0, 2), None);
        assert_eq!(playback_samples(-1, 2), None);
        assert_eq!(playback_samples(4, 2), Some(8));
        assert_eq!(samples_to_write(i32::MAX, 2), None);
        assert_eq!(samples_to_write(0, 1), None);
        assert_eq!(samples_to_write(3, -1), None);
        assert_eq!(samples_to_write(3, 2), Some(6));
    }

    #[test]
    fn playback_short_read_zeros_the_tail_and_counts_an_xrun() {
        begin();
        dsp::set_chain_mark(true);
        let state = test_state(true, true);
        state.prepare(4, 1, 0);
        let mut samples = [5.0f32; 4];
        state.on_audio(0, samples.as_mut_ptr(), 4, 1);
        assert_eq!(samples, [9.0, 0.0, 0.0, 0.0]);
        assert_eq!(state.xrun_count(), 1);
        assert_eq!(dsp::chain_calls(), 1);
    }

    #[test]
    fn playback_full_read_copies_samples_before_the_chain() {
        begin();
        dsp::set_chain_mark(true);
        let state = test_state(true, true);
        state.prepare(4, 1, 0);
        write_fifo(&state, &[1.0, 2.0, 3.0, 4.0]);
        let mut samples = [0.0f32; 4];
        state.on_audio(0, samples.as_mut_ptr(), 4, 1);
        assert_eq!(samples, [9.0, 2.0, 3.0, 4.0]);
        assert_eq!(state.xrun_count(), 0);
        assert_eq!(dsp::chain_calls(), 1);
    }

    #[test]
    fn capture_short_write_runs_the_chain_before_the_fifo() {
        begin();
        dsp::set_chain_mark(true);
        let state = test_state(true, true);
        state.prepare(1, 1, 1);
        let mut samples = [1.0f32; 16];
        state.on_audio(1, samples.as_mut_ptr(), 16, 1);
        assert_eq!(samples[0], 9.0);
        assert_eq!(read_fifo(&state, 1)[0], 9.0);
        assert_eq!(state.xrun_count(), 1);
        assert_eq!(dsp::chain_calls(), 1);
    }

    #[test]
    fn silence_zeros_the_buffer_and_still_runs_the_chain() {
        begin();
        dsp::set_chain_mark(true);
        let state = test_state(false, true);
        state.prepare(4, 1, 0);
        let mut samples = [3.0f32; 4];
        state.on_audio(0, samples.as_mut_ptr(), 4, 1);
        assert_eq!(samples, [9.0, 0.0, 0.0, 0.0]);
        assert_eq!(state.xrun_count(), 0);
        assert_eq!(dsp::chain_calls(), 1);
    }

    #[test]
    fn overflowing_block_does_not_panic_or_write() {
        begin();
        let state = test_state(true, true);
        state.prepare(4, 1, 0);
        let mut samples = [1.0f32; 4];
        state.on_audio(0, samples.as_mut_ptr(), i32::MAX, i32::MAX);
        assert_eq!(samples, [1.0, 1.0, 1.0, 1.0]);
        assert_eq!(dsp::chain_calls(), 0);
        assert_eq!(state.xrun_count(), 0);
    }

    #[test]
    fn prepare_uses_the_android_burst_default() {
        let state = test_state(false, false);
        state.prepare(0, 0, 0);
        let fifo = state.fifo.load(Ordering::Acquire);
        assert!(!fifo.is_null());
        assert_eq!(dsp::fifo_capacity(fifo), 2048);
        assert_eq!(state.frames_per_burst.load(Ordering::Acquire), 192);
        assert_eq!(state.channel_count.load(Ordering::Acquire), 1);
    }

    #[test]
    fn zero_frames_runs_the_chain_without_touching_samples() {
        begin();
        dsp::set_chain_mark(true);
        let state = test_state(true, true);
        state.prepare(4, 1, 0);
        let mut samples = [4.0f32; 4];
        state.on_audio(0, samples.as_mut_ptr(), 0, 2);
        assert_eq!(samples, [4.0, 4.0, 4.0, 4.0]);
        assert_eq!(dsp::chain_calls(), 1);
    }
}
