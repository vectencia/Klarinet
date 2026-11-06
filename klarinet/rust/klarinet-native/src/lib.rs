//! Native-desktop ABI for `linuxX64`.
//!
//! `klarinet_native.h` is implemented here. Vendored miniaudio stays in the C
//! wrapper. `linuxArm64` and `mingwX64` keep compiling `klarinet_native.c`.

mod api;
mod audio;
mod dsp_api;
mod ffi;

#[cfg(test)]
mod tests {
    use std::os::raw::c_char;

    use super::api::klarinet_get_playback_device_name;

    #[test]
    fn null_context_name_is_unknown() {
        let name = unsafe { klarinet_get_playback_device_name(std::ptr::null_mut(), 0) };
        assert!(!name.is_null());
        let text = unsafe { std::ffi::CStr::from_ptr(name.cast::<c_char>()) };
        assert_eq!(text.to_bytes(), b"Unknown");
    }
}
