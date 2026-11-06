//! JNI calls used by the JVM cdylib.
//!
//! Slots include the reserved pointers at the start of each table.
//! `JNINativeInterface_` has four, so FindClass is slot 6. `JNIInvokeInterface_`
//! has three, so GetEnv is slot 6. Counted from JDK 17 `jni.h`.

use std::os::raw::{c_char, c_void};
use std::ptr;

pub type JniObject = *mut c_void;

#[repr(C)]
pub struct JniEnv {
    _private: [u8; 0],
}

const JNI_VERSION_1_6: i32 = 0x0001_0006;
const JNI_OK: i32 = 0;
const JNI_EDETACHED: i32 = -2;
pub const JNI_ABORT: i32 = 2;

const SLOT_EXCEPTION_CLEAR: usize = 17;
const SLOT_NEW_GLOBAL_REF: usize = 21;
const SLOT_DELETE_GLOBAL_REF: usize = 22;
const SLOT_DELETE_LOCAL_REF: usize = 23;
const SLOT_GET_OBJECT_CLASS: usize = 31;
const SLOT_GET_METHOD_ID: usize = 33;
const SLOT_CALL_INT_METHOD_A: usize = 51;
const SLOT_CALL_VOID_METHOD_A: usize = 63;
const SLOT_NEW_STRING_UTF: usize = 167;
const SLOT_GET_STRING_UTF_CHARS: usize = 169;
const SLOT_RELEASE_STRING_UTF_CHARS: usize = 170;
const SLOT_NEW_FLOAT_ARRAY: usize = 181;
const SLOT_GET_FLOAT_ARRAY_ELEMENTS: usize = 189;
const SLOT_RELEASE_FLOAT_ARRAY_ELEMENTS: usize = 197;
const SLOT_GET_FLOAT_ARRAY_REGION: usize = 205;
const SLOT_SET_FLOAT_ARRAY_REGION: usize = 213;
const SLOT_GET_JAVA_VM: usize = 219;
const SLOT_EXCEPTION_CHECK: usize = 228;

const VM_GET_ENV: usize = 6;
const VM_ATTACH_DAEMON: usize = 7;

#[repr(C)]
union JValue {
    j: i64,
    i: i32,
    l: JniObject,
}

unsafe fn table(env: *mut JniEnv) -> *const *const c_void {
    *(env as *const *const *const c_void)
}

unsafe fn vm_table(vm: *mut c_void) -> *const *const c_void {
    *(vm as *const *const *const c_void)
}

macro_rules! slot_fn {
    ($table:expr, $slot:expr, $ty:ty) => {{
        let fp = *$table.add($slot);
        core::mem::transmute::<*const c_void, $ty>(fp)
    }};
}

pub unsafe fn with_attached(vm: *mut c_void) -> Option<*mut JniEnv> {
    if vm.is_null() {
        return None;
    }
    type GetEnv = unsafe extern "C" fn(*mut c_void, *mut *mut c_void, i32) -> i32;
    type Attach = unsafe extern "C" fn(*mut c_void, *mut *mut c_void, *mut c_void) -> i32;
    let get_env: GetEnv = slot_fn!(vm_table(vm), VM_GET_ENV, GetEnv);
    let mut raw: *mut c_void = ptr::null_mut();
    let rc = get_env(vm, &mut raw, JNI_VERSION_1_6);
    if rc == JNI_EDETACHED {
        let attach: Attach = slot_fn!(vm_table(vm), VM_ATTACH_DAEMON, Attach);
        if attach(vm, &mut raw, ptr::null_mut()) != JNI_OK {
            return None;
        }
    }
    if raw.is_null() {
        None
    } else {
        Some(raw as *mut JniEnv)
    }
}

pub unsafe fn get_java_vm(env: *mut JniEnv, out: *mut *mut c_void) {
    type GetJavaVm = unsafe extern "C" fn(*mut JniEnv, *mut *mut c_void) -> i32;
    let func: GetJavaVm = slot_fn!(table(env), SLOT_GET_JAVA_VM, GetJavaVm);
    let _ = func(env, out);
}

pub unsafe fn new_global_ref(env: *mut JniEnv, object: JniObject) -> JniObject {
    type NewGlobalRef = unsafe extern "C" fn(*mut JniEnv, JniObject) -> JniObject;
    let func: NewGlobalRef = slot_fn!(table(env), SLOT_NEW_GLOBAL_REF, NewGlobalRef);
    func(env, object)
}

pub unsafe fn delete_global_ref(env: *mut JniEnv, object: JniObject) {
    type DeleteRef = unsafe extern "C" fn(*mut JniEnv, JniObject);
    let func: DeleteRef = slot_fn!(table(env), SLOT_DELETE_GLOBAL_REF, DeleteRef);
    func(env, object);
}

pub unsafe fn delete_local_ref(env: *mut JniEnv, object: JniObject) {
    type DeleteRef = unsafe extern "C" fn(*mut JniEnv, JniObject);
    let func: DeleteRef = slot_fn!(table(env), SLOT_DELETE_LOCAL_REF, DeleteRef);
    func(env, object);
}

pub unsafe fn get_object_class(env: *mut JniEnv, object: JniObject) -> JniObject {
    type GetObjectClass = unsafe extern "C" fn(*mut JniEnv, JniObject) -> JniObject;
    let func: GetObjectClass = slot_fn!(table(env), SLOT_GET_OBJECT_CLASS, GetObjectClass);
    func(env, object)
}

