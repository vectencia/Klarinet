//! Device callback. `on_audio` runs on the miniaudio thread.
//!
//! It must not allocate, lock, panic, or enter Kotlin. Samples move through
//! `klarinet_offload_process`. The user callback runs on the offload worker.

use std::mem::MaybeUninit;
use std::os::raw::c_void;
use std::ptr;
use std::sync::atomic::{AtomicI32, AtomicPtr, AtomicUsize, Ordering};
use std::thread;
use std::time::{Duration, Instant};

use crate::ffi::{self, DataFn, MaDeviceConfig, UserAudioFn, XrunFn};

const CAPTURE_SCRATCH: i32 = 8192;

pub type NativeDataCb = unsafe extern "C" fn(*mut c_void, *mut f32, i32, i32, i32);
pub type NativeXrunCb = unsafe extern "C" fn(*mut c_void, i32);

pub struct OpenRequest {
    pub context: *mut c_void,
    pub sample_rate: i32,
    pub channel_count: i32,
    pub buffer_frames: i32,
    pub direction: i32,
    pub device_id: i32,
    pub callback: Option<NativeDataCb>,
    pub user: *mut c_void,
}

pub struct Device {
    ma: *mut c_void,
    callback: AtomicUsize,
    user: AtomicPtr<c_void>,
    channel_count: AtomicI32,
    is_capture: AtomicI32,
    chain: AtomicPtr<c_void>,
    xrun: AtomicUsize,
    offload: AtomicPtr<c_void>,
}

fn device_ref<'a>(user: *mut c_void) -> Option<&'a Device> {
    if user.is_null() {
        None
    } else {
        Some(unsafe { &*user.cast::<Device>() })
    }
}

fn product(frames: i32, channels: i32) -> Option<i32> {
    if frames < 0 || channels < 0 {
        None
    } else {
        frames.checked_mul(channels)
    }
}

fn playback(
    chain: *mut c_void,
    offload: *mut c_void,
    output: *mut f32,
    frames: i32,
    channels: i32,
) {
    unsafe {
        if offload.is_null() {
            if let Some(total) = product(frames, channels) {
                if total > 0 {
                    ptr::write_bytes(output, 0, total as usize);
                }
            }
        } else {
            ffi::klarinet_offload_process(offload, output, frames);
        }
        if !chain.is_null() {
            ffi::klarinet_chain_process(chain, output, frames, channels);
        }
    }
}

fn capture(
    chain: *mut c_void,
    offload: *mut c_void,
    input: *const f32,
    frames: i32,
    channels: i32,
) {
    if !chain.is_null() {
        if let Some(total) = product(frames, channels) {
            if (0..=CAPTURE_SCRATCH).contains(&total) {
                let mut work = MaybeUninit::<[f32; CAPTURE_SCRATCH as usize]>::uninit();
                unsafe {
                    if total > 0 {
                        ptr::copy_nonoverlapping(
                            input,
                            work.as_mut_ptr().cast::<f32>(),
                            total as usize,
                        );
                    }
                    let samples = work.as_mut_ptr().cast::<f32>();
                    ffi::klarinet_chain_process(chain, samples, frames, channels);
                    if !offload.is_null() {
                        ffi::klarinet_offload_process(offload, samples, frames);
                    }
                }
                return;
            }
        }
    }
    if !offload.is_null() {
        unsafe { ffi::klarinet_offload_process(offload, input as *mut f32, frames) }
    }
}

pub unsafe extern "C" fn on_audio(
    user: *mut c_void,
    is_capture: i32,
    output: *mut f32,
    input: *const f32,
    frames: i32,
) {
    let Some(device) = device_ref(user) else {
        return;
    };
    let channels = device.channel_count.load(Ordering::SeqCst);
    let chain = device.chain.load(Ordering::SeqCst);
    let offload = device.offload.load(Ordering::SeqCst);
    if is_capture != 0 {
        if !input.is_null() {
            capture(chain, offload, input, frames, channels);
        }
    } else if !output.is_null() {
        playback(chain, offload, output, frames, channels);
    }
}

unsafe extern "C" fn user_audio(
    user: *mut c_void,
    buffer: *mut f32,
    frames: i32,
    channels: i32,
) -> i32 {
    let Some(device) = device_ref(user) else {
        return 0;
    };
    let bits = device.callback.load(Ordering::SeqCst);
    if bits == 0 {
        return 0;
    }
    let callback = unsafe { std::mem::transmute::<usize, NativeDataCb>(bits) };
    let data = device.user.load(Ordering::SeqCst);
    let capture_flag = device.is_capture.load(Ordering::SeqCst);
    unsafe { callback(data, buffer, frames, channels, capture_flag) };
    frames
}

