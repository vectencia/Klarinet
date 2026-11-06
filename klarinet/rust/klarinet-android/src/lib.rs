//! Android JNI staticlib. Oboe stays in the C++ adapter.
//!
//! `on_audio` is the Oboe data callback. It does not allocate, lock, panic, or
//! enter the JVM. Samples move through `klarinet_fifo_*`. Effect processing
//! stays on that callback via `klarinet_chain_process`. The worker and the
//! routing callback may enter the JVM. They do not take a lock the data
//! callback needs.

mod analysis;
mod audio;
mod bridge;
mod dsp;
mod engine;
mod jni;
mod oboe;

use std::sync::{Mutex, MutexGuard};

#[used]
pub(crate) static KLARINET_DSP_EFFECTS: fn() = klarinet_dsp_core::link_effects;

#[used]
pub(crate) static KLARINET_DSP_QUEUES: fn() = klarinet_dsp_core::link_queues;

#[used]
pub(crate) static KLARINET_DSP_ANALYSIS: fn() = klarinet_dsp_core::link_analysis;

pub(crate) fn lock<T>(mutex: &Mutex<T>) -> MutexGuard<'_, T> {
    mutex.lock().unwrap_or_else(|err| err.into_inner())
}

pub(crate) fn usize_to_i64(value: usize) -> i64 {
    value as i64
}

pub(crate) fn i64_to_usize(value: i64) -> usize {
    value as usize
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pointer_bits_round_trip() {
        let ptr = usize::MAX;
        assert_eq!(i64_to_usize(usize_to_i64(ptr)), ptr);
    }
}
