//! Biquad, delay line, LFO, and envelope follower.
//!
//! Coefficient and smoothing math matches the C++ primitives. `process` and
//! `next_sample` do not allocate.

mod biquad;
mod circular;
mod envelope;
mod lfo;

pub use biquad::{Biquad, BiquadKind};
pub use circular::CircularBuffer;
pub use envelope::EnvelopeFollower;
pub use lfo::Lfo;
