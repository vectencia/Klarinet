//! JNI exports for `com.vectencia.klarinet.JniBridge`.
//!
//! The names and argument lists match the existing Android `external fun`
//! methods. `JniBridge` is a Kotlin object, so the second argument is the
//! instance, not a class.

use std::collections::HashMap;
use std::ffi::CString;
use std::os::raw::c_void;
use std::sync::{LazyLock, Mutex};

use crate::engine::{Engine, OpenRequest};
use crate::jni::{self, JniEnv, JniObject};
use crate::oboe;
use crate::{
    i64_to_usize, lock, usize_to_i64, KLARINET_DSP_ANALYSIS, KLARINET_DSP_EFFECTS,
    KLARINET_DSP_QUEUES,
};

static REGISTRY: LazyLock<Mutex<HashMap<i64, usize>>> =
    LazyLock::new(|| Mutex::new(HashMap::new()));

fn engine_from(handle: i64) -> *mut Engine {
    i64_to_usize(handle) as *mut Engine
}

fn register_stream(stream: i64, engine: *mut Engine) {
    if stream == 0 || engine.is_null() {
        return;
    }
    lock(&REGISTRY).insert(stream, engine as usize);
}

fn unregister_stream(stream: i64) {
    lock(&REGISTRY).remove(&stream);
}

fn unregister_engine(engine: *mut Engine) {
    let needle = engine as usize;
    lock(&REGISTRY).retain(|_, owner| *owner != needle);
}

fn find_engine(stream: i64) -> *mut Engine {
    lock(&REGISTRY)
        .get(&stream)
        .copied()
        .map(|addr| addr as *mut Engine)
        .unwrap_or(std::ptr::null_mut())
}

fn throw_stream_operation(env: *mut JniEnv, message: &str) {
    if env.is_null() {
        return;
    }
    let Ok(bytes) = CString::new(message) else {
        return;
    };
    unsafe {
        let class = jni::find_class(
            env,
            c"com/vectencia/klarinet/StreamOperationException".as_ptr(),
        );
        if class.is_null() {
            return;
        }
        let ctor = jni::get_method_id(
            env,
            class,
            c"<init>".as_ptr(),
            c"(Ljava/lang/String;Ljava/lang/Throwable;)V".as_ptr(),
        );
        if ctor.is_null() {
            jni::delete_local_ref(env, class);
            return;
        }
        let text = jni::new_string_utf(env, bytes.as_ptr());
        if text.is_null() {
            jni::delete_local_ref(env, class);
            return;
        }
        let args = [jni::j_obj(text), jni::j_obj(std::ptr::null_mut())];
        let thrown = jni::new_object_a(env, class, ctor, args.as_ptr());
        if !thrown.is_null() {
            let _ = jni::throw(env, thrown);
            jni::delete_local_ref(env, thrown);
        }
        jni::delete_local_ref(env, text);
        jni::delete_local_ref(env, class);
    }
}

fn throw_if_not_ok(env: *mut JniEnv, op: &str, result: i32) {
    if result == 0 {
        return;
    }
    let text = oboe::result_text(result);
    throw_stream_operation(env, &format!("Failed to {op} stream: {text}"));
}

fn with_engine<T>(stream: i64, missing: T, body: impl FnOnce(&Engine) -> T) -> T {
    let engine = find_engine(stream);
    if engine.is_null() {
        missing
    } else {
        body(unsafe { &*engine })
    }
}