unsafe extern "C" fn on_xrun(user: *mut c_void, count: i32) {
    let Some(device) = device_ref(user) else {
        return;
    };
    let bits = device.xrun.load(Ordering::SeqCst);
    if bits == 0 {
        return;
    }
    let callback = unsafe { std::mem::transmute::<usize, NativeXrunCb>(bits) };
    let data = device.user.load(Ordering::SeqCst);
    unsafe { callback(data, count) };
}

fn audio_fn() -> DataFn {
    on_audio
}

fn user_fn() -> UserAudioFn {
    user_audio
}

fn xrun_fn() -> XrunFn {
    on_xrun
}

pub unsafe fn open_device(request: OpenRequest) -> *mut Device {
    if request.context.is_null() {
        return ptr::null_mut();
    }
    // `klarinet_native.c` rejects an unavailable id before it creates the
    // offload, so a failed open cannot run the user callback.
    if request.device_id >= 0 {
        let available = unsafe {
            ffi::klarinet_ma_check_device_id(request.context, request.direction, request.device_id)
        };
        if available != 0 {
            return ptr::null_mut();
        }
    }
    let is_capture = if request.direction == 0 { 0 } else { 1 };
    let device = Box::new(Device {
        ma: ptr::null_mut(),
        callback: AtomicUsize::new(request.callback.map(|cb| cb as usize).unwrap_or(0)),
        user: AtomicPtr::new(request.user),
        channel_count: AtomicI32::new(request.channel_count),
        is_capture: AtomicI32::new(is_capture),
        chain: AtomicPtr::new(ptr::null_mut()),
        xrun: AtomicUsize::new(0),
        offload: AtomicPtr::new(ptr::null_mut()),
    });
    let ptr = Box::into_raw(device);
    if request.callback.is_some() {
        let burst = if request.buffer_frames > 0 {
            request.buffer_frames
        } else {
            256
        };
        let offload = unsafe {
            ffi::klarinet_offload_create(
                burst,
                request.channel_count,
                is_capture,
                user_fn(),
                ptr.cast::<c_void>(),
            )
        };
        unsafe { (*ptr).offload.store(offload, Ordering::SeqCst) };
    }
    let config = MaDeviceConfig {
        sample_rate: request.sample_rate,
        channel_count: request.channel_count,
        period_frames: request.buffer_frames,
        direction: request.direction,
        device_id: request.device_id,
        data_callback: if request.callback.is_some() {
            Some(audio_fn())
        } else {
            None
        },
        user: ptr.cast::<c_void>(),
    };
    let ma = unsafe { ffi::klarinet_ma_device_init(request.context, &config) };
    if ma.is_null() {
        let offload = unsafe { (*ptr).offload.load(Ordering::SeqCst) };
        if !offload.is_null() {
            unsafe { ffi::klarinet_offload_destroy(offload) };
        }
        drop(unsafe { Box::from_raw(ptr) });
        return ptr::null_mut();
    }
    unsafe { (*ptr).ma = ma };
    ptr
}

pub unsafe fn close_device(device: *mut Device) {
    if device.is_null() {
        return;
    }
    let offload = unsafe { (*device).offload.load(Ordering::SeqCst) };
    let ma = unsafe { (*device).ma };
    unsafe { ffi::klarinet_ma_device_uninit(ma) };
    if !offload.is_null() {
        unsafe { ffi::klarinet_offload_destroy(offload) };
    }
    drop(unsafe { Box::from_raw(device) });
}

pub unsafe fn set_chain(device: *mut Device, chain: *mut c_void) {
    if device.is_null() {
        return;
    }
    unsafe { (*device).chain.store(chain, Ordering::SeqCst) };
}

pub unsafe fn set_xrun(device: *mut Device, callback: Option<NativeXrunCb>) {
    if device.is_null() {
        return;
    }
    let bits = callback.map(|cb| cb as usize).unwrap_or(0);
    unsafe { (*device).xrun.store(bits, Ordering::SeqCst) };
    let offload = unsafe { (*device).offload.load(Ordering::SeqCst) };
    if !offload.is_null() {
        unsafe { ffi::klarinet_offload_set_xrun_callback(offload, xrun_fn()) };
    }
}

pub unsafe fn start_device(device: *mut Device) -> i32 {
    if device.is_null() {
        return -1;
    }
    unsafe { ffi::klarinet_ma_device_start((*device).ma) }
}

