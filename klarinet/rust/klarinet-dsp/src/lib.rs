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