pub unsafe fn get_method_id(
    env: *mut JniEnv,
    class: JniObject,
    name: *const c_char,
    signature: *const c_char,
) -> JniObject {
    type GetMethodId =
        unsafe extern "C" fn(*mut JniEnv, JniObject, *const c_char, *const c_char) -> JniObject;
    let func: GetMethodId = slot_fn!(table(env), SLOT_GET_METHOD_ID, GetMethodId);
    func(env, class, name, signature)
}

pub unsafe fn exception_check(env: *mut JniEnv) -> u8 {
    type ExceptionCheck = unsafe extern "C" fn(*mut JniEnv) -> u8;
    let func: ExceptionCheck = slot_fn!(table(env), SLOT_EXCEPTION_CHECK, ExceptionCheck);
    func(env)
}

pub unsafe fn exception_clear(env: *mut JniEnv) {
    type ExceptionClear = unsafe extern "C" fn(*mut JniEnv);
    let func: ExceptionClear = slot_fn!(table(env), SLOT_EXCEPTION_CLEAR, ExceptionClear);
    func(env);
}

pub unsafe fn new_string_utf(env: *mut JniEnv, bytes: *const c_char) -> JniObject {
    type NewStringUtf = unsafe extern "C" fn(*mut JniEnv, *const c_char) -> JniObject;
    let func: NewStringUtf = slot_fn!(table(env), SLOT_NEW_STRING_UTF, NewStringUtf);
    func(env, bytes)
}

pub unsafe fn with_utf<R>(
    env: *mut JniEnv,
    value: JniObject,
    body: impl FnOnce(*const c_char) -> R,
) -> Option<R> {
    if value.is_null() {
        return None;
    }
    type GetUtf = unsafe extern "C" fn(*mut JniEnv, JniObject, *mut u8) -> *const c_char;
    type ReleaseUtf = unsafe extern "C" fn(*mut JniEnv, JniObject, *const c_char);
    let get: GetUtf = slot_fn!(table(env), SLOT_GET_STRING_UTF_CHARS, GetUtf);
    let chars = get(env, value, ptr::null_mut());
    if chars.is_null() {
        return None;
    }
    let result = body(chars);
    let release: ReleaseUtf = slot_fn!(table(env), SLOT_RELEASE_STRING_UTF_CHARS, ReleaseUtf);
    release(env, value, chars);
    Some(result)
}

pub unsafe fn new_float_array(env: *mut JniEnv, length: i32) -> JniObject {
    type NewFloatArray = unsafe extern "C" fn(*mut JniEnv, i32) -> JniObject;
    let func: NewFloatArray = slot_fn!(table(env), SLOT_NEW_FLOAT_ARRAY, NewFloatArray);
    func(env, length)
}

pub unsafe fn set_float_region(
    env: *mut JniEnv,
    array: JniObject,
    start: i32,
    length: i32,
    buffer: *const f32,
) {
    type SetRegion = unsafe extern "C" fn(*mut JniEnv, JniObject, i32, i32, *const f32);
    let func: SetRegion = slot_fn!(table(env), SLOT_SET_FLOAT_ARRAY_REGION, SetRegion);
    func(env, array, start, length, buffer);
}

pub unsafe fn get_float_region(
    env: *mut JniEnv,
    array: JniObject,
    start: i32,
    length: i32,
    buffer: *mut f32,
) {
    type GetRegion = unsafe extern "C" fn(*mut JniEnv, JniObject, i32, i32, *mut f32);
    let func: GetRegion = slot_fn!(table(env), SLOT_GET_FLOAT_ARRAY_REGION, GetRegion);
    func(env, array, start, length, buffer);
}

pub unsafe fn get_float_elements(env: *mut JniEnv, array: JniObject) -> *mut f32 {
    type GetElements = unsafe extern "C" fn(*mut JniEnv, JniObject, *mut u8) -> *mut f32;
    let func: GetElements = slot_fn!(table(env), SLOT_GET_FLOAT_ARRAY_ELEMENTS, GetElements);
    func(env, array, ptr::null_mut())
}

pub unsafe fn release_float_elements(
    env: *mut JniEnv,
    array: JniObject,
    elements: *mut f32,
    mode: i32,
) {
    type Release = unsafe extern "C" fn(*mut JniEnv, JniObject, *mut f32, i32);
    let func: Release = slot_fn!(table(env), SLOT_RELEASE_FLOAT_ARRAY_ELEMENTS, Release);
    func(env, array, elements, mode);
}

pub unsafe fn call_int_audio(
    env: *mut JniEnv,
    object: JniObject,
    method: JniObject,
    buffer: JniObject,
    frames: i32,
) -> i32 {
    type CallIntA = unsafe extern "C" fn(*mut JniEnv, JniObject, JniObject, *const JValue) -> i32;
    let mut args = [JValue { j: 0 }, JValue { j: 0 }];
    args[0].l = buffer;
    args[1].i = frames;
    let func: CallIntA = slot_fn!(table(env), SLOT_CALL_INT_METHOD_A, CallIntA);
    func(env, object, method, args.as_ptr())
}

pub unsafe fn call_void_xrun(env: *mut JniEnv, object: JniObject, method: JniObject, count: i32) {
    type CallVoidA = unsafe extern "C" fn(*mut JniEnv, JniObject, JniObject, *const JValue);
    let mut arg = JValue { j: 0 };
    arg.i = count;
    let func: CallVoidA = slot_fn!(table(env), SLOT_CALL_VOID_METHOD_A, CallVoidA);
    func(env, object, method, &arg);
}
