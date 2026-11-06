//! Miniaudio wrapper and the DSP C ABI.
//!
//! Production calls the C wrapper and `klarinet_dsp.h`. Tests replace both
//! with stubs that do not dereference their pointers.

use std::os::raw::{c_char, c_void};

#[cfg(test)]
use std::cell::Cell;
#[cfg(test)]
use std::ptr::NonNull;

pub type UserAudioFn = unsafe extern "C" fn(*mut c_void, *mut f32, i32, i32) -> i32;
pub type XrunFn = unsafe extern "C" fn(*mut c_void, i32);
pub type DataFn = unsafe extern "C" fn(*mut c_void, i32, *mut f32, *const f32, i32);

#[repr(C)]
pub struct MaDeviceConfig {
    pub sample_rate: i32,
    pub channel_count: i32,
    pub period_frames: i32,
    pub direction: i32,
    pub device_id: i32,
    pub data_callback: Option<DataFn>,
    pub user: *mut c_void,
}

#[cfg(test)]
pub struct OffloadCreate {
    pub burst: i32,
    pub channels: i32,
    pub capture: i32,
}

#[cfg(test)]
thread_local! {
    pub static OFFLOAD_CREATE: Cell<Option<OffloadCreate>> = const { Cell::new(None) };
    pub static OFFLOAD_PROCESS: Cell<i32> = const { Cell::new(0) };
    pub static CHAIN_PROCESS: Cell<i32> = const { Cell::new(0) };
    pub static XRUN_SET: Cell<i32> = const { Cell::new(0) };
    pub static ORDER: Cell<u8> = const { Cell::new(0) };
    pub static OFFLOAD_FIRST: Cell<f32> = const { Cell::new(0.0) };
    pub static CHECK_DEVICE: Cell<i32> = const { Cell::new(0) };
    pub static CHECK_DEVICE_CALLS: Cell<i32> = const { Cell::new(0) };
}

#[cfg(not(test))]
extern "C" {
    pub fn klarinet_ma_context_init() -> *mut c_void;
    pub fn klarinet_ma_context_uninit(context: *mut c_void);
    pub fn klarinet_ma_playback_count(context: *mut c_void) -> i32;
    pub fn klarinet_ma_capture_count(context: *mut c_void) -> i32;
    pub fn klarinet_ma_playback_name(
        context: *mut c_void,
        index: i32,
        out: *mut c_char,
        out_len: i32,
    ) -> i32;
    pub fn klarinet_ma_capture_name(
        context: *mut c_void,
        index: i32,
        out: *mut c_char,
        out_len: i32,
    ) -> i32;
    pub fn klarinet_ma_check_device_id(context: *mut c_void, direction: i32, device_id: i32)
        -> i32;
    pub fn klarinet_ma_device_init(
        context: *mut c_void,
        config: *const MaDeviceConfig,
    ) -> *mut c_void;
    pub fn klarinet_ma_device_start(device: *mut c_void) -> i32;
    pub fn klarinet_ma_device_stop(device: *mut c_void) -> i32;
    pub fn klarinet_ma_device_uninit(device: *mut c_void);
    pub fn klarinet_ma_device_state(device: *mut c_void) -> i32;
    pub fn klarinet_ma_device_latency_ms(device: *mut c_void) -> f64;
    pub fn klarinet_ma_decoder_open(path: *const c_char) -> *mut c_void;
    pub fn klarinet_ma_decoder_sample_rate(decoder: *mut c_void) -> i32;
    pub fn klarinet_ma_decoder_channels(decoder: *mut c_void) -> i32;
    pub fn klarinet_ma_decoder_total_frames(decoder: *mut c_void) -> i64;
    pub fn klarinet_ma_decoder_read(decoder: *mut c_void, dst: *mut f32, frame_count: i32) -> i64;
    pub fn klarinet_ma_decoder_seek(decoder: *mut c_void, frame_index: i64) -> i32;
    pub fn klarinet_ma_decoder_close(decoder: *mut c_void);
    pub fn klarinet_ma_encoder_open(
        path: *const c_char,
        channels: i32,
        sample_rate: i32,
    ) -> *mut c_void;
    pub fn klarinet_ma_encoder_write(
        encoder: *mut c_void,
        data: *const f32,
        frame_count: i32,
    ) -> i32;
    pub fn klarinet_ma_encoder_close(encoder: *mut c_void);

    pub fn klarinet_offload_create(
        frames_per_burst: i32,
        channel_count: i32,
        is_capture: i32,
        callback: UserAudioFn,
        user: *mut c_void,
    ) -> *mut c_void;
    pub fn klarinet_offload_destroy(handle: *mut c_void);
    pub fn klarinet_offload_set_xrun_callback(handle: *mut c_void, callback: XrunFn);
    pub fn klarinet_offload_process(handle: *mut c_void, audio: *mut f32, num_frames: i32);
}

#[cfg(not(test))]
pub use crate::dsp_api::klarinet_chain_process;

#[cfg(test)]
fn dangling() -> *mut c_void {
    NonNull::<c_void>::dangling().as_ptr()
}

#[cfg(test)]
pub unsafe fn klarinet_ma_context_init() -> *mut c_void {
    dangling()
}

#[cfg(test)]
pub unsafe fn klarinet_ma_context_uninit(_context: *mut c_void) {}

#[cfg(test)]
pub unsafe fn klarinet_ma_playback_count(_context: *mut c_void) -> i32 {
    0
}

#[cfg(test)]
pub unsafe fn klarinet_ma_capture_count(_context: *mut c_void) -> i32 {
    0
}

