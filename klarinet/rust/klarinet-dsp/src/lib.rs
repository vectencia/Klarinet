//! Queue and offload bodies behind the Klarinet C ABI.
//!
//! `klarinet_offload_process` runs on the audio thread. It pushes or pops the
//! FIFO, counts xruns, and wakes the worker. It does not allocate, lock, panic,
//! or call the user callback.

mod fifo;
mod offload;
mod ring;
