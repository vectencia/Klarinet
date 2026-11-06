//! JNI slots for the Android audio worker and the routing thread.
//!
//! Counted from JDK 17 `jni.h`. `JNINativeInterface_` has four reserved
//! pointers, so FindClass is slot 6. `JNIInvokeInterface_` has three, so
//! AttachCurrentThread is slot 4. Android uses that slot. The JVM cdylib's
//! daemon attach slot is a different entry.

use std::os::raw::{c_char, c_void};
use std::ptr;

pub type JniObject = *mut c_void;

#[repr(C)]
pub struct JniEnv {
    _private: [u8; 0],
}

pub const JNI_VERSION_1_6: i32 = 0x0001_0006;
pub const JNI_OK: i32 = 0;
pub const JNI_EDETACHED: i32 = -2;
pub const JNI_ABORT: i32 = 2;

const SLOT_FIND_CLASS: usize = 6;
const SLOT_THROW: usize = 13;
const SLOT_EXCEPTION_DESCRIBE: usize = 16;
const SLOT_EXCEPTION_CLEAR: usize = 17;
const SLOT_NEW_GLOBAL_REF: usize = 21;
const SLOT_DELETE_GLOBAL_REF: usize = 22;
const SLOT_DELETE_LOCAL_REF: usize = 23;
const SLOT_NEW_OBJECT_A: usize = 30;
const SLOT_GET_OBJECT_CLASS: usize = 31;
const SLOT_GET_METHOD_ID: usize = 33;
const SLOT_CALL_OBJECT_METHOD_A: usize = 36;
const SLOT_CALL_INT_METHOD_A: usize = 51;
const SLOT_CALL_VOID_METHOD_A: usize = 63;
const SLOT_GET_STATIC_METHOD_ID: usize = 113;
const SLOT_CALL_STATIC_VOID_METHOD_A: usize = 143;
const SLOT_NEW_STRING_UTF: usize = 167;
const SLOT_GET_ARRAY_LENGTH: usize = 171;
const SLOT_NEW_INT_ARRAY: usize = 179;
const SLOT_NEW_FLOAT_ARRAY: usize = 181;
const SLOT_GET_FLOAT_ARRAY_ELEMENTS: usize = 189;
const SLOT_RELEASE_FLOAT_ARRAY_ELEMENTS: usize = 197;
const SLOT_GET_FLOAT_ARRAY_REGION: usize = 205;
const SLOT_SET_INT_ARRAY_REGION: usize = 211;
const SLOT_SET_FLOAT_ARRAY_REGION: usize = 213;
const SLOT_GET_JAVA_VM: usize = 219;
const SLOT_EXCEPTION_CHECK: usize = 228;

const VM_ATTACH: usize = 4;
const VM_DETACH: usize = 5;
const VM_GET_ENV: usize = 6;

#[repr(C)]
pub union JValue {
    pub j: i64,
    pub i: i32,
    pub l: JniObject,
}

pub fn j_int(value: i32) -> JValue {
    let mut out = JValue { j: 0 };
    out.i = value;
    out
}

