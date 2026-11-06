//! Klarinet DSP core.
//!
//! The `queues` feature exports the FIFO, ring, and offload C ABI.
//! `klarinet_offload_process` runs on the audio thread. It pushes or pops the
//! FIFO, counts xruns, and wakes the worker. It does not allocate, lock, panic,
//! or call the user callback.
//!
//! The `effects` feature exports primitives, the sixteen effects, and the
//! effect-chain hot swap. `klarinet_echain_process` does not allocate, lock,
//! or panic. It frees one retired effect list, drains the parameter ring, and
//! calls each effect.

#[cfg(feature = "queues")]
mod fifo;
#[cfg(feature = "queues")]
mod offload;
#[cfg(any(feature = "queues", feature = "effects"))]
mod ring;

#[cfg(feature = "effects")]
mod abi;
#[cfg(feature = "analysis")]
mod analysis;
#[cfg(feature = "effects")]
mod chain;
#[cfg(feature = "effects")]
mod fx;
#[cfg(feature = "effects")]
mod primitives;

/// Keeps the effects C ABI in a downstream cdylib.
///
/// The JVM crate links this rlib and the C++ shims. Those shims call the
/// `klarinet_fx_*` and `klarinet_echain_*` symbols. Referencing this function
/// from the cdylib stops rustc from dropping the exports before the linker
/// sees the C++ references.
#[cfg(feature = "effects")]
pub fn link_effects() {
    abi::keep_exports();
}

/// Keeps the FIFO C ABI in a downstream staticlib.
///
/// Ring and offload are not part of this set. Android calls `klarinet_fifo_*`
/// only, and the C++ ring in `EffectChain` stays compiled out of that path.
#[cfg(feature = "queues")]
pub fn link_queues() {
    fifo::keep_exports();
}

/// Keeps the analysis C ABI in a downstream cdylib.
///
/// The JVM crate calls `klarinet_fft_forward`, `klarinet_math_*`,
/// `klarinet_resample_linear`, and `klarinet_analyzer_*` through `extern "C"`.
/// Referencing this function stops rustc from dropping those exports.
#[cfg(feature = "analysis")]
pub fn link_analysis() {
    analysis::keep_exports();
}

/// Keeps the offload C ABI in a downstream staticlib.
///
/// The linuxX64 native crate calls `klarinet_offload_*` through `extern "C"`.
/// Referencing the exports here stops rustc from dropping them.
#[cfg(feature = "queues")]
pub fn link_offload() {
    offload::keep_exports();
}

/// # Safety
/// `samples` covers `frames` floats. `phase` is a writable radian. Audio thread:
/// no allocation, no lock.
#[no_mangle]
pub unsafe extern "C" fn klarinet_render_sine(
    samples: *mut f32,
    frames: i32,
    phase: *mut f32,
    frequency_hz: f32,
    sample_rate: i32,
    amplitude: f32,
) {
    if samples.is_null() || phase.is_null() || frames <= 0 || sample_rate <= 0 {
        return;
    }
    let two_pi = std::f32::consts::PI * 2.0;
    let inc = two_pi * frequency_hz / sample_rate as f32;
    let mut current = unsafe { *phase };
    let out = unsafe { std::slice::from_raw_parts_mut(samples, frames as usize) };
    for sample in out.iter_mut() {
        *sample = current.sin() * amplitude;
        current += inc;
        if current > two_pi {
            current -= two_pi;
        }
    }
    unsafe { *phase = current };
}

#[cfg(test)]
mod sine_tests {
    use super::klarinet_render_sine;

    #[test]
    fn render_sine_matches_frequency_rate_and_amplitude() {
        let sample_rate = 4_000;
        let frequency = 1_000.0f32;
        let amplitude = 0.5f32;
        let frames = 16;
        let mut buffer = [0.0f32; 16];
        let mut phase = 0.0f32;
        unsafe {
            klarinet_render_sine(
                buffer.as_mut_ptr(),
                frames,
                &mut phase,
                frequency,
                sample_rate,
                amplitude,
            );
        }
        let two_pi = std::f32::consts::PI * 2.0;
        let inc = two_pi * frequency / sample_rate as f32;
        let mut expected = 0.0f32;
        for (index, sample) in buffer.iter().enumerate() {
            let want = expected.sin() * amplitude;
            assert!(
                (sample - want).abs() < 1.0e-5,
                "index {index} got {sample} want {want}"
            );
            expected += inc;
            if expected > two_pi {
                expected -= two_pi;
            }
        }
        assert!((phase - expected).abs() < 1.0e-5);
        assert!(frames > 4, "the buffer must cross the 2π wrap");
    }
}
