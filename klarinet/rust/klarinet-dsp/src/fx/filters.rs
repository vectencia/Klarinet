use std::sync::atomic::{AtomicBool, AtomicU32, Ordering};

use crate::primitives::{Biquad, BiquadKind};

use super::{load_f32, store_f32, DspEffect};

struct FilterCore {
    cutoff: AtomicU32,
    resonance: AtomicU32,
    dirty: AtomicBool,
    biquads: Vec<Biquad>,
    sample_rate: i32,
    kind: BiquadKind,
}

impl FilterCore {
    fn new(cutoff: f32, kind: BiquadKind) -> Self {
        Self {
            cutoff: AtomicU32::new(cutoff.to_bits()),
            resonance: AtomicU32::new(0.707_f32.to_bits()),
            dirty: AtomicBool::new(true),
            biquads: Vec::new(),
            sample_rate: 48000,
            kind,
        }
    }

    fn process(&mut self, buffer: &mut [f32], num_frames: i32, channel_count: i32) {
        if self.dirty.swap(false, Ordering::Relaxed) {
            let cutoff = load_f32(&self.cutoff);
            let q = load_f32(&self.resonance);
            let rate = self.sample_rate as f32;
            let kind = self.kind;
            for biquad in &mut self.biquads {
                biquad.configure(kind, cutoff, q, 0.0, rate);
            }
        }
        if channel_count <= 0 {
            return;
        }
        let total = (num_frames as usize).saturating_mul(channel_count as usize);
        let total = total.min(buffer.len());
        let channels = channel_count as usize;
        for (i, sample) in buffer.iter_mut().enumerate().take(total) {
            let ch = i % channels;
            if let Some(biquad) = self.biquads.get_mut(ch) {
                *sample = biquad.process(*sample);
            }
        }
    }

    fn set_parameter(&mut self, param_id: i32, value: f32) {
        match param_id {
            0 => {
                store_f32(&self.cutoff, value);
                self.dirty.store(true, Ordering::Relaxed);
            }
            1 => {
                store_f32(&self.resonance, value);
                self.dirty.store(true, Ordering::Relaxed);
            }
            _ => {}
        }
    }

    fn get_parameter(&self, param_id: i32) -> f32 {
        match param_id {
            0 => load_f32(&self.cutoff),
            1 => load_f32(&self.resonance),
            _ => 0.0,
        }
    }

    fn prepare(&mut self, sample_rate: i32, channel_count: i32) {
        self.sample_rate = sample_rate;
        let count = if channel_count > 0 {
            channel_count as usize
        } else {
            0
        };
        self.biquads.resize(count, Biquad::new());
        self.dirty.store(true, Ordering::Relaxed);
    }

    fn reset(&mut self) {
        store_f32(&self.cutoff, 20000.0);
        store_f32(&self.resonance, 0.707);
        for biquad in &mut self.biquads {
            biquad.reset();
        }
        self.dirty.store(true, Ordering::Relaxed);
    }
}

pub struct LowPass(FilterCore);
pub struct HighPass(FilterCore);

impl LowPass {
    pub fn new() -> Self {
        Self(FilterCore::new(20000.0, BiquadKind::LowPass))
    }
}

impl HighPass {
    pub fn new() -> Self {
        Self(FilterCore::new(20000.0, BiquadKind::HighPass))
    }
}

impl DspEffect for LowPass {
    fn process(&mut self, buffer: &mut [f32], num_frames: i32, channel_count: i32) {
        self.0.process(buffer, num_frames, channel_count);
    }
    fn set_parameter(&mut self, param_id: i32, value: f32) {
        self.0.set_parameter(param_id, value);
    }
    fn get_parameter(&self, param_id: i32) -> f32 {
        self.0.get_parameter(param_id)
    }
    fn prepare(&mut self, sample_rate: i32, channel_count: i32) {
        self.0.prepare(sample_rate, channel_count);
    }
    fn reset(&mut self) {
        self.0.reset();
    }
}