pub fn j_obj(value: JniObject) -> JValue {
    let mut out = JValue { j: 0 };
    out.l = value;
    out
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

pub unsafe fn vm_from_env(env: *mut JniEnv, out: *mut *mut c_void) {
    type GetJavaVm = unsafe extern "C" fn(*mut JniEnv, *mut *mut c_void) -> i32;
    if env.is_null() || out.is_null() {
        return;
    }
    let func: GetJavaVm = slot_fn!(table(env), SLOT_GET_JAVA_VM, GetJavaVm);
    let _ = func(env, out);
}

/// `AttachCurrentThread`. The Android worker is a new thread, so this is the
/// call `KlarinetCallback::workerLoop` makes. It does not use the daemon slot.
pub unsafe fn attach_current(vm: *mut c_void) -> Option<*mut JniEnv> {
    if vm.is_null() {
        return None;
    }
    type Attach = unsafe extern "C" fn(*mut c_void, *mut *mut c_void, *mut c_void) -> i32;
    let attach: Attach = slot_fn!(vm_table(vm), VM_ATTACH, Attach);
    let mut raw: *mut c_void = ptr::null_mut();
    if attach(vm, &mut raw, ptr::null_mut()) != JNI_OK || raw.is_null() {
        None
    } else {
        Some(raw as *mut JniEnv)
    }
}

/// Attaches with `AttachCurrentThread` when the thread is detached.
/// The bool is true only when this call attached.
pub unsafe fn attach_or_get(vm: *mut c_void) -> Option<(*mut JniEnv, bool)> {
    if vm.is_null() {
        return None;
    }
    type GetEnv = unsafe extern "C" fn(*mut c_void, *mut *mut c_void, i32) -> i32;
    type Attach = unsafe extern "C" fn(*mut c_void, *mut *mut c_void, *mut c_void) -> i32;
    let get_env: GetEnv = slot_fn!(vm_table(vm), VM_GET_ENV, GetEnv);
    let mut raw: *mut c_void = ptr::null_mut();
    let rc = get_env(vm, &mut raw, JNI_VERSION_1_6);
    if rc == JNI_EDETACHED {
        let attach: Attach = slot_fn!(vm_table(vm), VM_ATTACH, Attach);
        if attach(vm, &mut raw, ptr::null_mut()) != JNI_OK || raw.is_null() {
            return None;
        }
        return Some((raw as *mut JniEnv, true));
    }
    if raw.is_null() {
        None
    } else {
        Some((raw as *mut JniEnv, false))
    }
}

pub unsafe fn detach(vm: *mut c_void) {
    if vm.is_null() {
        return;
    }
    type Detach = unsafe extern "C" fn(*mut c_void) -> i32;
    let func: Detach = slot_fn!(vm_table(vm), VM_DETACH, Detach);
    let _ = func(vm);
}

pub unsafe fn find_class(env: *mut JniEnv, name: *const c_char) -> JniObject {
    type FindClass = unsafe extern "C" fn(*mut JniEnv, *const c_char) -> JniObject;
    let func: FindClass = slot_fn!(table(env), SLOT_FIND_CLASS, FindClass);
    func(env, name)
}

pub unsafe fn throw(env: *mut JniEnv, throwable: JniObject) -> i32 {
    type Throw = unsafe extern "C" fn(*mut JniEnv, JniObject) -> i32;
    let func: Throw = slot_fn!(table(env), SLOT_THROW, Throw);
    func(env, throwable)
}

pub unsafe fn exception_describe(env: *mut JniEnv) {
    type Describe = unsafe extern "C" fn(*mut JniEnv);
    let func: Describe = slot_fn!(table(env), SLOT_EXCEPTION_DESCRIBE, Describe);
    func(env);
}

pub unsafe fn exception_clear(env: *mut JniEnv) {
    type Clear = unsafe extern "C" fn(*mut JniEnv);
    let func: Clear = slot_fn!(table(env), SLOT_EXCEPTION_CLEAR, Clear);
    func(env);
}

pub unsafe fn exception_check(env: *mut JniEnv) -> u8 {
    type Check = unsafe extern "C" fn(*mut JniEnv) -> u8;
    let func: Check = slot_fn!(table(env), SLOT_EXCEPTION_CHECK, Check);
    func(env)
}

pub unsafe fn new_global_ref(env: *mut JniEnv, object: JniObject) -> JniObject {
    type NewGlobal = unsafe extern "C" fn(*mut JniEnv, JniObject) -> JniObject;
    let func: NewGlobal = slot_fn!(table(env), SLOT_NEW_GLOBAL_REF, NewGlobal);
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

pub unsafe fn new_object_a(
    env: *mut JniEnv,
    class: JniObject,
    method: JniObject,
    args: *const JValue,
) -> JniObject {
    type NewObject =
        unsafe extern "C" fn(*mut JniEnv, JniObject, JniObject, *const JValue) -> JniObject;
    let func: NewObject = slot_fn!(table(env), SLOT_NEW_OBJECT_A, NewObject);
    func(env, class, method, args)
}

pub unsafe fn get_object_class(env: *mut JniEnv, object: JniObject) -> JniObject {
    type GetClass = unsafe extern "C" fn(*mut JniEnv, JniObject) -> JniObject;
    let func: GetClass = slot_fn!(table(env), SLOT_GET_OBJECT_CLASS, GetClass);
    func(env, object)
}

pub unsafe fn get_method_id(
    env: *mut JniEnv,
    class: JniObject,
    name: *const c_char,
    signature: *const c_char,
) -> JniObject {
    type GetMethod =
        unsafe extern "C" fn(*mut JniEnv, JniObject, *const c_char, *const c_char) -> JniObject;
    let func: GetMethod = slot_fn!(table(env), SLOT_GET_METHOD_ID, GetMethod);
    func(env, class, name, signature)
}

pub unsafe fn get_array_length(env: *mut JniEnv, array: JniObject) -> i32 {
    type GetLength = unsafe extern "C" fn(*mut JniEnv, JniObject) -> i32;
    let func: GetLength = slot_fn!(table(env), SLOT_GET_ARRAY_LENGTH, GetLength);
    func(env, array)
}

pub unsafe fn call_object_int(
    env: *mut JniEnv,
    object: JniObject,
    method: JniObject,
    arg: i32,
) -> JniObject {
    type CallObjectA =
        unsafe extern "C" fn(*mut JniEnv, JniObject, JniObject, *const JValue) -> JniObject;
    let mut value = JValue { j: 0 };
    value.i = arg;
    let func: CallObjectA = slot_fn!(table(env), SLOT_CALL_OBJECT_METHOD_A, CallObjectA);
    func(env, object, method, &value)
}

pub unsafe fn get_static_method_id(
    env: *mut JniEnv,
    class: JniObject,
    name: *const c_char,
    signature: *const c_char,
) -> JniObject {
    type GetMethod =
        unsafe extern "C" fn(*mut JniEnv, JniObject, *const c_char, *const c_char) -> JniObject;
    let func: GetMethod = slot_fn!(table(env), SLOT_GET_STATIC_METHOD_ID, GetMethod);
    func(env, class, name, signature)
}

pub unsafe fn call_int_method_a(
    env: *mut JniEnv,
    object: JniObject,
    method: JniObject,
    args: *const JValue,
) -> i32 {
    type Call = unsafe extern "C" fn(*mut JniEnv, JniObject, JniObject, *const JValue) -> i32;
    let func: Call = slot_fn!(table(env), SLOT_CALL_INT_METHOD_A, Call);
    func(env, object, method, args)
}

pub unsafe fn call_void_method_a(
    env: *mut JniEnv,
    object: JniObject,
    method: JniObject,
    args: *const JValue,
) {
    type Call = unsafe extern "C" fn(*mut JniEnv, JniObject, JniObject, *const JValue);
    let func: Call = slot_fn!(table(env), SLOT_CALL_VOID_METHOD_A, Call);
    func(env, object, method, args);
}

pub unsafe fn call_static_void_method_a(
    env: *mut JniEnv,
    class: JniObject,
    method: JniObject,
    args: *const JValue,
) {
    type Call = unsafe extern "C" fn(*mut JniEnv, JniObject, JniObject, *const JValue);
    let func: Call = slot_fn!(table(env), SLOT_CALL_STATIC_VOID_METHOD_A, Call);
    func(env, class, method, args);
}

pub unsafe fn new_string_utf(env: *mut JniEnv, bytes: *const c_char) -> JniObject {
    type NewString = unsafe extern "C" fn(*mut JniEnv, *const c_char) -> JniObject;
    let func: NewString = slot_fn!(table(env), SLOT_NEW_STRING_UTF, NewString);
    func(env, bytes)
}

pub unsafe fn new_int_array(env: *mut JniEnv, length: i32) -> JniObject {
    type NewArray = unsafe extern "C" fn(*mut JniEnv, i32) -> JniObject;
    let func: NewArray = slot_fn!(table(env), SLOT_NEW_INT_ARRAY, NewArray);
    func(env, length)
}

pub unsafe fn new_float_array(env: *mut JniEnv, length: i32) -> JniObject {
    type NewArray = unsafe extern "C" fn(*mut JniEnv, i32) -> JniObject;
    let func: NewArray = slot_fn!(table(env), SLOT_NEW_FLOAT_ARRAY, NewArray);
    func(env, length)
}

pub unsafe fn set_int_array_region(
    env: *mut JniEnv,
    array: JniObject,
    start: i32,
    length: i32,
    buffer: *const i32,
) {
    type SetRegion = unsafe extern "C" fn(*mut JniEnv, JniObject, i32, i32, *const i32);
    let func: SetRegion = slot_fn!(table(env), SLOT_SET_INT_ARRAY_REGION, SetRegion);
    func(env, array, start, length, buffer);
}

pub unsafe fn get_float_array_elements(env: *mut JniEnv, array: JniObject) -> *mut f32 {
    type GetElements = unsafe extern "C" fn(*mut JniEnv, JniObject, *mut u8) -> *mut f32;
    let func: GetElements = slot_fn!(table(env), SLOT_GET_FLOAT_ARRAY_ELEMENTS, GetElements);
    func(env, array, ptr::null_mut())
}

pub unsafe fn release_float_array_elements(
    env: *mut JniEnv,
    array: JniObject,
    elements: *mut f32,
    mode: i32,
) {
    type Release = unsafe extern "C" fn(*mut JniEnv, JniObject, *mut f32, i32);
    let func: Release = slot_fn!(table(env), SLOT_RELEASE_FLOAT_ARRAY_ELEMENTS, Release);
    func(env, array, elements, mode);
}

pub unsafe fn get_float_array_region(
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

pub unsafe fn set_float_array_region(
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
