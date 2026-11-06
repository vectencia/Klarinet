//! Sixteen effects. `process` loads atomics and does not allocate.
//! Delay lines are allocated in `prepare`.

mod delay;
mod dynamics;
mod filters;
mod gain;
mod modulation;
mod reverb;

pub use delay::Delay;
pub use dynamics::{Compressor, Limiter, NoiseGate};
pub use filters::{BandPass, HighPass, LowPass, ParametricEq};
pub use gain::{Gain, MuteSolo, Pan};
pub use modulation::{Chorus, Flanger, Phaser, Tremolo};
pub use reverb::Reverb;

use std::sync::atomic::{AtomicU32, Ordering};

pub fn load_f32(atom: &AtomicU32) -> f32 {
    f32::from_bits(atom.load(Ordering::Relaxed))
}

pub fn store_f32(atom: &AtomicU32, value: f32) {
    atom.store(value.to_bits(), Ordering::Relaxed);
}

pub trait DspEffect {
    fn process(&mut self, buffer: &mut [f32], num_frames: i32, channel_count: i32);
    fn set_parameter(&mut self, param_id: i32, value: f32);
    fn get_parameter(&self, param_id: i32) -> f32;
    fn prepare(&mut self, sample_rate: i32, channel_count: i32);
    fn reset(&mut self);
}

pub enum Effect {
    Gain(Box<Gain>),
    Pan(Box<Pan>),
    MuteSolo(Box<MuteSolo>),
    Compressor(Box<Compressor>),
    Limiter(Box<Limiter>),
    NoiseGate(Box<NoiseGate>),
    ParametricEq(Box<ParametricEq>),
    LowPass(Box<LowPass>),
    HighPass(Box<HighPass>),
    BandPass(Box<BandPass>),
    Delay(Box<Delay>),
    Reverb(Box<Reverb>),
    Chorus(Box<Chorus>),
    Flanger(Box<Flanger>),
    Phaser(Box<Phaser>),
    Tremolo(Box<Tremolo>),
}

impl Effect {
    pub fn new(effect_type: i32) -> Option<Self> {
        match effect_type {
            0 => Some(Self::Gain(Box::new(Gain::new()))),
            1 => Some(Self::Pan(Box::new(Pan::new()))),
            2 => Some(Self::MuteSolo(Box::new(MuteSolo::new()))),
            3 => Some(Self::Compressor(Box::new(Compressor::new()))),
            4 => Some(Self::Limiter(Box::new(Limiter::new()))),
            5 => Some(Self::NoiseGate(Box::new(NoiseGate::new()))),
            6 => Some(Self::ParametricEq(Box::new(ParametricEq::new()))),
            7 => Some(Self::LowPass(Box::new(LowPass::new()))),
            8 => Some(Self::HighPass(Box::new(HighPass::new()))),
            9 => Some(Self::BandPass(Box::new(BandPass::new()))),
            10 => Some(Self::Delay(Box::new(Delay::new()))),
            11 => Some(Self::Reverb(Box::new(Reverb::new()))),
            12 => Some(Self::Chorus(Box::new(Chorus::new()))),
            13 => Some(Self::Flanger(Box::new(Flanger::new()))),
            14 => Some(Self::Phaser(Box::new(Phaser::new()))),
            15 => Some(Self::Tremolo(Box::new(Tremolo::new()))),
            _ => None,
        }
    }
}

macro_rules! dispatch {
    ($self:ident, $method:ident ( $($arg:expr),* )) => {
        match $self {
            Effect::Gain(effect) => effect.$method($($arg),*),
            Effect::Pan(effect) => effect.$method($($arg),*),
            Effect::MuteSolo(effect) => effect.$method($($arg),*),
            Effect::Compressor(effect) => effect.$method($($arg),*),
            Effect::Limiter(effect) => effect.$method($($arg),*),
            Effect::NoiseGate(effect) => effect.$method($($arg),*),
            Effect::ParametricEq(effect) => effect.$method($($arg),*),
            Effect::LowPass(effect) => effect.$method($($arg),*),
            Effect::HighPass(effect) => effect.$method($($arg),*),
            Effect::BandPass(effect) => effect.$method($($arg),*),
            Effect::Delay(effect) => effect.$method($($arg),*),
            Effect::Reverb(effect) => effect.$method($($arg),*),
            Effect::Chorus(effect) => effect.$method($($arg),*),
            Effect::Flanger(effect) => effect.$method($($arg),*),
            Effect::Phaser(effect) => effect.$method($($arg),*),
            Effect::Tremolo(effect) => effect.$method($($arg),*),
        }
    };
}

impl DspEffect for Effect {
    fn process(&mut self, buffer: &mut [f32], num_frames: i32, channel_count: i32) {
        dispatch!(self, process(buffer, num_frames, channel_count));
    }

    fn set_parameter(&mut self, param_id: i32, value: f32) {
        dispatch!(self, set_parameter(param_id, value));
    }

    fn get_parameter(&self, param_id: i32) -> f32 {
        dispatch!(self, get_parameter(param_id))
    }

    fn prepare(&mut self, sample_rate: i32, channel_count: i32) {
        dispatch!(self, prepare(sample_rate, channel_count));
    }

    fn reset(&mut self) {
        dispatch!(self, reset());
    }
}