impl DspEffect for HighPass {
    fn process(&mut self, buffer: &mut [f32], num_frames: i32, channel_count: i32) {
        self.0.process(buffer, num_frames, channel_count);
    }
    fn set_parameter(&mut self, param_id: i32, value: f32) {
        self.0.set_parameter(param_id, value);
    }
    fn get_parameter(&self, param_id: i32) -> f32 {
        self.0.get_parameter(param_id)
    }
    fn prepare(&mut self, sample_rate: i32, channel_count: i32) {
        self.0.prepare(sample_rate, channel_count);
    }
    fn reset(&mut self) {
        self.0.reset();
    }
}

pub struct BandPass {
    center: AtomicU32,
    bandwidth: AtomicU32,
    dirty: AtomicBool,
    biquads: Vec<Biquad>,
    sample_rate: i32,
}

impl BandPass {
    pub fn new() -> Self {
        Self {
            center: AtomicU32::new(1000.0_f32.to_bits()),
            bandwidth: AtomicU32::new(1.0_f32.to_bits()),
            dirty: AtomicBool::new(true),
            biquads: Vec::new(),
            sample_rate: 48000,
        }
    }
}

fn bandwidth_octaves_to_q(octaves: f32) -> f32 {
    let bw = octaves.max(0.001);
    let sinh_arg = 2.0_f32.ln() * 0.5 * bw;
    1.0 / (2.0 * sinh_arg.sinh().max(0.001))
}

impl DspEffect for BandPass {
    fn process(&mut self, buffer: &mut [f32], num_frames: i32, channel_count: i32) {
        if self.dirty.swap(false, Ordering::Relaxed) {
            let center = load_f32(&self.center);
            let bw = load_f32(&self.bandwidth);
            let q = bandwidth_octaves_to_q(bw);
            let rate = self.sample_rate as f32;
            for biquad in &mut self.biquads {
                biquad.configure(BiquadKind::BandPass, center, q, 0.0, rate);
            }
        }
        if channel_count <= 0 {
            return;
        }
        let total = (num_frames as usize).saturating_mul(channel_count as usize);
        let total = total.min(buffer.len());
        let channels = channel_count as usize;
        for (i, sample) in buffer.iter_mut().enumerate().take(total) {
            let ch = i % channels;
            if let Some(biquad) = self.biquads.get_mut(ch) {
                *sample = biquad.process(*sample);
            }
        }
    }

    fn set_parameter(&mut self, param_id: i32, value: f32) {
        match param_id {
            0 => {
                store_f32(&self.center, value);
                self.dirty.store(true, Ordering::Relaxed);
            }
            1 => {
                store_f32(&self.bandwidth, value);
                self.dirty.store(true, Ordering::Relaxed);
            }
            _ => {}
        }
    }

    fn get_parameter(&self, param_id: i32) -> f32 {
        match param_id {
            0 => load_f32(&self.center),
            1 => load_f32(&self.bandwidth),
            _ => 0.0,
        }
    }

    fn prepare(&mut self, sample_rate: i32, channel_count: i32) {
        self.sample_rate = sample_rate;
        let count = if channel_count > 0 {
            channel_count as usize
        } else {
            0
        };
        self.biquads.resize(count, Biquad::new());
        self.dirty.store(true, Ordering::Relaxed);
    }

    fn reset(&mut self) {
        store_f32(&self.center, 1000.0);
        store_f32(&self.bandwidth, 1.0);
        for biquad in &mut self.biquads {
            biquad.reset();
        }
        self.dirty.store(true, Ordering::Relaxed);
    }
}

struct EqBand {
    frequency: AtomicU32,
    gain: AtomicU32,
    q: AtomicU32,
    kind: AtomicU32,
    dirty: AtomicBool,
    biquads: Vec<Biquad>,
}

