//! Device, context, and the miniaudio data callback.
//!
//! `audio_callback` runs on the audio thread. It must not allocate, lock,
//! panic, or enter the JVM. Samples move through `klarinet_offload_process`.
//! The worker callbacks below are the only place that enters the JVM.

use std::mem::MaybeUninit;
use std::os::raw::c_void;
use std::ptr;
use std::sync::atomic::{AtomicI32, AtomicPtr, Ordering};

use crate::ffi::{self, DataFn, UserAudioFn, XrunFn};
use crate::jni::{self, JniEnv, JniObject};

const CAPTURE_SCRATCH: i32 = 8192;
const UNKNOWN: &[u8] = b"Unknown\0";
const ON_AUDIO_READY: &[u8] = b"onAudioReady\0";
const ON_AUDIO_READY_SIG: &[u8] = b"([FI)I\0";
const NOTIFY_XRUN: &[u8] = b"notifyXrun\0";
const NOTIFY_XRUN_SIG: &[u8] = b"(I)V\0";

struct Device {
    ma: *mut c_void,
    channel_count: AtomicI32,
    chain: AtomicPtr<c_void>,
    offload: AtomicPtr<c_void>,
    jvm: *mut c_void,
    callback: JniObject,
    on_audio_ready: JniObject,
    notify_xrun: JniObject,
}

fn load_device<'a>(user: *mut c_void) -> Option<&'a Device> {
    if user.is_null() {
        None
    } else {
        Some(unsafe { &*user.cast::<Device>() })
    }
}

fn capture_samples(
    chain: *mut c_void,
    offload: *mut c_void,
    input: *const f32,
    frame_count: i32,
    channels: i32,
) {
    let total = frame_count.checked_mul(channels);
    if !chain.is_null() {
        if let Some(total) = total {
            if (0..=CAPTURE_SCRATCH).contains(&total) {
                let mut work = MaybeUninit::<[f32; CAPTURE_SCRATCH as usize]>::uninit();
                unsafe {
                    if total > 0 {
                        ptr::copy_nonoverlapping(
                            input,
                            work.as_mut_ptr().cast::<f32>(),
                            total as usize,
                        );
                    }
                    let samples = work.as_mut_ptr().cast::<f32>();
                    ffi::klarinet_chain_process(chain, samples, frame_count, channels);
                    if !offload.is_null() {
                        ffi::klarinet_offload_process(offload, samples, frame_count);
                    }
                }
                return;
            }
        }
    }
    if !offload.is_null() {
        unsafe {
            ffi::klarinet_offload_process(offload, input as *mut f32, frame_count);
        }
    }
}

fn playback_samples(
    chain: *mut c_void,
    offload: *mut c_void,
    output: *mut f32,
    frame_count: i32,
    channels: i32,
) {
    unsafe {
        if offload.is_null() {
            if let Some(total) = frame_count.checked_mul(channels) {
                if total > 0 {
                    ptr::write_bytes(output, 0, total as usize);
                }
            }
        } else {
            ffi::klarinet_offload_process(offload, output, frame_count);
        }
        if !chain.is_null() {
            ffi::klarinet_chain_process(chain, output, frame_count, channels);
        }
    }
}

unsafe extern "C" fn audio_callback(
    user: *mut c_void,
    is_capture: i32,
    output: *mut f32,
    input: *const f32,
    frame_count: i32,
) {
    let Some(device) = load_device(user) else {
        return;
    };
    let channels = device.channel_count.load(Ordering::SeqCst);
    let chain = device.chain.load(Ordering::SeqCst);
    let offload = device.offload.load(Ordering::SeqCst);
    if is_capture != 0 {
        capture_samples(chain, offload, input, frame_count, channels);
    } else if !output.is_null() {
        playback_samples(chain, offload, output, frame_count, channels);
    }
}

unsafe extern "C" fn jvm_user_audio(
    user: *mut c_void,
    buffer: *mut f32,
    num_frames: i32,
    channel_count: i32,
) -> i32 {
    let Some(device) = load_device(user) else {
        return 0;
    };
    if device.jvm.is_null() || device.callback.is_null() {
        return 0;
    }
    let Some(env) = jni::with_attached(device.jvm) else {
        return 0;
    };
    let Some(total) = num_frames.checked_mul(channel_count) else {
        return 0;
    };
    if total < 0 {
        return 0;
    }
    let jbuffer = jni::new_float_array(env, total);
    if jbuffer.is_null() {
        return 0;
    }
    jni::set_float_region(env, jbuffer, 0, total, buffer);
    let frames = jni::call_int_audio(
        env,
        device.callback,
        device.on_audio_ready,
        jbuffer,
        num_frames,
    );
    if jni::exception_check(env) != 0 {
        jni::exception_clear(env);
        jni::delete_local_ref(env, jbuffer);
        return 0;
    }
    if frames > 0 {
        if let Some(out_samples) = frames.checked_mul(channel_count) {
            if out_samples > 0 {
                jni::get_float_region(env, jbuffer, 0, out_samples, buffer);
            }
        }
    }
    jni::delete_local_ref(env, jbuffer);
    frames
}

