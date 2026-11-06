//! C ABI of `klarinet_oboe.cpp`. The Oboe 1.11 calls stay in that adapter.
//! Host tests replace the symbols with stubs that do not dereference.

use std::ffi::{CStr, CString};
use std::mem::{offset_of, size_of};
use std::os::raw::{c_char, c_void};

pub type OboeAudioFn = extern "C" fn(*mut c_void, i32, *mut f32, i32, i32);
pub type OboeRouteFn = extern "C" fn(*mut c_void, *const i32, i32);

#[repr(C)]
pub struct OboeOpen {
    pub sample_rate: i32,
    pub channel_count: i32,
    pub audio_format: i32,
    pub buffer_capacity_in_frames: i32,
    pub performance_mode: i32,
    pub sharing_mode: i32,
    pub direction: i32,
    pub device_id: i32,
    pub audio: Option<OboeAudioFn>,
    pub route: Option<OboeRouteFn>,
    pub user: *mut c_void,
}

#[repr(C)]
pub struct OboeInfo {
    pub frames_per_burst: i32,
    pub channel_count: i32,
    pub direction: i32,
    pub sample_rate: i32,
    pub device_count: i32,
    pub device_capacity: i32,
    pub device_ids: *mut i32,
}

const _: () = {
    assert!(offset_of!(OboeOpen, audio) == 32);
    assert!(offset_of!(OboeInfo, device_ids) == 24);
};
#[cfg(target_pointer_width = "64")]
const _: () = {
    assert!(size_of::<OboeOpen>() == 56);
    assert!(size_of::<OboeInfo>() == 32);
};
#[cfg(target_pointer_width = "32")]
const _: () = {
    assert!(size_of::<OboeOpen>() == 44);
    assert!(size_of::<OboeInfo>() == 28);
};

const LOG_INFO: i32 = 4;
const LOG_ERROR: i32 = 6;

#[cfg(not(test))]
unsafe extern "C" {
    fn klarinet_oboe_open(request: *const OboeOpen, info: *mut OboeInfo) -> *mut c_void;
    fn klarinet_oboe_request_start(handle: *mut c_void) -> i32;
    fn klarinet_oboe_request_pause(handle: *mut c_void) -> i32;
    fn klarinet_oboe_request_stop(handle: *mut c_void) -> i32;
    fn klarinet_oboe_stop(handle: *mut c_void) -> i32;
    fn klarinet_oboe_close(handle: *mut c_void);
    fn klarinet_oboe_write(
        handle: *mut c_void,
        data: *const f32,
        num_frames: i32,
        timeout_nanos: i64,
    ) -> i32;
    fn klarinet_oboe_read(
        handle: *mut c_void,
        data: *mut f32,
        num_frames: i32,
        timeout_nanos: i64,
    ) -> i32;
    fn klarinet_oboe_state(handle: *mut c_void) -> i32;
    fn klarinet_oboe_latency_ms(handle: *mut c_void) -> f64;
    fn klarinet_oboe_invalid_handle() -> i32;
    fn klarinet_oboe_result_text(result: i32) -> *const c_char;
    fn klarinet_oboe_log(priority: i32, tag: *const c_char, message: *const c_char);
}

#[cfg(test)]
unsafe fn klarinet_oboe_open(_request: *const OboeOpen, _info: *mut OboeInfo) -> *mut c_void {
    std::ptr::null_mut()
}

#[cfg(test)]
unsafe fn klarinet_oboe_request_start(_handle: *mut c_void) -> i32 {
    klarinet_oboe_invalid_handle()
}

#[cfg(test)]
unsafe fn klarinet_oboe_request_pause(_handle: *mut c_void) -> i32 {
    klarinet_oboe_invalid_handle()
}

#[cfg(test)]
unsafe fn klarinet_oboe_request_stop(_handle: *mut c_void) -> i32 {
    klarinet_oboe_invalid_handle()
}

#[cfg(test)]
unsafe fn klarinet_oboe_stop(_handle: *mut c_void) -> i32 {
    klarinet_oboe_invalid_handle()
}

#[cfg(test)]
unsafe fn klarinet_oboe_close(_handle: *mut c_void) {}

#[cfg(test)]
unsafe fn klarinet_oboe_write(
    _handle: *mut c_void,
    _data: *const f32,
    _num_frames: i32,
    _timeout_nanos: i64,
) -> i32 {
    -1
}