#[no_mangle]
pub extern "system" fn JNI_OnLoad(_vm: *mut c_void, _reserved: *mut c_void) -> i32 {
    std::hint::black_box(KLARINET_DSP_EFFECTS as usize);
    std::hint::black_box(KLARINET_DSP_QUEUES as usize);
    std::hint::black_box(KLARINET_DSP_ANALYSIS as usize);
    klarinet_dsp_core::link_effects();
    klarinet_dsp_core::link_queues();
    klarinet_dsp_core::link_analysis();
    oboe::log_info_lit(c"klarinet_jni", c"klarinet-jni loaded");
    jni::JNI_VERSION_1_6
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeCreateEngine(
    _env: *mut JniEnv,
    _thiz: JniObject,
) -> i64 {
    let engine = Box::new(Engine::new());
    usize_to_i64(Box::into_raw(engine) as usize)
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeDestroyEngine(
    _env: *mut JniEnv,
    _thiz: JniObject,
    engine_handle: i64,
) {
    if engine_handle == 0 {
        return;
    }
    let engine = engine_from(engine_handle);
    unregister_engine(engine);
    unsafe { drop(Box::from_raw(engine)) };
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeOpenStream(
    env: *mut JniEnv,
    _thiz: JniObject,
    engine_handle: i64,
    sample_rate: i32,
    channel_count: i32,
    audio_format: i32,
    buffer_capacity_in_frames: i32,
    performance_mode: i32,
    sharing_mode: i32,
    direction: i32,
    device_id: i32,
    callback: JniObject,
) -> i64 {
    if engine_handle == 0 {
        return 0;
    }
    let request = OpenRequest {
        sample_rate,
        channel_count,
        audio_format,
        buffer_capacity_in_frames,
        performance_mode,
        sharing_mode,
        direction,
        device_id,
    };
    let engine = engine_from(engine_handle);
    let stream = unsafe { (*engine).open(env, request, callback) };
    register_stream(stream, engine);
    stream
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeStartStream(
    env: *mut JniEnv,
    _thiz: JniObject,
    stream_handle: i64,
) {
    let result = with_engine(stream_handle, oboe::invalid_handle(), |engine| {
        engine.start(stream_handle)
    });
    throw_if_not_ok(env, "start", result);
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativePauseStream(
    env: *mut JniEnv,
    _thiz: JniObject,
    stream_handle: i64,
) {
    let result = with_engine(stream_handle, oboe::invalid_handle(), |engine| {
        engine.pause(stream_handle)
    });
    throw_if_not_ok(env, "pause", result);
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeStopStream(
    env: *mut JniEnv,
    _thiz: JniObject,
    stream_handle: i64,
) {
    let result = with_engine(stream_handle, oboe::invalid_handle(), |engine| {
        engine.stop(stream_handle)
    });
    throw_if_not_ok(env, "stop", result);
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeCloseStream(
    _env: *mut JniEnv,
    _thiz: JniObject,
    stream_handle: i64,
) {
    let engine = find_engine(stream_handle);
    if engine.is_null() {
        return;
    }
    unsafe { (*engine).close(stream_handle) };
    unregister_stream(stream_handle);
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeWriteStream(
    env: *mut JniEnv,
    _thiz: JniObject,
    stream_handle: i64,
    data: JniObject,
    num_frames: i32,
    timeout_nanos: i64,
) -> i32 {
    let engine = find_engine(stream_handle);
    if engine.is_null() {
        return -1;
    }
    let elements = unsafe { jni::get_float_array_elements(env, data) };
    if elements.is_null() {
        return -1;
    }
    let result = unsafe { (*engine).write(stream_handle, elements, num_frames, timeout_nanos) };
    unsafe { jni::release_float_array_elements(env, data, elements, jni::JNI_ABORT) };
    result
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeReadStream(
    env: *mut JniEnv,
    _thiz: JniObject,
    stream_handle: i64,
    data: JniObject,
    num_frames: i32,
    timeout_nanos: i64,
) -> i32 {
    let engine = find_engine(stream_handle);
    if engine.is_null() {
        return -1;
    }
    let elements = unsafe { jni::get_float_array_elements(env, data) };
    if elements.is_null() {
        return -1;
    }
    let result = unsafe { (*engine).read(stream_handle, elements, num_frames, timeout_nanos) };
    unsafe { jni::release_float_array_elements(env, data, elements, 0) };
    result
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeGetStreamState(
    _env: *mut JniEnv,
    _thiz: JniObject,
    stream_handle: i64,
) -> i32 {
    with_engine(stream_handle, 0, |engine| engine.state(stream_handle))
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeGetOutputLatencyMs(
    _env: *mut JniEnv,
    _thiz: JniObject,
    stream_handle: i64,
) -> f64 {
    with_engine(stream_handle, 0.0, |engine| {
        engine.latency_ms(stream_handle)
    })
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeGetInputLatencyMs(
    _env: *mut JniEnv,
    _thiz: JniObject,
    stream_handle: i64,
) -> f64 {
    with_engine(stream_handle, 0.0, |engine| {
        engine.latency_ms(stream_handle)
    })
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeCreateEffect(
    _env: *mut JniEnv,
    _thiz: JniObject,
    engine_handle: i64,
    effect_type: i32,
) -> i64 {
    if engine_handle == 0 {
        return 0;
    }
    unsafe { (*engine_from(engine_handle)).create_effect(effect_type) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeSetEffectParameter(
    _env: *mut JniEnv,
    _thiz: JniObject,
    engine_handle: i64,
    effect_handle: i64,
    param_id: i32,
    value: f32,
) {
    if engine_handle == 0 {
        return;
    }
    unsafe {
        (*engine_from(engine_handle)).set_effect_parameter(effect_handle, param_id, value);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeGetEffectParameter(
    _env: *mut JniEnv,
    _thiz: JniObject,
    engine_handle: i64,
    effect_handle: i64,
    param_id: i32,
) -> f32 {
    if engine_handle == 0 {
        return 0.0;
    }
    unsafe { (*engine_from(engine_handle)).effect_parameter(effect_handle, param_id) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeSetEffectEnabled(
    _env: *mut JniEnv,
    _thiz: JniObject,
    engine_handle: i64,
    effect_handle: i64,
    enabled: u8,
) {
    if engine_handle == 0 {
        return;
    }
    unsafe {
        (*engine_from(engine_handle)).set_effect_enabled(effect_handle, enabled != 0);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeIsEffectEnabled(
    _env: *mut JniEnv,
    _thiz: JniObject,
    engine_handle: i64,
    effect_handle: i64,
) -> u8 {
    if engine_handle == 0 {
        return 0;
    }
    u8::from(unsafe { (*engine_from(engine_handle)).effect_enabled(effect_handle) })
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeDestroyEffect(
    _env: *mut JniEnv,
    _thiz: JniObject,
    engine_handle: i64,
    effect_handle: i64,
) {
    if engine_handle == 0 {
        return;
    }
    unsafe { (*engine_from(engine_handle)).destroy_effect(effect_handle) };
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeCreateEffectChain(
    _env: *mut JniEnv,
    _thiz: JniObject,
    engine_handle: i64,
) -> i64 {
    if engine_handle == 0 {
        return 0;
    }
    unsafe { (*engine_from(engine_handle)).create_chain() }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeChainAddEffect(
    _env: *mut JniEnv,
    _thiz: JniObject,
    engine_handle: i64,
    chain_handle: i64,
    effect_handle: i64,
) {
    if engine_handle == 0 {
        return;
    }
    unsafe { (*engine_from(engine_handle)).chain_add(chain_handle, effect_handle) };
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeChainRemoveEffect(
    _env: *mut JniEnv,
    _thiz: JniObject,
    engine_handle: i64,
    chain_handle: i64,
    effect_handle: i64,
) {
    if engine_handle == 0 {
        return;
    }
    unsafe { (*engine_from(engine_handle)).chain_remove(chain_handle, effect_handle) };
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeChainClear(
    _env: *mut JniEnv,
    _thiz: JniObject,
    engine_handle: i64,
    chain_handle: i64,
) {
    if engine_handle == 0 {
        return;
    }
    unsafe { (*engine_from(engine_handle)).chain_clear(chain_handle) };
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeChainPrepare(
    _env: *mut JniEnv,
    _thiz: JniObject,
    engine_handle: i64,
    chain_handle: i64,
    sample_rate: i32,
    channel_count: i32,
) {
    if engine_handle == 0 {
        return;
    }
    unsafe {
        (*engine_from(engine_handle)).chain_prepare(chain_handle, sample_rate, channel_count);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeChainGetEffectCount(
    _env: *mut JniEnv,
    _thiz: JniObject,
    engine_handle: i64,
    chain_handle: i64,
) -> i32 {
    if engine_handle == 0 {
        return 0;
    }
    unsafe { (*engine_from(engine_handle)).chain_count(chain_handle) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeChainEnqueueParam(
    _env: *mut JniEnv,
    _thiz: JniObject,
    engine_handle: i64,
    chain_handle: i64,
    effect_handle: i64,
    param_id: i32,
    value: f32,
) {
    if engine_handle == 0 {
        return;
    }
    unsafe {
        (*engine_from(engine_handle)).chain_enqueue(chain_handle, effect_handle, param_id, value);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeSetStreamEffectChain(
    _env: *mut JniEnv,
    _thiz: JniObject,
    engine_handle: i64,
    stream_handle: i64,
    chain_handle: i64,
) {
    if engine_handle == 0 {
        return;
    }
    unsafe { (*engine_from(engine_handle)).set_stream_chain(stream_handle, chain_handle) };
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeClearStreamEffectChain(
    _env: *mut JniEnv,
    _thiz: JniObject,
    engine_handle: i64,
    stream_handle: i64,
) {
    if engine_handle == 0 {
        return;
    }
    unsafe { (*engine_from(engine_handle)).clear_stream_chain(stream_handle) };
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeDestroyEffectChain(
    _env: *mut JniEnv,
    _thiz: JniObject,
    engine_handle: i64,
    chain_handle: i64,
) {
    if engine_handle == 0 {
        return;
    }
    unsafe { (*engine_from(engine_handle)).destroy_chain(chain_handle) };
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeGetDeviceCount(
    _env: *mut JniEnv,
    _thiz: JniObject,
) -> i32 {
    0
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeGetDeviceInfo(
    env: *mut JniEnv,
    _thiz: JniObject,
    _index: i32,
) -> JniObject {
    let array = unsafe { jni::new_int_array(env, 3) };
    if array.is_null() {
        return std::ptr::null_mut();
    }
    let fill = [0i32, 0, 0];
    unsafe { jni::set_int_array_region(env, array, 0, 3, fill.as_ptr()) };
    array
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeGetDeviceName(
    env: *mut JniEnv,
    _thiz: JniObject,
    _index: i32,
) -> JniObject {
    unsafe { jni::new_string_utf(env, c"Unknown".as_ptr()) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeGetDeviceSampleRates(
    env: *mut JniEnv,
    _thiz: JniObject,
    _index: i32,
) -> JniObject {
    unsafe { jni::new_int_array(env, 0) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeGetDeviceChannelCounts(
    env: *mut JniEnv,
    _thiz: JniObject,
    _index: i32,
) -> JniObject {
    unsafe { jni::new_int_array(env, 0) }
}
