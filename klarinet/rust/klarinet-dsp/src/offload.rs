//! Worker plus an 8-burst FIFO.
//!
//! `klarinet_offload_process` is the audio-thread entry. The user callback and
//! the xrun callback run on the worker only. The worker allocates its scratch
//! buffer once, before it publishes itself as ready.

use crate::fifo::AudioFifo;
use std::ptr;
use std::sync::atomic::{AtomicBool, AtomicI32, AtomicUsize, Ordering};
use std::thread::{self, JoinHandle, Thread};
use std::time::Duration;

type UserCb = unsafe extern "C" fn(*mut std::ffi::c_void, *mut f32, i32, i32) -> i32;
type XrunCb = unsafe extern "C" fn(*mut std::ffi::c_void, i32);

struct Offload {
    fifo: AudioFifo,
    frames_per_burst: i32,
    channel_count: i32,
    is_capture: bool,
    callback: UserCb,
    user_data: *mut std::ffi::c_void,
    xrun_cb: AtomicUsize,
    xrun_count: AtomicI32,
    last_reported: AtomicI32,
    running: AtomicBool,
    ready: AtomicBool,
    worker_thread: std::cell::UnsafeCell<Option<Thread>>,
    join: std::cell::UnsafeCell<Option<JoinHandle<()>>>,
}

// Safety: the audio thread calls `process` only. The worker calls `worker_loop`
// only. `join` is taken from `klarinet_offload_destroy` after the worker has
// been asked to stop. `worker_thread` is written once before `ready` and is
// only read after that release/acquire pair.
unsafe impl Send for Offload {}
unsafe impl Sync for Offload {}

struct OffloadPtr(*mut Offload);
unsafe impl Send for OffloadPtr {}

impl Offload {
    fn burst_samples(&self) -> i32 {
        self.frames_per_burst * self.channel_count
    }

    fn has_work(&self) -> bool {
        let needed = self.burst_samples();
        if self.is_capture {
            self.fifo.available_to_read() >= needed
        } else {
            self.fifo.available_to_write() >= needed
        }
    }

    fn report_xruns(&self) {
        let bits = self.xrun_cb.load(Ordering::Acquire);
        if bits == 0 {
            return;
        }
        let count = self.xrun_count.load(Ordering::Acquire);
        if count != self.last_reported.load(Ordering::Relaxed) {
            self.last_reported.store(count, Ordering::Relaxed);
            let cb: XrunCb = unsafe { std::mem::transmute(bits) };
            unsafe { cb(self.user_data, count) };
        }
    }

    fn wake(&self) {
        if !self.ready.load(Ordering::Acquire) {
            return;
        }
        if let Some(worker) = unsafe { &*self.worker_thread.get() } {
            worker.unpark();
        }
    }
}

fn worker_loop(shared: OffloadPtr) {
    let o = unsafe { &*shared.0 };
    let needed = o.burst_samples();
    let mut scratch = vec![0.0f32; needed as usize];
    unsafe {
        *o.worker_thread.get() = Some(thread::current());
    }
    o.ready.store(true, Ordering::Release);

    while o.running.load(Ordering::Acquire) {
        o.report_xruns();
        if !o.has_work() {
            thread::park_timeout(Duration::from_millis(2));
            continue;
        }

        if o.is_capture {
            if o.fifo.read(scratch.as_mut_ptr(), needed) < needed {
                thread::park_timeout(Duration::from_millis(2));
                continue;
            }
            unsafe {
                (o.callback)(
                    o.user_data,
                    scratch.as_mut_ptr(),
                    o.frames_per_burst,
                    o.channel_count,
                );
            }
        } else {
            let frames = unsafe {
                (o.callback)(
                    o.user_data,
                    scratch.as_mut_ptr(),
                    o.frames_per_burst,
                    o.channel_count,
                )
            };
            if frames <= 0 {
                continue;
            }
            let frames = frames.min(o.frames_per_burst);
            let samples = frames * o.channel_count;
            let mut written = 0;
            while written < samples && o.running.load(Ordering::Acquire) {
                let n = o.fifo.write(
                    unsafe { scratch.as_ptr().add(written as usize) },
                    samples - written,
                );
                if n == 0 {
                    thread::park_timeout(Duration::from_millis(2));
                } else {
                    written += n;
                }
            }
        }
    }
}