#[cfg(test)]
unsafe fn klarinet_oboe_read(
    _handle: *mut c_void,
    _data: *mut f32,
    _num_frames: i32,
    _timeout_nanos: i64,
) -> i32 {
    -1
}

#[cfg(test)]
unsafe fn klarinet_oboe_state(_handle: *mut c_void) -> i32 {
    0
}

#[cfg(test)]
unsafe fn klarinet_oboe_latency_ms(_handle: *mut c_void) -> f64 {
    0.0
}

#[cfg(test)]
unsafe fn klarinet_oboe_invalid_handle() -> i32 {
    -892
}

#[cfg(test)]
unsafe fn klarinet_oboe_result_text(result: i32) -> *const c_char {
    if result == 0 {
        c"OK".as_ptr()
    } else {
        c"ErrorInvalidHandle".as_ptr()
    }
}

#[cfg(test)]
unsafe fn klarinet_oboe_log(_priority: i32, _tag: *const c_char, _message: *const c_char) {}

pub fn open(request: &OboeOpen, info: &mut OboeInfo) -> *mut c_void {
    unsafe { klarinet_oboe_open(request, info) }
}

pub fn request_start(handle: *mut c_void) -> i32 {
    unsafe { klarinet_oboe_request_start(handle) }
}

pub fn request_pause(handle: *mut c_void) -> i32 {
    unsafe { klarinet_oboe_request_pause(handle) }
}

pub fn request_stop(handle: *mut c_void) -> i32 {
    unsafe { klarinet_oboe_request_stop(handle) }
}

pub fn stop_blocking(handle: *mut c_void) -> i32 {
    unsafe { klarinet_oboe_stop(handle) }
}

pub fn close(handle: *mut c_void) {
    unsafe { klarinet_oboe_close(handle) }
}

pub fn write(handle: *mut c_void, data: *const f32, num_frames: i32, timeout_nanos: i64) -> i32 {
    unsafe { klarinet_oboe_write(handle, data, num_frames, timeout_nanos) }
}

pub fn read(handle: *mut c_void, data: *mut f32, num_frames: i32, timeout_nanos: i64) -> i32 {
    unsafe { klarinet_oboe_read(handle, data, num_frames, timeout_nanos) }
}

pub fn state(handle: *mut c_void) -> i32 {
    unsafe { klarinet_oboe_state(handle) }
}

pub fn latency_ms(handle: *mut c_void) -> f64 {
    unsafe { klarinet_oboe_latency_ms(handle) }
}

pub fn invalid_handle() -> i32 {
    unsafe { klarinet_oboe_invalid_handle() }
}

pub fn result_text(result: i32) -> String {
    let ptr = unsafe { klarinet_oboe_result_text(result) };
    if ptr.is_null() {
        String::new()
    } else {
        unsafe { CStr::from_ptr(ptr) }
            .to_string_lossy()
            .into_owned()
    }
}

pub fn log_info(tag: &str, message: &str) {
    log(LOG_INFO, tag, message);
}

pub fn log_error(tag: &str, message: &str) {
    log(LOG_ERROR, tag, message);
}

pub fn log_info_lit(tag: &CStr, message: &CStr) {
    unsafe { klarinet_oboe_log(LOG_INFO, tag.as_ptr(), message.as_ptr()) }
}

pub fn log_error_lit(tag: &CStr, message: &CStr) {
    unsafe { klarinet_oboe_log(LOG_ERROR, tag.as_ptr(), message.as_ptr()) }
}

fn log(priority: i32, tag: &str, message: &str) {
    let Ok(tag) = CString::new(tag) else {
        return;
    };
    let Ok(message) = CString::new(message) else {
        return;
    };
    unsafe { klarinet_oboe_log(priority, tag.as_ptr(), message.as_ptr()) }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn open_struct_matches_the_c_header() {
        assert_eq!(offset_of!(OboeOpen, audio), 32);
        assert_eq!(offset_of!(OboeInfo, device_ids), 24);
        assert_eq!(
            size_of::<OboeOpen>(),
            if cfg!(target_pointer_width = "64") {
                56
            } else {
                44
            }
        );
        assert_eq!(
            size_of::<OboeInfo>(),
            if cfg!(target_pointer_width = "64") {
                32
            } else {
                28
            }
        );
    }
}