unsafe extern "C" fn jvm_xrun(user: *mut c_void, count: i32) {
    let Some(device) = load_device(user) else {
        return;
    };
    if device.jvm.is_null() || device.callback.is_null() || device.notify_xrun.is_null() {
        return;
    }
    let Some(env) = jni::with_attached(device.jvm) else {
        return;
    };
    jni::call_void_xrun(env, device.callback, device.notify_xrun, count);
    if jni::exception_check(env) != 0 {
        jni::exception_clear(env);
    }
}

fn audio_fn() -> DataFn {
    audio_callback
}

fn user_fn() -> UserAudioFn {
    jvm_user_audio
}

fn xrun_fn() -> XrunFn {
    jvm_xrun
}

unsafe fn bind_callback(env: *mut JniEnv, callback: JniObject, device: &mut Device) {
    jni::get_java_vm(env, &mut device.jvm);
    device.callback = jni::new_global_ref(env, callback);
    let class = jni::get_object_class(env, callback);
    device.on_audio_ready = jni::get_method_id(
        env,
        class,
        ON_AUDIO_READY.as_ptr().cast(),
        ON_AUDIO_READY_SIG.as_ptr().cast(),
    );
    device.notify_xrun = jni::get_method_id(
        env,
        class,
        NOTIFY_XRUN.as_ptr().cast(),
        NOTIFY_XRUN_SIG.as_ptr().cast(),
    );
    if device.notify_xrun.is_null() {
        jni::exception_clear(env);
    }
    jni::delete_local_ref(env, class);
}

unsafe fn fail_device(env: *mut JniEnv, ptr: *mut Device) {
    let offload = (*ptr).offload.load(Ordering::SeqCst);
    if !offload.is_null() {
        ffi::klarinet_offload_destroy(offload);
    }
    if !(*ptr).callback.is_null() {
        jni::delete_global_ref(env, (*ptr).callback);
    }
    drop(Box::from_raw(ptr));
}

struct DeviceRequest {
    context_bits: i64,
    sample_rate: i32,
    channel_count: i32,
    buffer_frames: i32,
    direction: i32,
    device_id: i32,
}

unsafe fn device_init(env: *mut JniEnv, request: DeviceRequest, callback: JniObject) -> i64 {
    let DeviceRequest {
        context_bits,
        sample_rate,
        channel_count,
        buffer_frames,
        direction,
        device_id,
    } = request;
    let context = ffi::ptr_from(context_bits);
    if ffi::klarinet_ma_check_device_id(context, direction, device_id) != 0 {
        return 0;
    }
    let device = Box::new(Device {
        ma: ptr::null_mut(),
        channel_count: AtomicI32::new(channel_count),
        chain: AtomicPtr::new(ptr::null_mut()),
        offload: AtomicPtr::new(ptr::null_mut()),
        jvm: ptr::null_mut(),
        callback: ptr::null_mut(),
        on_audio_ready: ptr::null_mut(),
        notify_xrun: ptr::null_mut(),
    });
    let ptr = Box::into_raw(device);
    if !callback.is_null() {
        bind_callback(env, callback, &mut *ptr);
        let burst = if buffer_frames > 0 {
            buffer_frames
        } else {
            256
        };
        let is_capture = if direction == 0 { 0 } else { 1 };
        let offload =
            ffi::klarinet_offload_create(burst, channel_count, is_capture, user_fn(), ptr.cast());
        (*ptr).offload.store(offload, Ordering::SeqCst);
        if !offload.is_null() && !(*ptr).notify_xrun.is_null() {
            ffi::klarinet_offload_set_xrun_callback(offload, xrun_fn());
        }
    }
    let config = ffi::MaDeviceConfig {
        sample_rate,
        channel_count,
        period_frames: buffer_frames,
        direction,
        device_id,
        data_callback: if callback.is_null() {
            None
        } else {
            Some(audio_fn())
        },
        user: ptr.cast(),
    };
    let ma = ffi::klarinet_ma_device_init(context, &config);
    if ma.is_null() {
        fail_device(env, ptr);
        return 0;
    }
    (*ptr).ma = ma;
    ffi::bits_from(ptr.cast())
}