pub unsafe fn stop_device(device: *mut Device) -> i32 {
    if device.is_null() {
        return -1;
    }
    unsafe { ffi::klarinet_ma_device_stop((*device).ma) }
}

pub unsafe fn device_state(device: *mut Device) -> i32 {
    if device.is_null() {
        return 0;
    }
    unsafe { ffi::klarinet_ma_device_state((*device).ma) }
}

pub unsafe fn device_latency_ms(device: *mut Device) -> f64 {
    if device.is_null() {
        return 0.0;
    }
    unsafe { ffi::klarinet_ma_device_latency_ms((*device).ma) }
}

struct AudioThreadProbe {
    caller: thread::ThreadId,
    audio_hits: AtomicI32,
    worker_hits: AtomicI32,
}

unsafe extern "C" fn audio_thread_probe(
    user: *mut c_void,
    _buffer: *mut f32,
    _frames: i32,
    _channels: i32,
    _capture: i32,
) {
    if user.is_null() {
        return;
    }
    let probe = unsafe { &*user.cast::<AudioThreadProbe>() };
    if thread::current().id() == probe.caller {
        probe.audio_hits.fetch_add(1, Ordering::Release);
    } else {
        probe.worker_hits.fetch_add(1, Ordering::Release);
    }
}

unsafe extern "C" fn fill_ones(
    user: *mut c_void,
    buffer: *mut f32,
    frames: i32,
    channels: i32,
    _capture: i32,
) {
    let Some(total) = product(frames, channels) else {
        return;
    };
    if !buffer.is_null() {
        for index in 0..total {
            unsafe { *buffer.offset(index as isize) = 1.0 };
        }
    }
    if !user.is_null() {
        unsafe { &*user.cast::<AtomicI32>() }.fetch_add(1, Ordering::Release);
    }
}

/// Drives `on_audio` on the calling thread. The worker writes 1.0 into the
/// FIFO. The callback copies that block and then runs the chain, which is
/// the playback body in `klarinet_native.c`.
#[no_mangle]
pub unsafe extern "C" fn klarinet_test_playback_chain(
    chain: *mut c_void,
    buffer: *mut f32,
    frames: i32,
    channels: i32,
) {
    if buffer.is_null() || frames <= 0 || channels <= 0 || frames > 64 || channels > 2 {
        return;
    }
    let fills = Box::new(AtomicI32::new(0));
    let fills_ptr = Box::into_raw(fills);
    let device = Box::new(Device {
        ma: ptr::null_mut(),
        callback: AtomicUsize::new(fill_ones as *const () as usize),
        user: AtomicPtr::new(fills_ptr.cast()),
        channel_count: AtomicI32::new(channels),
        is_capture: AtomicI32::new(0),
        chain: AtomicPtr::new(chain),
        xrun: AtomicUsize::new(0),
        offload: AtomicPtr::new(ptr::null_mut()),
    });
    let device_ptr = Box::into_raw(device);
    let offload =
        unsafe { ffi::klarinet_offload_create(frames, channels, 0, user_fn(), device_ptr.cast()) };
    if offload.is_null() {
        drop(unsafe { Box::from_raw(device_ptr) });
        drop(unsafe { Box::from_raw(fills_ptr) });
        return;
    }
    unsafe { (*device_ptr).offload.store(offload, Ordering::SeqCst) };
    let started = Instant::now();
    // The second fill starts only after the first burst is in the FIFO.
    while unsafe { &*fills_ptr }.load(Ordering::Acquire) < 2 {
        if started.elapsed() > Duration::from_secs(2) {
            unsafe { ffi::klarinet_offload_destroy(offload) };
            drop(unsafe { Box::from_raw(device_ptr) });
            drop(unsafe { Box::from_raw(fills_ptr) });
            return;
        }
        thread::yield_now();
    }
    unsafe { on_audio(device_ptr.cast(), 0, buffer, ptr::null(), frames) };
    unsafe { ffi::klarinet_offload_destroy(offload) };
    unsafe {
        (*device_ptr)
            .offload
            .store(ptr::null_mut(), Ordering::SeqCst)
    };
    drop(unsafe { Box::from_raw(device_ptr) });
    drop(unsafe { Box::from_raw(fills_ptr) });
}