impl EqBand {
    fn new() -> Self {
        Self {
            frequency: AtomicU32::new(1000.0_f32.to_bits()),
            gain: AtomicU32::new(0.0_f32.to_bits()),
            q: AtomicU32::new(0.707_f32.to_bits()),
            kind: AtomicU32::new((BiquadKind::Peak as i32 as f32).to_bits()),
            dirty: AtomicBool::new(true),
            biquads: Vec::new(),
        }
    }
}

pub struct ParametricEq {
    bands: [EqBand; 8],
    sample_rate: i32,
}

impl ParametricEq {
    pub fn new() -> Self {
        Self {
            bands: [
                EqBand::new(),
                EqBand::new(),
                EqBand::new(),
                EqBand::new(),
                EqBand::new(),
                EqBand::new(),
                EqBand::new(),
                EqBand::new(),
            ],
            sample_rate: 48000,
        }
    }
}

impl DspEffect for ParametricEq {
    fn process(&mut self, buffer: &mut [f32], num_frames: i32, channel_count: i32) {
        for band in &mut self.bands {
            if band.dirty.swap(false, Ordering::Relaxed) {
                let freq = load_f32(&band.frequency);
                let gain = load_f32(&band.gain);
                let q = load_f32(&band.q);
                let kind_bits = load_f32(&band.kind) as i32;
                if let Some(kind) = BiquadKind::from_i32(kind_bits) {
                    let rate = self.sample_rate as f32;
                    for biquad in &mut band.biquads {
                        biquad.configure(kind, freq, q, gain, rate);
                    }
                }
            }
        }
        if channel_count <= 0 {
            return;
        }
        let total = (num_frames as usize).saturating_mul(channel_count as usize);
        let total = total.min(buffer.len());
        let channels = channel_count as usize;
        for band in &mut self.bands {
            let gain = load_f32(&band.gain);
            let kind = BiquadKind::from_i32(load_f32(&band.kind) as i32);
            if kind == Some(BiquadKind::Peak) && gain == 0.0 {
                continue;
            }
            for (i, sample) in buffer.iter_mut().enumerate().take(total) {
                let ch = i % channels;
                if let Some(biquad) = band.biquads.get_mut(ch) {
                    *sample = biquad.process(*sample);
                }
            }
        }
    }

    fn set_parameter(&mut self, param_id: i32, value: f32) {
        if param_id < 0 {
            return;
        }
        let band_index = param_id / 4;
        let offset = param_id % 4;
        if !(0..8).contains(&band_index) {
            return;
        }
        let band = &mut self.bands[band_index as usize];
        match offset {
            0 => store_f32(&band.frequency, value),
            1 => store_f32(&band.gain, value),
            2 => store_f32(&band.q, value),
            3 => store_f32(&band.kind, value),
            _ => return,
        }
        band.dirty.store(true, Ordering::Relaxed);
    }

    fn get_parameter(&self, param_id: i32) -> f32 {
        if param_id < 0 {
            return 0.0;
        }
        let band_index = param_id / 4;
        let offset = param_id % 4;
        if !(0..8).contains(&band_index) {
            return 0.0;
        }
        let band = &self.bands[band_index as usize];
        match offset {
            0 => load_f32(&band.frequency),
            1 => load_f32(&band.gain),
            2 => load_f32(&band.q),
            3 => load_f32(&band.kind),
            _ => 0.0,
        }
    }

    fn prepare(&mut self, sample_rate: i32, channel_count: i32) {
        self.sample_rate = sample_rate;
        let count = if channel_count > 0 {
            channel_count as usize
        } else {
            0
        };
        for band in &mut self.bands {
            band.biquads.resize(count, Biquad::new());
            band.dirty.store(true, Ordering::Relaxed);
        }
    }

    fn reset(&mut self) {
        for band in &mut self.bands {
            store_f32(&band.frequency, 1000.0);
            store_f32(&band.gain, 0.0);
            store_f32(&band.q, 0.707);
            store_f32(&band.kind, BiquadKind::Peak as i32 as f32);
            for biquad in &mut band.biquads {
                biquad.reset();
            }
            band.dirty.store(true, Ordering::Relaxed);
        }
    }
}