#[cfg(test)]
pub unsafe fn klarinet_ma_playback_name(
    _context: *mut c_void,
    _index: i32,
    _out: *mut c_char,
    _out_len: i32,
) -> i32 {
    -1
}

#[cfg(test)]
pub unsafe fn klarinet_ma_capture_name(
    _context: *mut c_void,
    _index: i32,
    _out: *mut c_char,
    _out_len: i32,
) -> i32 {
    -1
}

#[cfg(test)]
pub unsafe fn klarinet_ma_check_device_id(
    _context: *mut c_void,
    _direction: i32,
    _device_id: i32,
) -> i32 {
    CHECK_DEVICE_CALLS.with(|slot| slot.set(slot.get() + 1));
    CHECK_DEVICE.with(Cell::get)
}

#[cfg(test)]
pub unsafe fn klarinet_ma_device_init(
    _context: *mut c_void,
    _config: *const MaDeviceConfig,
) -> *mut c_void {
    dangling()
}

#[cfg(test)]
pub unsafe fn klarinet_ma_device_start(_device: *mut c_void) -> i32 {
    0
}

#[cfg(test)]
pub unsafe fn klarinet_ma_device_stop(_device: *mut c_void) -> i32 {
    0
}

#[cfg(test)]
pub unsafe fn klarinet_ma_device_uninit(_device: *mut c_void) {}

#[cfg(test)]
pub unsafe fn klarinet_ma_device_state(_device: *mut c_void) -> i32 {
    0
}

#[cfg(test)]
pub unsafe fn klarinet_ma_device_latency_ms(_device: *mut c_void) -> f64 {
    0.0
}

#[cfg(test)]
pub unsafe fn klarinet_ma_decoder_open(_path: *const c_char) -> *mut c_void {
    dangling()
}

#[cfg(test)]
pub unsafe fn klarinet_ma_decoder_sample_rate(_decoder: *mut c_void) -> i32 {
    48_000
}

#[cfg(test)]
pub unsafe fn klarinet_ma_decoder_channels(_decoder: *mut c_void) -> i32 {
    2
}

#[cfg(test)]
pub unsafe fn klarinet_ma_decoder_total_frames(_decoder: *mut c_void) -> i64 {
    4
}

#[cfg(test)]
pub unsafe fn klarinet_ma_decoder_read(
    _decoder: *mut c_void,
    _dst: *mut f32,
    frame_count: i32,
) -> i64 {
    i64::from(frame_count)
}

#[cfg(test)]
pub unsafe fn klarinet_ma_decoder_seek(_decoder: *mut c_void, _frame_index: i64) -> i32 {
    1
}

#[cfg(test)]
pub unsafe fn klarinet_ma_decoder_close(_decoder: *mut c_void) {}

#[cfg(test)]
pub unsafe fn klarinet_ma_encoder_open(
    _path: *const c_char,
    _channels: i32,
    _sample_rate: i32,
) -> *mut c_void {
    dangling()
}

#[cfg(test)]
pub unsafe fn klarinet_ma_encoder_write(
    _encoder: *mut c_void,
    _data: *const f32,
    _frame_count: i32,
) -> i32 {
    1
}

#[cfg(test)]
pub unsafe fn klarinet_ma_encoder_close(_encoder: *mut c_void) {}

#[cfg(test)]
pub unsafe fn klarinet_offload_create(
    frames_per_burst: i32,
    channel_count: i32,
    is_capture: i32,
    _callback: UserAudioFn,
    _user: *mut c_void,
) -> *mut c_void {
    OFFLOAD_CREATE.with(|slot| {
        slot.set(Some(OffloadCreate {
            burst: frames_per_burst,
            channels: channel_count,
            capture: is_capture,
        }));
    });
    dangling()
}

#[cfg(test)]
pub unsafe fn klarinet_offload_destroy(_handle: *mut c_void) {}

#[cfg(test)]
pub unsafe fn klarinet_offload_set_xrun_callback(_handle: *mut c_void, _callback: XrunFn) {
    XRUN_SET.with(|slot| slot.set(slot.get() + 1));
}

#[cfg(test)]
pub unsafe fn klarinet_offload_process(handle: *mut c_void, audio: *mut f32, num_frames: i32) {
    OFFLOAD_PROCESS.with(|slot| slot.set(slot.get() + 1));
    ORDER.with(|slot| {
        if slot.get() == 0 {
            slot.set(1);
        }
    });
    if !handle.is_null() && !audio.is_null() && num_frames > 0 {
        OFFLOAD_FIRST.with(|slot| slot.set(*audio));
        *audio = 1.0;
    }
}

#[cfg(test)]
pub unsafe fn klarinet_chain_process(
    chain: *mut c_void,
    buffer: *mut f32,
    num_frames: i32,
    channel_count: i32,
) {
    CHAIN_PROCESS.with(|slot| slot.set(slot.get() + 1));
    ORDER.with(|slot| {
        if slot.get() == 1 {
            slot.set(2);
        }
    });
    if !chain.is_null() && !buffer.is_null() && num_frames > 0 && channel_count > 0 {
        *buffer = 9.0;
    }
}

#[cfg(test)]
pub fn reset_counters() {
    OFFLOAD_CREATE.with(|slot| slot.set(None));
    OFFLOAD_PROCESS.with(|slot| slot.set(0));
    CHAIN_PROCESS.with(|slot| slot.set(0));
    XRUN_SET.with(|slot| slot.set(0));
    ORDER.with(|slot| slot.set(0));
    OFFLOAD_FIRST.with(|slot| slot.set(0.0));
    CHECK_DEVICE.with(|slot| slot.set(0));
    CHECK_DEVICE_CALLS.with(|slot| slot.set(0));
}