/// Returns 0 when the worker invoked the user callback and `on_audio` did not.
#[no_mangle]
pub unsafe extern "C" fn klarinet_test_offload_user_stays_off_the_audio_thread(
    frames: i32,
    channels: i32,
) -> i32 {
    if frames <= 0 || channels <= 0 || frames > 64 || channels > 2 {
        return -1;
    }
    let probe = Box::new(AudioThreadProbe {
        caller: thread::current().id(),
        audio_hits: AtomicI32::new(0),
        worker_hits: AtomicI32::new(0),
    });
    let probe_ptr = Box::into_raw(probe);
    let device = Box::new(Device {
        ma: ptr::null_mut(),
        callback: AtomicUsize::new(audio_thread_probe as *const () as usize),
        user: AtomicPtr::new(probe_ptr.cast()),
        channel_count: AtomicI32::new(channels),
        is_capture: AtomicI32::new(0),
        chain: AtomicPtr::new(ptr::null_mut()),
        xrun: AtomicUsize::new(0),
        offload: AtomicPtr::new(ptr::null_mut()),
    });
    let device_ptr = Box::into_raw(device);
    let offload =
        unsafe { ffi::klarinet_offload_create(frames, channels, 0, user_fn(), device_ptr.cast()) };
    if offload.is_null() {
        drop(unsafe { Box::from_raw(device_ptr) });
        drop(unsafe { Box::from_raw(probe_ptr) });
        return -1;
    }
    unsafe { (*device_ptr).offload.store(offload, Ordering::SeqCst) };
    let started = Instant::now();
    while unsafe { &*probe_ptr }.worker_hits.load(Ordering::Acquire) == 0 {
        if started.elapsed() > Duration::from_secs(2) {
            unsafe { ffi::klarinet_offload_destroy(offload) };
            drop(unsafe { Box::from_raw(device_ptr) });
            drop(unsafe { Box::from_raw(probe_ptr) });
            return -1;
        }
        thread::yield_now();
    }
    let mut output = [0.0f32; 128];
    unsafe {
        on_audio(
            device_ptr.cast(),
            0,
            output.as_mut_ptr(),
            ptr::null(),
            frames,
        )
    };
    let audio_hits = unsafe { &*probe_ptr }.audio_hits.load(Ordering::Acquire);
    let worker_hits = unsafe { &*probe_ptr }.worker_hits.load(Ordering::Acquire);
    unsafe { ffi::klarinet_offload_destroy(offload) };
    unsafe {
        (*device_ptr)
            .offload
            .store(ptr::null_mut(), Ordering::SeqCst)
    };
    drop(unsafe { Box::from_raw(device_ptr) });
    drop(unsafe { Box::from_raw(probe_ptr) });
    if audio_hits == 0 && worker_hits > 0 {
        0
    } else {
        -1
    }
}

#[cfg(test)]
mod tests {
    use std::cell::Cell;
    use std::ptr::NonNull;
    use std::sync::atomic::Ordering;

    use super::*;
    use crate::ffi;

    thread_local! {
        static USER_CALLS: Cell<i32> = const { Cell::new(0) };
    }

    unsafe extern "C" fn user_cb(
        _user: *mut c_void,
        _buffer: *mut f32,
        _frames: i32,
        _channels: i32,
        _capture: i32,
    ) {
        USER_CALLS.with(|slot| slot.set(slot.get() + 1));
    }

    fn chain() -> *mut c_void {
        NonNull::<c_void>::dangling().as_ptr()
    }

    fn context() -> *mut c_void {
        NonNull::<c_void>::dangling().as_ptr()
    }

    fn request(direction: i32, frames: i32, callback: Option<NativeDataCb>) -> OpenRequest {
        OpenRequest {
            context: context(),
            sample_rate: 48_000,
            channel_count: 1,
            buffer_frames: frames,
            direction,
            device_id: -1,
            callback,
            user: NonNull::<c_void>::dangling().as_ptr(),
        }
    }

    #[test]
    fn playback_runs_offload_then_chain_without_the_user_callback() {
        ffi::reset_counters();
        USER_CALLS.with(|slot| slot.set(0));
        let device = unsafe { open_device(request(0, 128, Some(user_cb))) };
        assert!(!device.is_null());
        unsafe { set_chain(device, chain()) };
        let mut output = [4.0f32, 0.0];
        unsafe { on_audio(device.cast(), 0, output.as_mut_ptr(), ptr::null(), 2) };
        assert_eq!(output[0], 9.0);
        assert_eq!(ffi::OFFLOAD_PROCESS.with(Cell::get), 1);
        assert_eq!(ffi::CHAIN_PROCESS.with(Cell::get), 1);
        assert_eq!(ffi::ORDER.with(Cell::get), 2);
        assert_eq!(USER_CALLS.with(Cell::get), 0);
        let created = ffi::OFFLOAD_CREATE
            .with(|slot| slot.take())
            .expect("offload");
        assert_eq!(created.burst, 128);
        assert_eq!(created.channels, 1);
        assert_eq!(created.capture, 0);
        unsafe { close_device(device) };
    }

