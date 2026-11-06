use std::sync::atomic::AtomicU32;

use crate::primitives::EnvelopeFollower;

use super::{load_f32, store_f32, DspEffect};

pub struct Compressor {
    threshold: AtomicU32,
    ratio: AtomicU32,
    attack_ms: AtomicU32,
    release_ms: AtomicU32,
    makeup_gain: AtomicU32,
    envelope: EnvelopeFollower,
    sample_rate: i32,
}

impl Compressor {
    pub fn new() -> Self {
        Self {
            threshold: AtomicU32::new((-20.0_f32).to_bits()),
            ratio: AtomicU32::new(4.0_f32.to_bits()),
            attack_ms: AtomicU32::new(10.0_f32.to_bits()),
            release_ms: AtomicU32::new(100.0_f32.to_bits()),
            makeup_gain: AtomicU32::new(0.0_f32.to_bits()),
            envelope: EnvelopeFollower::new(),
            sample_rate: 48000,
        }
    }
}

impl DspEffect for Compressor {
    fn process(&mut self, buffer: &mut [f32], num_frames: i32, channel_count: i32) {
        let thresh = load_f32(&self.threshold);
        let ratio = load_f32(&self.ratio);
        let makeup = load_f32(&self.makeup_gain);
        let makeup_linear = 10.0_f32.powf(makeup / 20.0);
        for frame in 0..num_frames {
            let mut peak = 0.0_f32;
            for ch in 0..channel_count {
                let index = (frame * channel_count + ch) as usize;
                if index >= buffer.len() {
                    break;
                }
                let abs_sample = buffer[index].abs();
                if abs_sample > peak {
                    peak = abs_sample;
                }
            }
            let env_level = self.envelope.process(peak);
            let env_level_db = if env_level > 1e-10 {
                20.0 * env_level.log10()
            } else {
                -200.0
            };
            let mut gain_linear = 1.0_f32;
            if env_level_db > thresh {
                let over_db = env_level_db - thresh;
                let compressed_db = thresh + over_db / ratio;
                let gain_reduction_db = compressed_db - env_level_db;
                gain_linear = 10.0_f32.powf(gain_reduction_db / 20.0);
            }
            for ch in 0..channel_count {
                let index = (frame * channel_count + ch) as usize;
                if index < buffer.len() {
                    buffer[index] *= gain_linear * makeup_linear;
                }
            }
        }
    }

    fn set_parameter(&mut self, param_id: i32, value: f32) {
        match param_id {
            0 => store_f32(&self.threshold, value),
            1 => store_f32(&self.ratio, value.max(1.0)),
            2 => {
                store_f32(&self.attack_ms, value);
                self.envelope.set_attack_ms(value);
            }
            3 => {
                store_f32(&self.release_ms, value);
                self.envelope.set_release_ms(value);
            }
            4 => store_f32(&self.makeup_gain, value),
            _ => {}
        }
    }

    fn get_parameter(&self, param_id: i32) -> f32 {
        match param_id {
            0 => load_f32(&self.threshold),
            1 => load_f32(&self.ratio),
            2 => load_f32(&self.attack_ms),
            3 => load_f32(&self.release_ms),
            4 => load_f32(&self.makeup_gain),
            _ => 0.0,
        }
    }

    fn prepare(&mut self, sample_rate: i32, _channel_count: i32) {
        self.sample_rate = sample_rate;
        self.envelope.prepare(sample_rate as f32);
        self.envelope.set_attack_ms(load_f32(&self.attack_ms));
        self.envelope.set_release_ms(load_f32(&self.release_ms));
    }

    fn reset(&mut self) {
        self.envelope.reset();
    }
}

pub struct Limiter {
    threshold: AtomicU32,
    release_ms: AtomicU32,
    envelope: EnvelopeFollower,
}

impl Limiter {
    pub fn new() -> Self {
        Self {
            threshold: AtomicU32::new((-1.0_f32).to_bits()),
            release_ms: AtomicU32::new(50.0_f32.to_bits()),
            envelope: EnvelopeFollower::new(),
        }
    }
}

impl DspEffect for Limiter {
    fn process(&mut self, buffer: &mut [f32], num_frames: i32, channel_count: i32) {
        let thresh_db = load_f32(&self.threshold);
        let thresh_linear = 10.0_f32.powf(thresh_db / 20.0);
        for frame in 0..num_frames {
            let mut peak = 0.0_f32;
            for ch in 0..channel_count {
                let index = (frame * channel_count + ch) as usize;
                if index >= buffer.len() {
                    break;
                }
                let abs_sample = buffer[index].abs();
                if abs_sample > peak {
                    peak = abs_sample;
                }
            }
            let env_level = self.envelope.process(peak);
            let mut gain_linear = 1.0_f32;
            if env_level > thresh_linear {
                gain_linear = thresh_linear / env_level;
            }
            for ch in 0..channel_count {
                let index = (frame * channel_count + ch) as usize;
                if index < buffer.len() {
                    buffer[index] *= gain_linear;
                }
            }
        }
    }

