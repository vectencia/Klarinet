//! JNI for FFT, metering, and linear resampling.
//!
//! The caller is the file-playback worker or the analyzer worker, not
//! `on_audio`. `klarinet_fft_forward` and `klarinet_resample_linear` do not
//! allocate, lock, or panic.

use std::os::raw::c_void;

use crate::jni::{self, JniEnv, JniObject};
use crate::{i64_to_usize, usize_to_i64};

const PULL: &[u8] = b"pull\0";
const PULL_SIG: &[u8] = b"(I)[F\0";

unsafe extern "C" {
    fn klarinet_fft_create(size: i32) -> *mut c_void;
    fn klarinet_fft_destroy(handle: *mut c_void);
    fn klarinet_fft_forward(handle: *mut c_void, real: *mut f32, imag: *mut f32, length: i32);
    fn klarinet_fft_magnitude(
        handle: *const c_void,
        real: *const f32,
        imag: *const f32,
        out: *mut f32,
        out_len: i32,
    );
    fn klarinet_math_rms(data: *const f32, offset: i32, length: i32) -> f32;
    fn klarinet_math_peak(data: *const f32, offset: i32, length: i32) -> f32;
    fn klarinet_math_linear_to_db(linear: f32) -> f32;
    fn klarinet_math_db_to_linear(db: f32) -> f32;
    fn klarinet_math_hann(out: *mut f32, size: i32);
    fn klarinet_analyzer_create(fft_size: i32, sample_rate: i32) -> *mut c_void;
    fn klarinet_analyzer_destroy(handle: *mut c_void);
    fn klarinet_analyzer_analyze(
        handle: *mut c_void,
        buffer: *const f32,
        buffer_samples: i32,
        num_frames: i32,
        channel_count: i32,
        spectrum: *mut f32,
        spectrum_len: i32,
        rms: *mut f32,
        rms_db: *mut f32,
        peak: *mut f32,
        peak_db: *mut f32,
    );
    fn klarinet_analyzer_band_energy(
        spectrum: *const f32,
        spectrum_len: i32,
        resolution: f32,
        low_hz: f32,
        high_hz: f32,
    ) -> f32;
    fn klarinet_resampler_create(source_rate: i32, channels: i32) -> *mut c_void;
    fn klarinet_resampler_destroy(handle: *mut c_void);
    fn klarinet_resampler_has_buffered(handle: *const c_void) -> i32;
    fn klarinet_resample_linear(
        handle: *mut c_void,
        dest_rate: i32,
        dest: *mut f32,
        dest_frames: i32,
        read: Option<unsafe extern "C" fn(*mut c_void, *mut f32, i32) -> i32>,
        user: *mut c_void,
    ) -> i32;
}

fn bits_from(value: *mut c_void) -> i64 {
    usize_to_i64(value as usize)
}

fn ptr_from(value: i64) -> *mut c_void {
    i64_to_usize(value) as *mut c_void
}

struct JniRead {
    env: *mut JniEnv,
    read: JniObject,
    method: JniObject,
    channels: i32,
}