    #[test]
    fn playback_without_a_callback_writes_silence_then_the_chain() {
        ffi::reset_counters();
        let device = unsafe { open_device(request(0, 64, None)) };
        unsafe { set_chain(device, chain()) };
        let mut output = [4.0f32, 4.0, 4.0, 4.0];
        unsafe { on_audio(device.cast(), 0, output.as_mut_ptr(), ptr::null(), 4) };
        assert_eq!(output, [9.0, 0.0, 0.0, 0.0]);
        assert_eq!(ffi::OFFLOAD_PROCESS.with(Cell::get), 0);
        assert_eq!(ffi::CHAIN_PROCESS.with(Cell::get), 1);
        assert!(ffi::OFFLOAD_CREATE.with(|slot| slot.take()).is_none());
        unsafe { close_device(device) };
    }

    #[test]
    fn capture_processes_a_stack_copy_before_the_fifo() {
        ffi::reset_counters();
        let device = unsafe { open_device(request(1, 0, Some(user_cb))) };
        unsafe { set_chain(device, chain()) };
        let input = [4.0f32, 5.0];
        unsafe { on_audio(device.cast(), 1, ptr::null_mut(), input.as_ptr(), 2) };
        assert_eq!(input, [4.0, 5.0]);
        assert_eq!(ffi::OFFLOAD_FIRST.with(Cell::get), 9.0);
        assert_eq!(ffi::CHAIN_PROCESS.with(Cell::get), 1);
        assert_eq!(USER_CALLS.with(Cell::get), 0);
        let created = ffi::OFFLOAD_CREATE
            .with(|slot| slot.take())
            .expect("offload");
        assert_eq!(created.burst, 256);
        assert_eq!(created.channels, 1);
        assert_eq!(created.capture, 1);
        unsafe {
            let mut one = [0.0f32];
            assert_eq!(user_audio(device.cast(), one.as_mut_ptr(), 1, 1), 1);
        }
        assert_eq!(USER_CALLS.with(Cell::get), 1);
        unsafe { close_device(device) };
    }

    #[test]
    fn capture_above_the_scratch_skips_the_chain() {
        ffi::reset_counters();
        let device = unsafe { open_device(request(1, 32, Some(user_cb))) };
        unsafe { set_chain(device, chain()) };
        let input = [4.0f32];
        unsafe { on_audio(device.cast(), 1, ptr::null_mut(), input.as_ptr(), 8193) };
        assert_eq!(ffi::CHAIN_PROCESS.with(Cell::get), 0);
        assert_eq!(ffi::OFFLOAD_FIRST.with(Cell::get), 4.0);
        unsafe { close_device(device) };
    }

    #[test]
    fn null_context_does_not_open() {
        ffi::reset_counters();
        let mut request = request(0, 16, Some(user_cb));
        request.context = ptr::null_mut();
        let device = unsafe { open_device(request) };
        assert!(device.is_null());
        assert!(ffi::OFFLOAD_CREATE.with(|slot| slot.take()).is_none());
        assert_eq!(ffi::CHECK_DEVICE_CALLS.with(Cell::get), 0);
    }

    #[test]
    fn rejected_device_id_does_not_start_offload() {
        ffi::reset_counters();
        ffi::CHECK_DEVICE.with(|slot| slot.set(-1));
        let mut open = request(0, 32, Some(user_cb));
        open.device_id = 3;
        let device = unsafe { open_device(open) };
        assert!(device.is_null());
        assert!(ffi::OFFLOAD_CREATE.with(|slot| slot.take()).is_none());
        assert_eq!(ffi::CHECK_DEVICE_CALLS.with(Cell::get), 1);
    }

    #[test]
    fn xrun_registration_follows_the_offload_handle() {
        ffi::reset_counters();
        let device = unsafe { open_device(request(0, 16, Some(user_cb))) };
        unsafe extern "C" fn xrun(_user: *mut c_void, _count: i32) {}
        unsafe { set_xrun(device, Some(xrun)) };
        assert_eq!(ffi::XRUN_SET.with(Cell::get), 1);
        assert_ne!(unsafe { (*device).xrun.load(Ordering::SeqCst) }, 0);
        unsafe { close_device(device) };
    }
}
