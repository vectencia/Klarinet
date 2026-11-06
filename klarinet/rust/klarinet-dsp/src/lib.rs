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
