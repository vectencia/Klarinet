//! JVM JNI cdylib.
//!
//! The audio callback is `device::audio_callback`. It does not allocate, lock,
//! panic, or enter the JVM. `klarinet_offload_process` moves samples on the
//! lock-free FIFO. Effect processing stays on that callback. JVM entry stays
//! on the offload worker.

mod analysis;
mod device;
mod effects;
mod ffi;
mod files;
mod jni;

#[cfg(feature = "dsp-effects")]
#[used]
static KLARINET_DSP_LINK: fn() = klarinet_dsp_core::link_effects;

#[used]
static KLARINET_ANALYSIS_LINK: fn() = klarinet_dsp_core::link_analysis;
