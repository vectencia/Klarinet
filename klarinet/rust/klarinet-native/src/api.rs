//! `klarinet_native.h` entry points.
//!
//! Return codes match `klarinet_native.c`: device start and stop, decoder
//! seek, and encoder write return 0 on success and -1 on failure.

use std::os::raw::{c_char, c_void};
use std::ptr;
use std::sync::Mutex;

use crate::audio::{self, NativeDataCb, NativeXrunCb, OpenRequest};
use crate::ffi;

static NAME: Mutex<[u8; 256]> = Mutex::new([0; 256]);
static UNKNOWN: [u8; 8] = *b"Unknown\0";

fn unknown() -> *const c_char {
    UNKNOWN.as_ptr().cast()
}

fn publish_name(bytes: &[u8]) -> *const c_char {
    let mut guard = NAME.lock().unwrap_or_else(|err| err.into_inner());
    let len = bytes.len().min(255);
    guard[..len].copy_from_slice(&bytes[..len]);
    guard[len] = 0;
    guard.as_mut_ptr().cast()
}

fn copied_name(ctx: *mut c_void, index: i32, playback: bool) -> *const c_char {
    if ctx.is_null() {
        return unknown();
    }
    let mut buf = [0u8; 256];
    let rc = unsafe {
        if playback {
            ffi::klarinet_ma_playback_name(ctx, index, buf.as_mut_ptr().cast(), 256)
        } else {
            ffi::klarinet_ma_capture_name(ctx, index, buf.as_mut_ptr().cast(), 256)
        }
    };
    if rc != 0 {
        return unknown();
    }
    let end = buf.iter().position(|byte| *byte == 0).unwrap_or(buf.len());
    publish_name(&buf[..end.min(255)])
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_context_init() -> *mut c_void {
    unsafe { ffi::klarinet_ma_context_init() }
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_context_uninit(ctx: *mut c_void) {
    if ctx.is_null() {
        return;
    }
    unsafe { ffi::klarinet_ma_context_uninit(ctx) };
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_device_init(
    ctx: *mut c_void,
    sample_rate: i32,
    channel_count: i32,
    buffer_frames: i32,
    direction: i32,
    device_id: i32,
    callback: Option<NativeDataCb>,
    user: *mut c_void,
) -> *mut c_void {
    unsafe {
        audio::open_device(OpenRequest {
            context: ctx,
            sample_rate,
            channel_count,
            buffer_frames,
            direction,
            device_id,
            callback,
            user,
        })
        .cast()
    }
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_device_start(dev: *mut c_void) -> i32 {
    unsafe { audio::start_device(dev.cast()) }
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_device_stop(dev: *mut c_void) -> i32 {
    unsafe { audio::stop_device(dev.cast()) }
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_device_uninit(dev: *mut c_void) {
    unsafe { audio::close_device(dev.cast()) };
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_device_get_state(dev: *mut c_void) -> i32 {
    unsafe { audio::device_state(dev.cast()) }
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_device_get_latency_ms(dev: *mut c_void) -> f64 {
    unsafe { audio::device_latency_ms(dev.cast()) }
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_device_set_chain(dev: *mut c_void, chain: *mut c_void) {
    unsafe { audio::set_chain(dev.cast(), chain) };
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_device_set_xrun_notify(
    dev: *mut c_void,
    callback: Option<NativeXrunCb>,
) {
    unsafe { audio::set_xrun(dev.cast(), callback) };
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_get_playback_device_count(ctx: *mut c_void) -> i32 {
    if ctx.is_null() {
        return 0;
    }
    unsafe { ffi::klarinet_ma_playback_count(ctx) }
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_get_capture_device_count(ctx: *mut c_void) -> i32 {
    if ctx.is_null() {
        return 0;
    }
    unsafe { ffi::klarinet_ma_capture_count(ctx) }
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_get_playback_device_name(
    ctx: *mut c_void,
    index: i32,
) -> *const c_char {
    copied_name(ctx, index, true)
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_get_capture_device_name(
    ctx: *mut c_void,
    index: i32,
) -> *const c_char {
    copied_name(ctx, index, false)
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_decoder_init_file(path: *const c_char) -> *mut c_void {
    if path.is_null() {
        return ptr::null_mut();
    }
    unsafe { ffi::klarinet_ma_decoder_open(path) }
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_decoder_get_sample_rate(dec: *mut c_void) -> i32 {
    if dec.is_null() {
        return 0;
    }
    unsafe { ffi::klarinet_ma_decoder_sample_rate(dec) }
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_decoder_get_channels(dec: *mut c_void) -> i32 {
    if dec.is_null() {
        return 0;
    }
    unsafe { ffi::klarinet_ma_decoder_channels(dec) }
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_decoder_get_total_frames(dec: *mut c_void) -> i64 {
    if dec.is_null() {
        return 0;
    }
    unsafe { ffi::klarinet_ma_decoder_total_frames(dec) }
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_decoder_read_frames(
    dec: *mut c_void,
    output: *mut f32,
    frame_count: i32,
) -> i64 {
    if dec.is_null() {
        return 0;
    }
    // A null output is the miniaudio discard: the cursor advances and the
    // frame count comes back. `klarinet_native.c` forwards that pointer.
    unsafe { ffi::klarinet_ma_decoder_read(dec, output, frame_count) }
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_decoder_seek(dec: *mut c_void, frame_index: i64) -> i32 {
    if dec.is_null() {
        return -1;
    }
    if unsafe { ffi::klarinet_ma_decoder_seek(dec, frame_index) } == 1 {
        0
    } else {
        -1
    }
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_decoder_uninit(dec: *mut c_void) {
    if dec.is_null() {
        return;
    }
    unsafe { ffi::klarinet_ma_decoder_close(dec) };
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_encoder_init_file(
    path: *const c_char,
    _format: i32,
    channels: i32,
    sample_rate: i32,
) -> *mut c_void {
    if path.is_null() {
        return ptr::null_mut();
    }
    unsafe { ffi::klarinet_ma_encoder_open(path, channels, sample_rate) }
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_encoder_write_frames(
    enc: *mut c_void,
    data: *const f32,
    frame_count: i32,
) -> i32 {
    if enc.is_null() || data.is_null() {
        return -1;
    }
    if unsafe { ffi::klarinet_ma_encoder_write(enc, data, frame_count) } == 1 {
        0
    } else {
        -1
    }
}

#[no_mangle]
pub unsafe extern "C" fn klarinet_encoder_uninit(enc: *mut c_void) {
    if enc.is_null() {
        return;
    }
    unsafe { ffi::klarinet_ma_encoder_close(enc) };
}

#[cfg(test)]
mod tests {
    use std::ffi::CStr;
    use std::ptr;

    use super::*;

    #[test]
    fn null_handles_match_the_c_sentinels() {
        unsafe {
            assert_eq!(klarinet_device_start(ptr::null_mut()), -1);
            assert_eq!(klarinet_device_stop(ptr::null_mut()), -1);
            assert_eq!(klarinet_device_get_state(ptr::null_mut()), 0);
            assert_eq!(klarinet_device_get_latency_ms(ptr::null_mut()), 0.0);
            assert_eq!(klarinet_get_playback_device_count(ptr::null_mut()), 0);
            assert_eq!(klarinet_get_capture_device_count(ptr::null_mut()), 0);
            assert_eq!(klarinet_decoder_get_sample_rate(ptr::null_mut()), 0);
            assert_eq!(
                klarinet_decoder_read_frames(ptr::null_mut(), ptr::null_mut(), 1),
                0
            );
            assert_eq!(klarinet_decoder_seek(ptr::null_mut(), 0), -1);
            assert_eq!(
                klarinet_encoder_write_frames(ptr::null_mut(), ptr::null(), 1),
                -1
            );
            assert!(klarinet_decoder_init_file(ptr::null()).is_null());
            assert!(klarinet_encoder_init_file(ptr::null(), 0, 1, 48_000).is_null());
            let name = klarinet_get_capture_device_name(ptr::null_mut(), 0);
            assert_eq!(CStr::from_ptr(name).to_bytes(), b"Unknown");
        }
    }

    #[test]
    fn file_return_codes_follow_the_native_header() {
        let path = c"tone.wav";
        unsafe {
            let dec = klarinet_decoder_init_file(path.as_ptr());
            assert!(!dec.is_null());
            assert_eq!(klarinet_decoder_get_sample_rate(dec), 48_000);
            assert_eq!(klarinet_decoder_get_channels(dec), 2);
            assert_eq!(klarinet_decoder_get_total_frames(dec), 4);
            let mut samples = [0.0f32; 4];
            assert_eq!(
                klarinet_decoder_read_frames(dec, samples.as_mut_ptr(), 2),
                2
            );
            assert_eq!(klarinet_decoder_seek(dec, 1), 0);
            klarinet_decoder_uninit(dec);

            let enc = klarinet_encoder_init_file(path.as_ptr(), 1, 1, 48_000);
            assert!(!enc.is_null());
            assert_eq!(klarinet_encoder_write_frames(enc, samples.as_ptr(), 2), 0);
            klarinet_encoder_uninit(enc);
        }
    }
}