    fn set_parameter(&mut self, param_id: i32, value: f32) {
        match param_id {
            0 => store_f32(&self.threshold, value),
            1 => {
                store_f32(&self.release_ms, value);
                self.envelope.set_release_ms(value);
            }
            _ => {}
        }
    }

    fn get_parameter(&self, param_id: i32) -> f32 {
        match param_id {
            0 => load_f32(&self.threshold),
            1 => load_f32(&self.release_ms),
            _ => 0.0,
        }
    }

    fn prepare(&mut self, sample_rate: i32, _channel_count: i32) {
        self.envelope.prepare(sample_rate as f32);
        self.envelope.set_attack_ms(0.1);
        self.envelope.set_release_ms(load_f32(&self.release_ms));
    }

    fn reset(&mut self) {
        self.envelope.reset();
    }
}

pub struct NoiseGate {
    threshold: AtomicU32,
    attack_ms: AtomicU32,
    release_ms: AtomicU32,
    hold_ms: AtomicU32,
    envelope: EnvelopeFollower,
    sample_rate: i32,
    gate_gain: f32,
    hold_samples: i32,
    hold_counter: i32,
}

impl NoiseGate {
    pub fn new() -> Self {
        Self {
            threshold: AtomicU32::new((-40.0_f32).to_bits()),
            attack_ms: AtomicU32::new(1.0_f32.to_bits()),
            release_ms: AtomicU32::new(50.0_f32.to_bits()),
            hold_ms: AtomicU32::new(10.0_f32.to_bits()),
            envelope: EnvelopeFollower::new(),
            sample_rate: 48000,
            gate_gain: 0.0,
            hold_samples: 0,
            hold_counter: 0,
        }
    }
}

impl DspEffect for NoiseGate {
    fn process(&mut self, buffer: &mut [f32], num_frames: i32, channel_count: i32) {
        let thresh_db = load_f32(&self.threshold);
        let thresh_linear = 10.0_f32.powf(thresh_db / 20.0);
        for frame in 0..num_frames {
            let mut peak = 0.0_f32;
            for ch in 0..channel_count {
                let index = (frame * channel_count + ch) as usize;
                if index >= buffer.len() {
                    break;
                }
                let abs_sample = buffer[index].abs();
                if abs_sample > peak {
                    peak = abs_sample;
                }
            }
            let env_level = self.envelope.process(peak);
            if env_level > thresh_linear {
                self.gate_gain = 1.0;
                self.hold_counter = self.hold_samples;
            } else if self.hold_counter > 0 {
                self.hold_counter -= 1;
                self.gate_gain = 1.0;
            } else {
                self.gate_gain *= 0.999;
            }
            for ch in 0..channel_count {
                let index = (frame * channel_count + ch) as usize;
                if index < buffer.len() {
                    buffer[index] *= self.gate_gain;
                }
            }
        }
    }

    fn set_parameter(&mut self, param_id: i32, value: f32) {
        match param_id {
            0 => store_f32(&self.threshold, value),
            1 => {
                store_f32(&self.attack_ms, value);
                self.envelope.set_attack_ms(value);
            }
            2 => {
                store_f32(&self.release_ms, value);
                self.envelope.set_release_ms(value);
            }
            3 => {
                store_f32(&self.hold_ms, value);
                self.hold_samples = (value * self.sample_rate as f32 / 1000.0) as i32;
            }
            _ => {}
        }
    }

    fn get_parameter(&self, param_id: i32) -> f32 {
        match param_id {
            0 => load_f32(&self.threshold),
            1 => load_f32(&self.attack_ms),
            2 => load_f32(&self.release_ms),
            3 => load_f32(&self.hold_ms),
            _ => 0.0,
        }
    }

    fn prepare(&mut self, sample_rate: i32, _channel_count: i32) {
        self.sample_rate = sample_rate;
        self.envelope.prepare(sample_rate as f32);
        self.envelope.set_attack_ms(load_f32(&self.attack_ms));
        self.envelope.set_release_ms(load_f32(&self.release_ms));
        self.hold_samples = (load_f32(&self.hold_ms) * sample_rate as f32 / 1000.0) as i32;
    }

    fn reset(&mut self) {
        self.envelope.reset();
        self.gate_gain = 0.0;
        self.hold_counter = 0;
    }
}
