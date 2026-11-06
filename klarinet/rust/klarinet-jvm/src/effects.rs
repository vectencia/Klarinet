//! Effect and chain JNI. `nativeChainProcess` runs on the caller thread.
//! The audio thread reaches the chain through `klarinet_chain_process` inside
//! the miniaudio callback, not through this file.

use crate::ffi;
use crate::jni::{self, JniEnv, JniObject};

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeCreateEffect(
    _env: *mut JniEnv,
    _class: JniObject,
    effect_type: i32,
) -> i64 {
    unsafe { ffi::bits_from(ffi::klarinet_create_effect(effect_type)) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeDestroyEffect(
    _env: *mut JniEnv,
    _class: JniObject,
    effect_handle: i64,
) {
    unsafe { ffi::klarinet_effect_destroy(ffi::ptr_from(effect_handle)) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeSetEffectParameter(
    _env: *mut JniEnv,
    _class: JniObject,
    effect_handle: i64,
    param_id: i32,
    value: f32,
) {
    unsafe {
        ffi::klarinet_effect_set_parameter(ffi::ptr_from(effect_handle), param_id, value);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeGetEffectParameter(
    _env: *mut JniEnv,
    _class: JniObject,
    effect_handle: i64,
    param_id: i32,
) -> f32 {
    unsafe { ffi::klarinet_effect_get_parameter(ffi::ptr_from(effect_handle), param_id) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeSetEffectEnabled(
    _env: *mut JniEnv,
    _class: JniObject,
    effect_handle: i64,
    enabled: u8,
) {
    unsafe {
        ffi::klarinet_effect_set_enabled(ffi::ptr_from(effect_handle), i32::from(enabled != 0));
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeIsEffectEnabled(
    _env: *mut JniEnv,
    _class: JniObject,
    effect_handle: i64,
) -> u8 {
    unsafe {
        if ffi::klarinet_effect_is_enabled(ffi::ptr_from(effect_handle)) != 0 {
            1
        } else {
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeEffectPrepare(
    _env: *mut JniEnv,
    _class: JniObject,
    effect_handle: i64,
    sample_rate: i32,
    channel_count: i32,
) {
    unsafe {
        ffi::klarinet_effect_prepare(ffi::ptr_from(effect_handle), sample_rate, channel_count);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeCreateEffectChain(
    _env: *mut JniEnv,
    _class: JniObject,
) -> i64 {
    unsafe { ffi::bits_from(ffi::klarinet_chain_create()) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeDestroyEffectChain(
    _env: *mut JniEnv,
    _class: JniObject,
    chain_handle: i64,
) {
    unsafe { ffi::klarinet_chain_destroy(ffi::ptr_from(chain_handle)) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeChainAddEffect(
    _env: *mut JniEnv,
    _class: JniObject,
    chain_handle: i64,
    effect_handle: i64,
) {
    unsafe {
        ffi::klarinet_chain_add(ffi::ptr_from(chain_handle), ffi::ptr_from(effect_handle));
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeChainRemoveEffect(
    _env: *mut JniEnv,
    _class: JniObject,
    chain_handle: i64,
    effect_handle: i64,
) {
    unsafe {
        ffi::klarinet_chain_remove(ffi::ptr_from(chain_handle), ffi::ptr_from(effect_handle));
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeChainClear(
    _env: *mut JniEnv,
    _class: JniObject,
    chain_handle: i64,
) {
    unsafe { ffi::klarinet_chain_clear(ffi::ptr_from(chain_handle)) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeChainPrepare(
    _env: *mut JniEnv,
    _class: JniObject,
    chain_handle: i64,
    sample_rate: i32,
    channel_count: i32,
) {
    unsafe {
        ffi::klarinet_chain_prepare(ffi::ptr_from(chain_handle), sample_rate, channel_count);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeChainGetEffectCount(
    _env: *mut JniEnv,
    _class: JniObject,
    chain_handle: i64,
) -> i32 {
    unsafe { ffi::klarinet_chain_get_effect_count(ffi::ptr_from(chain_handle)) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeChainEnqueueParam(
    _env: *mut JniEnv,
    _class: JniObject,
    chain_handle: i64,
    effect_handle: i64,
    param_id: i32,
    value: f32,
) {
    unsafe {
        ffi::klarinet_chain_enqueue_param(
            ffi::ptr_from(chain_handle),
            ffi::ptr_from(effect_handle),
            param_id,
            value,
        );
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeChainProcess(
    env: *mut JniEnv,
    _class: JniObject,
    chain_handle: i64,
    data: JniObject,
    num_frames: i32,
    channel_count: i32,
) {
    unsafe {
        if data.is_null() {
            return;
        }
        let samples = jni::get_float_elements(env, data);
        if samples.is_null() {
            return;
        }
        ffi::klarinet_chain_process(
            ffi::ptr_from(chain_handle),
            samples,
            num_frames,
            channel_count,
        );
        jni::release_float_elements(env, data, samples, 0);
    }
}
