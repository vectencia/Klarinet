//! JNI for FFT, metering, and linear resampling.
//!
//! The caller is the file-playback worker or the analyzer worker.
//! `klarinet_fft_forward` and `klarinet_resample_linear` do not allocate.

use std::os::raw::c_void;

use crate::ffi;
use crate::jni::{self, JniEnv, JniObject};

const PULL: &[u8] = b"pull\0";
const PULL_SIG: &[u8] = b"(I)[F\0";

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
            jni::get_float_region(ctx.env, array, 0, samples, dst);
        }
        jni::delete_local_ref(ctx.env, array);
        frames
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeFftCreate(
    _env: *mut JniEnv,
    _class: JniObject,
    size: i32,
) -> i64 {
    unsafe { ffi::bits_from(ffi::klarinet_fft_create(size)) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeFftDestroy(
    _env: *mut JniEnv,
    _class: JniObject,
    handle: i64,
) {
    unsafe { ffi::klarinet_fft_destroy(ffi::ptr_from(handle)) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeFftForward(
    env: *mut JniEnv,
    _class: JniObject,
    handle: i64,
    real: JniObject,
    imag: JniObject,
    length: i32,
) {
    unsafe {
        if real.is_null() || imag.is_null() || length <= 0 {
            return;
        }
        let real_ptr = jni::get_float_elements(env, real);
        let imag_ptr = jni::get_float_elements(env, imag);
        if !real_ptr.is_null() && !imag_ptr.is_null() {
            ffi::klarinet_fft_forward(ffi::ptr_from(handle), real_ptr, imag_ptr, length);
        }
        if !real_ptr.is_null() {
            jni::release_float_elements(env, real, real_ptr, 0);
        }
        if !imag_ptr.is_null() {
            jni::release_float_elements(env, imag, imag_ptr, 0);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeFftMagnitude(
    env: *mut JniEnv,
    _class: JniObject,
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
        let real_ptr = jni::get_float_elements(env, real);
        let imag_ptr = jni::get_float_elements(env, imag);
        let out_ptr = jni::get_float_elements(env, out);
        if !real_ptr.is_null() && !imag_ptr.is_null() && !out_ptr.is_null() {
            ffi::klarinet_fft_magnitude(
                ffi::ptr_from(handle),
                real_ptr,
                imag_ptr,
                out_ptr,
                out_len,
            );
        }
        if !real_ptr.is_null() {
            jni::release_float_elements(env, real, real_ptr, jni::JNI_ABORT);
        }
        if !imag_ptr.is_null() {
            jni::release_float_elements(env, imag, imag_ptr, jni::JNI_ABORT);
        }
        if !out_ptr.is_null() {
            jni::release_float_elements(env, out, out_ptr, 0);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeMathRms(
    env: *mut JniEnv,
    _class: JniObject,
    data: JniObject,
    offset: i32,
    length: i32,
) -> f32 {
    unsafe { math_reduce(env, data, offset, length, ffi::klarinet_math_rms) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeMathPeak(
    env: *mut JniEnv,
    _class: JniObject,
    data: JniObject,
    offset: i32,
    length: i32,
) -> f32 {
    unsafe { math_reduce(env, data, offset, length, ffi::klarinet_math_peak) }
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
    let ptr = jni::get_float_elements(env, data);
    if ptr.is_null() {
        return 0.0;
    }
    let value = reduce(ptr, offset, length);
    jni::release_float_elements(env, data, ptr, jni::JNI_ABORT);
    value
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeMathLinearToDb(
    _env: *mut JniEnv,
    _class: JniObject,
    linear: f32,
) -> f32 {
    unsafe { ffi::klarinet_math_linear_to_db(linear) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeMathDbToLinear(
    _env: *mut JniEnv,
    _class: JniObject,
    db: f32,
) -> f32 {
    unsafe { ffi::klarinet_math_db_to_linear(db) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeMathHann(
    env: *mut JniEnv,
    _class: JniObject,
    out: JniObject,
    size: i32,
) {
    unsafe {
        if out.is_null() || size <= 0 {
            return;
        }
        let ptr = jni::get_float_elements(env, out);
        if ptr.is_null() {
            return;
        }
        ffi::klarinet_math_hann(ptr, size);
        jni::release_float_elements(env, out, ptr, 0);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeAnalyzerCreate(
    _env: *mut JniEnv,
    _class: JniObject,
    fft_size: i32,
    sample_rate: i32,
) -> i64 {
    unsafe { ffi::bits_from(ffi::klarinet_analyzer_create(fft_size, sample_rate)) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeAnalyzerDestroy(
    _env: *mut JniEnv,
    _class: JniObject,
    handle: i64,
) {
    unsafe { ffi::klarinet_analyzer_destroy(ffi::ptr_from(handle)) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeAnalyzerAnalyze(
    env: *mut JniEnv,
    _class: JniObject,
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
        let spectrum_ptr = jni::get_float_elements(env, spectrum);
        let level_ptr = jni::get_float_elements(env, levels);
        if spectrum_ptr.is_null() || level_ptr.is_null() || jni::get_array_length(env, levels) < 4 {
            if !spectrum_ptr.is_null() {
                jni::release_float_elements(env, spectrum, spectrum_ptr, jni::JNI_ABORT);
            }
            if !level_ptr.is_null() {
                jni::release_float_elements(env, levels, level_ptr, jni::JNI_ABORT);
            }
            return;
        }
        let input = if buffer.is_null() || buffer_samples <= 0 {
            std::ptr::null_mut()
        } else {
            jni::get_float_elements(env, buffer)
        };
        let input_samples = if input.is_null() { 0 } else { buffer_samples };
        ffi::klarinet_analyzer_analyze(
            ffi::ptr_from(handle),
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
            jni::release_float_elements(env, buffer, input, jni::JNI_ABORT);
        }
        jni::release_float_elements(env, spectrum, spectrum_ptr, 0);
        jni::release_float_elements(env, levels, level_ptr, 0);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeAnalyzerBandEnergy(
    env: *mut JniEnv,
    _class: JniObject,
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
        let ptr = jni::get_float_elements(env, spectrum);
        if ptr.is_null() {
            return 0.0;
        }
        let energy =
            ffi::klarinet_analyzer_band_energy(ptr, spectrum_len, resolution, low_hz, high_hz);
        jni::release_float_elements(env, spectrum, ptr, jni::JNI_ABORT);
        energy
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeResamplerCreate(
    _env: *mut JniEnv,
    _class: JniObject,
    source_rate: i32,
    channels: i32,
) -> i64 {
    unsafe { ffi::bits_from(ffi::klarinet_resampler_create(source_rate, channels)) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeResamplerDestroy(
    _env: *mut JniEnv,
    _class: JniObject,
    handle: i64,
) {
    unsafe { ffi::klarinet_resampler_destroy(ffi::ptr_from(handle)) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeResamplerHasBuffered(
    _env: *mut JniEnv,
    _class: JniObject,
    handle: i64,
) -> i32 {
    unsafe { ffi::klarinet_resampler_has_buffered(ffi::ptr_from(handle)) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeResampleLinear(
    env: *mut JniEnv,
    _class: JniObject,
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
        let dest_ptr = jni::get_float_elements(env, dest);
        if dest_ptr.is_null() {
            return 0;
        }
        let ctx = JniRead {
            env,
            read,
            method,
            channels,
        };
        let frames = ffi::klarinet_resample_linear(
            ffi::ptr_from(handle),
            dest_rate,
            dest_ptr,
            dest_frames,
            Some(jni_resampler_read),
            &ctx as *const JniRead as *mut c_void,
        );
        jni::release_float_elements(env, dest, dest_ptr, 0);
        if jni::exception_check(env) != 0 || frames < 0 {
            return 0;
        }
        frames
    }
}