unsafe fn device_name(env: *mut JniEnv, context_bits: i64, index: i32, capture: bool) -> JniObject {
    let mut bytes = [0u8; 256];
    let context = ffi::ptr_from(context_bits);
    let rc = if capture {
        ffi::klarinet_ma_capture_name(context, index, bytes.as_mut_ptr().cast(), 256)
    } else {
        ffi::klarinet_ma_playback_name(context, index, bytes.as_mut_ptr().cast(), 256)
    };
    if rc != 0 {
        jni::new_string_utf(env, UNKNOWN.as_ptr().cast())
    } else {
        jni::new_string_utf(env, bytes.as_ptr().cast())
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeContextInit(
    _env: *mut JniEnv,
    _class: JniObject,
) -> i64 {
    unsafe { ffi::bits_from(ffi::klarinet_ma_context_init()) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeContextUninit(
    _env: *mut JniEnv,
    _class: JniObject,
    context_ptr: i64,
) {
    unsafe { ffi::klarinet_ma_context_uninit(ffi::ptr_from(context_ptr)) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeDeviceInit(
    env: *mut JniEnv,
    _class: JniObject,
    context_ptr: i64,
    sample_rate: i32,
    channel_count: i32,
    buffer_capacity_in_frames: i32,
    direction: i32,
    device_id: i32,
    callback: JniObject,
) -> i64 {
    unsafe {
        device_init(
            env,
            DeviceRequest {
                context_bits: context_ptr,
                sample_rate,
                channel_count,
                buffer_frames: buffer_capacity_in_frames,
                direction,
                device_id,
            },
            callback,
        )
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeDeviceStart(
    _env: *mut JniEnv,
    _class: JniObject,
    device_ptr: i64,
) {
    unsafe {
        let device = ffi::ptr_from(device_ptr).cast::<Device>();
        if !device.is_null() && !(*device).ma.is_null() {
            ffi::klarinet_ma_device_start((*device).ma);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeDeviceStop(
    _env: *mut JniEnv,
    _class: JniObject,
    device_ptr: i64,
) {
    unsafe {
        let device = ffi::ptr_from(device_ptr).cast::<Device>();
        if !device.is_null() && !(*device).ma.is_null() {
            ffi::klarinet_ma_device_stop((*device).ma);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeDeviceUninit(
    env: *mut JniEnv,
    _class: JniObject,
    device_ptr: i64,
) {
    unsafe {
        let device = ffi::ptr_from(device_ptr).cast::<Device>();
        if device.is_null() {
            return;
        }
        if !(*device).ma.is_null() {
            ffi::klarinet_ma_device_uninit((*device).ma);
        }
        let offload = (*device).offload.load(Ordering::SeqCst);
        if !offload.is_null() {
            ffi::klarinet_offload_destroy(offload);
        }
        if !(*device).callback.is_null() {
            jni::delete_global_ref(env, (*device).callback);
        }
        drop(Box::from_raw(device));
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeDeviceWriteFloat(
    _env: *mut JniEnv,
    _class: JniObject,
    _device_ptr: i64,
    _data: JniObject,
    _num_frames: i32,
    _timeout_nanos: i64,
) -> i32 {
    -1
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeDeviceReadFloat(
    _env: *mut JniEnv,
    _class: JniObject,
    _device_ptr: i64,
    _data: JniObject,
    _num_frames: i32,
    _timeout_nanos: i64,
) -> i32 {
    -1
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeDeviceGetState(
    _env: *mut JniEnv,
    _class: JniObject,
    device_ptr: i64,
) -> i32 {
    unsafe {
        let device = ffi::ptr_from(device_ptr).cast::<Device>();
        if device.is_null() || (*device).ma.is_null() {
            0
        } else {
            ffi::klarinet_ma_device_state((*device).ma)
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeDeviceGetLatencyMs(
    _env: *mut JniEnv,
    _class: JniObject,
    device_ptr: i64,
) -> f64 {
    unsafe {
        let device = ffi::ptr_from(device_ptr).cast::<Device>();
        if device.is_null() || (*device).ma.is_null() {
            0.0
        } else {
            ffi::klarinet_ma_device_latency_ms((*device).ma)
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeGetPlaybackDeviceCount(
    _env: *mut JniEnv,
    _class: JniObject,
    context_ptr: i64,
) -> i32 {
    unsafe { ffi::klarinet_ma_playback_count(ffi::ptr_from(context_ptr)) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeGetCaptureDeviceCount(
    _env: *mut JniEnv,
    _class: JniObject,
    context_ptr: i64,
) -> i32 {
    unsafe { ffi::klarinet_ma_capture_count(ffi::ptr_from(context_ptr)) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeGetPlaybackDeviceName(
    env: *mut JniEnv,
    _class: JniObject,
    context_ptr: i64,
    index: i32,
) -> JniObject {
    unsafe { device_name(env, context_ptr, index, false) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeGetCaptureDeviceName(
    env: *mut JniEnv,
    _class: JniObject,
    context_ptr: i64,
    index: i32,
) -> JniObject {
    unsafe { device_name(env, context_ptr, index, true) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeSetDeviceEffectChain(
    _env: *mut JniEnv,
    _class: JniObject,
    device_ptr: i64,
    chain_handle: i64,
) {
    unsafe {
        let device = ffi::ptr_from(device_ptr).cast::<Device>();
        if !device.is_null() {
            (*device)
                .chain
                .store(ffi::ptr_from(chain_handle), Ordering::SeqCst);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeClearDeviceEffectChain(
    _env: *mut JniEnv,
    _class: JniObject,
    device_ptr: i64,
) {
    unsafe {
        let device = ffi::ptr_from(device_ptr).cast::<Device>();
        if !device.is_null() {
            (*device).chain.store(ptr::null_mut(), Ordering::SeqCst);
        }
    }
}