#[no_mangle]
pub extern "C" fn klarinet_offload_create(
    mut frames_per_burst: i32,
    mut channel_count: i32,
    is_capture: i32,
    callback: Option<UserCb>,
    user_data: *mut std::ffi::c_void,
) -> *mut std::ffi::c_void {
    if frames_per_burst <= 0 {
        frames_per_burst = 256;
    }
    if channel_count <= 0 {
        channel_count = 1;
    }
    let Some(callback) = callback else {
        return ptr::null_mut();
    };
    let Some(needed) = frames_per_burst.checked_mul(channel_count) else {
        return ptr::null_mut();
    };
    let Some(capacity) = needed.checked_mul(8) else {
        return ptr::null_mut();
    };
    let Some(fifo) = AudioFifo::new(capacity) else {
        return ptr::null_mut();
    };

    let offload = Box::new(Offload {
        fifo,
        frames_per_burst,
        channel_count,
        is_capture: is_capture != 0,
        callback,
        user_data,
        xrun_cb: AtomicUsize::new(0),
        xrun_count: AtomicI32::new(0),
        last_reported: AtomicI32::new(0),
        running: AtomicBool::new(true),
        ready: AtomicBool::new(false),
        worker_thread: std::cell::UnsafeCell::new(None),
        join: std::cell::UnsafeCell::new(None),
    });
    let ptr = Box::into_raw(offload);
    let shared = OffloadPtr(ptr);
    let spawned = thread::Builder::new().spawn(move || worker_loop(shared));
    let handle = match spawned {
        Ok(handle) => handle,
        Err(_) => {
            unsafe { drop(Box::from_raw(ptr)) };
            return ptr::null_mut();
        }
    };
    unsafe { *(&*ptr).join.get() = Some(handle) };
    while !unsafe { &*ptr }.ready.load(Ordering::Acquire) {
        thread::yield_now();
    }
    ptr.cast()
}

#[no_mangle]
pub extern "C" fn klarinet_offload_destroy(handle: *mut std::ffi::c_void) {
    if handle.is_null() {
        return;
    }
    let o = unsafe { &*handle.cast::<Offload>() };
    o.running.store(false, Ordering::Release);
    o.wake();
    if let Some(worker) = unsafe { (*o.join.get()).take() } {
        let _ = worker.join();
    }
    unsafe { drop(Box::from_raw(handle.cast::<Offload>())) };
}

#[no_mangle]
pub extern "C" fn klarinet_offload_set_xrun_callback(
    handle: *mut std::ffi::c_void,
    cb: Option<XrunCb>,
) {
    if handle.is_null() {
        return;
    }
    let bits = cb.map(|f| f as usize).unwrap_or(0);
    unsafe { &*handle.cast::<Offload>() }
        .xrun_cb
        .store(bits, Ordering::Release);
}

pub(crate) fn keep_exports() {
    let exports: [*const (); 4] = [
        klarinet_offload_create as *const (),
        klarinet_offload_destroy as *const (),
        klarinet_offload_set_xrun_callback as *const (),
        klarinet_offload_process as *const (),
    ];
    let _ = std::hint::black_box(exports);
}

/// Audio thread. Moves samples through the FIFO and counts xruns.
/// Does not call `callback` or the xrun callback.
#[no_mangle]
pub extern "C" fn klarinet_offload_process(
    handle: *mut std::ffi::c_void,
    audio: *mut f32,
    num_frames: i32,
) {
    if handle.is_null() || audio.is_null() || num_frames <= 0 {
        return;
    }
    let o = unsafe { &*handle.cast::<Offload>() };
    let Some(total) = num_frames.checked_mul(o.channel_count) else {
        return;
    };
    if total <= 0 {
        return;
    }
    if o.is_capture {
        let written = o.fifo.write(audio, total);
        if written < total {
            o.xrun_count.fetch_add(1, Ordering::Release);
        }
    } else {
        let got = o.fifo.read(audio, total);
        if got < total {
            unsafe {
                ptr::write_bytes(audio.add(got as usize), 0, (total - got) as usize);
            }
            o.xrun_count.fetch_add(1, Ordering::Release);
        }
    }
    o.wake();
}