extern "C" fn jni_resampler_read(user: *mut c_void, dst: *mut f32, max_frames: i32) -> i32 {
    if user.is_null() || dst.is_null() || max_frames <= 0 {
        return 0;
    }
    // SAFETY: `user` is the `JniRead` stack value in `nativeResampleLinear`,
    // alive for this synchronous callback.
    let ctx = unsafe { &*(user as *const JniRead) };
    unsafe {
        if jni::exception_check(ctx.env) != 0 {
            return -1;
        }
        let array = jni::call_object_int(ctx.env, ctx.read, ctx.method, max_frames);
        if jni::exception_check(ctx.env) != 0 {
            if !array.is_null() {
                jni::delete_local_ref(ctx.env, array);
            }
            return -1;
        }
        if array.is_null() {
            return 0;
        }
        let len = jni::get_array_length(ctx.env, array);
        let channels = ctx.channels;
        if channels <= 0 || len <= 0 {
            jni::delete_local_ref(ctx.env, array);
            return 0;
        }
        let frames = (len / channels).min(max_frames);
        let samples = frames.saturating_mul(channels);
        if samples > 0 {
            // SAFETY: `dst` covers `max_frames` frames, and `frames <= max_frames`.
            jni::get_float_array_region(ctx.env, array, 0, samples, dst);
        }
        jni::delete_local_ref(ctx.env, array);
        frames
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeFftCreate(
    _env: *mut JniEnv,
    _thiz: JniObject,
    size: i32,
) -> i64 {
    unsafe { bits_from(klarinet_fft_create(size)) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeFftDestroy(
    _env: *mut JniEnv,
    _thiz: JniObject,
    handle: i64,
) {
    unsafe { klarinet_fft_destroy(ptr_from(handle)) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeFftForward(
    env: *mut JniEnv,
    _thiz: JniObject,
    handle: i64,
    real: JniObject,
    imag: JniObject,
    length: i32,
) {
    unsafe {
        if real.is_null() || imag.is_null() || length <= 0 {
            return;
        }
        let real_ptr = jni::get_float_array_elements(env, real);
        let imag_ptr = jni::get_float_array_elements(env, imag);
        if !real_ptr.is_null() && !imag_ptr.is_null() {
            klarinet_fft_forward(ptr_from(handle), real_ptr, imag_ptr, length);
        }
        if !real_ptr.is_null() {
            jni::release_float_array_elements(env, real, real_ptr, 0);
        }
        if !imag_ptr.is_null() {
            jni::release_float_array_elements(env, imag, imag_ptr, 0);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeFftMagnitude(
    env: *mut JniEnv,
    _thiz: JniObject,
    handle: i64,
    real: JniObject,
    imag: JniObject,
    out: JniObject,
    out_len: i32,
) {
    unsafe {
        if real.is_null() || imag.is_null() || out.is_null() || out_len <= 0 {
            return;
        }
        let real_ptr = jni::get_float_array_elements(env, real);
        let imag_ptr = jni::get_float_array_elements(env, imag);
        let out_ptr = jni::get_float_array_elements(env, out);
        if !real_ptr.is_null() && !imag_ptr.is_null() && !out_ptr.is_null() {
            klarinet_fft_magnitude(ptr_from(handle), real_ptr, imag_ptr, out_ptr, out_len);
        }
        if !real_ptr.is_null() {
            jni::release_float_array_elements(env, real, real_ptr, jni::JNI_ABORT);
        }
        if !imag_ptr.is_null() {
            jni::release_float_array_elements(env, imag, imag_ptr, jni::JNI_ABORT);
        }
        if !out_ptr.is_null() {
            jni::release_float_array_elements(env, out, out_ptr, 0);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeMathRms(
    env: *mut JniEnv,
    _thiz: JniObject,
    data: JniObject,
    offset: i32,
    length: i32,
) -> f32 {
    unsafe { math_reduce(env, data, offset, length, klarinet_math_rms) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeMathPeak(
    env: *mut JniEnv,
    _thiz: JniObject,
    data: JniObject,
    offset: i32,
    length: i32,
) -> f32 {
    unsafe { math_reduce(env, data, offset, length, klarinet_math_peak) }
}

unsafe fn math_reduce(
    env: *mut JniEnv,
    data: JniObject,
    offset: i32,
    length: i32,
    reduce: unsafe extern "C" fn(*const f32, i32, i32) -> f32,
) -> f32 {
    if data.is_null() || length <= 0 {
        return 0.0;
    }
    let ptr = jni::get_float_array_elements(env, data);
    if ptr.is_null() {
        return 0.0;
    }
    let value = reduce(ptr, offset, length);
    jni::release_float_array_elements(env, data, ptr, jni::JNI_ABORT);
    value
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeMathLinearToDb(
    _env: *mut JniEnv,
    _thiz: JniObject,
    linear: f32,
) -> f32 {
    unsafe { klarinet_math_linear_to_db(linear) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeMathDbToLinear(
    _env: *mut JniEnv,
    _thiz: JniObject,
    db: f32,
) -> f32 {
    unsafe { klarinet_math_db_to_linear(db) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeMathHann(
    env: *mut JniEnv,
    _thiz: JniObject,
    out: JniObject,
    size: i32,
) {
    unsafe {
        if out.is_null() || size <= 0 {
            return;
        }
        let ptr = jni::get_float_array_elements(env, out);
        if ptr.is_null() {
            return;
        }
        klarinet_math_hann(ptr, size);
        jni::release_float_array_elements(env, out, ptr, 0);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeAnalyzerCreate(
    _env: *mut JniEnv,
    _thiz: JniObject,
    fft_size: i32,
    sample_rate: i32,
) -> i64 {
    unsafe { bits_from(klarinet_analyzer_create(fft_size, sample_rate)) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeAnalyzerDestroy(
    _env: *mut JniEnv,
    _thiz: JniObject,
    handle: i64,
) {
    unsafe { klarinet_analyzer_destroy(ptr_from(handle)) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeAnalyzerAnalyze(
    env: *mut JniEnv,
    _thiz: JniObject,
    handle: i64,
    buffer: JniObject,
    buffer_samples: i32,
    num_frames: i32,
    channel_count: i32,
    spectrum: JniObject,
    spectrum_len: i32,
    levels: JniObject,
) {
    unsafe {
        if spectrum.is_null() || levels.is_null() || spectrum_len <= 0 {
            return;
        }
        let spectrum_ptr = jni::get_float_array_elements(env, spectrum);
        let level_ptr = jni::get_float_array_elements(env, levels);
        if spectrum_ptr.is_null() || level_ptr.is_null() || jni::get_array_length(env, levels) < 4 {
            if !spectrum_ptr.is_null() {
                jni::release_float_array_elements(env, spectrum, spectrum_ptr, jni::JNI_ABORT);
            }
            if !level_ptr.is_null() {
                jni::release_float_array_elements(env, levels, level_ptr, jni::JNI_ABORT);
            }
            return;
        }
        let input = if buffer.is_null() || buffer_samples <= 0 {
            std::ptr::null_mut()
        } else {
            jni::get_float_array_elements(env, buffer)
        };
        let input_samples = if input.is_null() { 0 } else { buffer_samples };
        klarinet_analyzer_analyze(
            ptr_from(handle),
            input,
            input_samples,
            num_frames,
            channel_count,
            spectrum_ptr,
            spectrum_len,
            level_ptr,
            level_ptr.add(1),
            level_ptr.add(2),
            level_ptr.add(3),
        );
        if !input.is_null() {
            jni::release_float_array_elements(env, buffer, input, jni::JNI_ABORT);
        }
        jni::release_float_array_elements(env, spectrum, spectrum_ptr, 0);
        jni::release_float_array_elements(env, levels, level_ptr, 0);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeAnalyzerBandEnergy(
    env: *mut JniEnv,
    _thiz: JniObject,
    spectrum: JniObject,
    spectrum_len: i32,
    resolution: f32,
    low_hz: f32,
    high_hz: f32,
) -> f32 {
    unsafe {
        if spectrum.is_null() || spectrum_len <= 0 {
            return 0.0;
        }
        let ptr = jni::get_float_array_elements(env, spectrum);
        if ptr.is_null() {
            return 0.0;
        }
        let energy = klarinet_analyzer_band_energy(ptr, spectrum_len, resolution, low_hz, high_hz);
        jni::release_float_array_elements(env, spectrum, ptr, jni::JNI_ABORT);
        energy
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeResamplerCreate(
    _env: *mut JniEnv,
    _thiz: JniObject,
    source_rate: i32,
    channels: i32,
) -> i64 {
    unsafe { bits_from(klarinet_resampler_create(source_rate, channels)) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeResamplerDestroy(
    _env: *mut JniEnv,
    _thiz: JniObject,
    handle: i64,
) {
    unsafe { klarinet_resampler_destroy(ptr_from(handle)) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeResamplerHasBuffered(
    _env: *mut JniEnv,
    _thiz: JniObject,
    handle: i64,
) -> i32 {
    unsafe { klarinet_resampler_has_buffered(ptr_from(handle)) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeResampleLinear(
    env: *mut JniEnv,
    _thiz: JniObject,
    handle: i64,
    dest_rate: i32,
    dest: JniObject,
    dest_frames: i32,
    channels: i32,
    read: JniObject,
) -> i32 {
    unsafe {
        if dest.is_null() || read.is_null() || dest_frames <= 0 || channels <= 0 {
            return 0;
        }
        let class_ref = jni::get_object_class(env, read);
        let method = jni::get_method_id(
            env,
            class_ref,
            PULL.as_ptr().cast(),
            PULL_SIG.as_ptr().cast(),
        );
        jni::delete_local_ref(env, class_ref);
        if method.is_null() {
            return 0;
        }
        let dest_ptr = jni::get_float_array_elements(env, dest);
        if dest_ptr.is_null() {
            return 0;
        }
        let ctx = JniRead {
            env,
            read,
            method,
            channels,
        };
        let frames = klarinet_resample_linear(
            ptr_from(handle),
            dest_rate,
            dest_ptr,
            dest_frames,
            Some(jni_resampler_read),
            &ctx as *const JniRead as *mut c_void,
        );
        jni::release_float_array_elements(env, dest, dest_ptr, 0);
        if jni::exception_check(env) != 0 || frames < 0 {
            return 0;
        }
        frames
    }
}
