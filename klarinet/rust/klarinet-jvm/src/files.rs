//! Decoder and encoder JNI. These run on the calling thread, not the audio thread.

use crate::ffi;
use crate::jni::{self, JniEnv, JniObject};

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeDecoderInitFile(
    env: *mut JniEnv,
    _class: JniObject,
    path: JniObject,
) -> i64 {
    unsafe {
        match jni::with_utf(env, path, |chars| ffi::klarinet_ma_decoder_open(chars)) {
            Some(decoder) => ffi::bits_from(decoder),
            None => 0,
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeDecoderGetSampleRate(
    _env: *mut JniEnv,
    _class: JniObject,
    decoder_ptr: i64,
) -> i32 {
    unsafe { ffi::klarinet_ma_decoder_sample_rate(ffi::ptr_from(decoder_ptr)) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeDecoderGetChannels(
    _env: *mut JniEnv,
    _class: JniObject,
    decoder_ptr: i64,
) -> i32 {
    unsafe { ffi::klarinet_ma_decoder_channels(ffi::ptr_from(decoder_ptr)) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeDecoderGetTotalFrames(
    _env: *mut JniEnv,
    _class: JniObject,
    decoder_ptr: i64,
) -> i64 {
    unsafe { ffi::klarinet_ma_decoder_total_frames(ffi::ptr_from(decoder_ptr)) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeDecoderReadFrames(
    env: *mut JniEnv,
    _class: JniObject,
    decoder_ptr: i64,
    output: JniObject,
    frame_count: i32,
) -> i64 {
    unsafe {
        let samples = jni::get_float_elements(env, output);
        let frames =
            ffi::klarinet_ma_decoder_read(ffi::ptr_from(decoder_ptr), samples, frame_count);
        jni::release_float_elements(env, output, samples, 0);
        frames
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeDecoderSeek(
    _env: *mut JniEnv,
    _class: JniObject,
    decoder_ptr: i64,
    frame_index: i64,
) -> u8 {
    unsafe {
        if ffi::klarinet_ma_decoder_seek(ffi::ptr_from(decoder_ptr), frame_index) != 0 {
            1
        } else {
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeDecoderUninit(
    _env: *mut JniEnv,
    _class: JniObject,
    decoder_ptr: i64,
) {
    unsafe { ffi::klarinet_ma_decoder_close(ffi::ptr_from(decoder_ptr)) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeEncoderInitFile(
    env: *mut JniEnv,
    _class: JniObject,
    path: JniObject,
    _format: i32,
    channels: i32,
    sample_rate: i32,
) -> i64 {
    unsafe {
        match jni::with_utf(env, path, |chars| {
            ffi::klarinet_ma_encoder_open(chars, channels, sample_rate)
        }) {
            Some(encoder) => ffi::bits_from(encoder),
            None => 0,
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeEncoderWriteFrames(
    env: *mut JniEnv,
    _class: JniObject,
    encoder_ptr: i64,
    data: JniObject,
    frame_count: i32,
) -> u8 {
    unsafe {
        let samples = jni::get_float_elements(env, data);
        let ok = ffi::klarinet_ma_encoder_write(ffi::ptr_from(encoder_ptr), samples, frame_count);
        jni::release_float_elements(env, data, samples, jni::JNI_ABORT);
        if ok != 0 {
            1
        } else {
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeEncoderUninit(
    _env: *mut JniEnv,
    _class: JniObject,
    encoder_ptr: i64,
) {
    unsafe { ffi::klarinet_ma_encoder_close(ffi::ptr_from(encoder_ptr)) }
}
