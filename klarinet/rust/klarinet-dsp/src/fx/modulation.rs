use std::sync::atomic::AtomicU32;

use crate::primitives::{Biquad, BiquadKind, CircularBuffer, Lfo};

use super::{load_f32, store_f32, DspEffect};

pub struct Chorus {
    rate_hz: AtomicU32,
    depth: AtomicU32,
    wet_dry: AtomicU32,
    lines: Vec<CircularBuffer>,
    lfo: Lfo,
    sample_rate: i32,
}

impl Chorus {
    pub fn new() -> Self {
        Self {
            rate_hz: AtomicU32::new(1.0_f32.to_bits()),
            depth: AtomicU32::new(0.5_f32.to_bits()),
            wet_dry: AtomicU32::new(0.5_f32.to_bits()),
            lines: Vec::new(),
            lfo: Lfo::new(),
            sample_rate: 48000,
        }
    }
}

impl DspEffect for Chorus {
    fn process(&mut self, buffer: &mut [f32], num_frames: i32, channel_count: i32) {
        let depth = load_f32(&self.depth);
        let mix = load_f32(&self.wet_dry);
        let sample_rate = self.sample_rate as f32;
        for frame in 0..num_frames {
            let lfo_val = self.lfo.next_sample();
            let delay_samples = (7.0 + depth * 7.0 * (lfo_val * 0.5 + 0.5)) * sample_rate / 1000.0;
            for ch in 0..channel_count {
                let idx = (frame * channel_count + ch) as usize;
                if idx >= buffer.len() {
                    break;
                }
                let Some(line) = self.lines.get_mut(ch as usize) else {
                    break;
                };
                let dry = buffer[idx];
                line.write(dry);
                let wet = line.read_linear(delay_samples);
                buffer[idx] = dry * (1.0 - mix) + wet * mix;
            }
        }
    }

    fn set_parameter(&mut self, param_id: i32, value: f32) {
        match param_id {
            0 => {
                store_f32(&self.rate_hz, value);
                self.lfo.set_frequency(value);
            }
            1 => store_f32(&self.depth, value),
            2 => store_f32(&self.wet_dry, value),
            _ => {}
        }
    }

    fn get_parameter(&self, param_id: i32) -> f32 {
        match param_id {
            0 => load_f32(&self.rate_hz),
            1 => load_f32(&self.depth),
            2 => load_f32(&self.wet_dry),
            _ => 0.0,
        }
    }

    fn prepare(&mut self, sample_rate: i32, channel_count: i32) {
        self.sample_rate = sample_rate;
        let max_capacity = (50.0 * sample_rate as f32 / 1000.0) as i32;
        let count = if channel_count > 0 {
            channel_count as usize
        } else {
            0
        };
        self.lines.resize(count, CircularBuffer::new(0));
        for line in &mut self.lines {
            line.set_size(max_capacity);
            line.clear();
        }
        self.lfo.prepare(sample_rate as f32);
        self.lfo.set_frequency(load_f32(&self.rate_hz));
    }

    fn reset(&mut self) {
        self.lfo.reset();
        for line in &mut self.lines {
            line.clear();
        }
        store_f32(&self.rate_hz, 1.0);
        store_f32(&self.depth, 0.5);
        store_f32(&self.wet_dry, 0.5);
    }
}

pub struct Flanger {
    rate_hz: AtomicU32,
    depth: AtomicU32,
    feedback: AtomicU32,
    wet_dry: AtomicU32,
    lines: Vec<CircularBuffer>,
    feedback_state: Vec<f32>,
    lfo: Lfo,
    sample_rate: i32,
}

impl Flanger {
    pub fn new() -> Self {
        Self {
            rate_hz: AtomicU32::new(0.5_f32.to_bits()),
            depth: AtomicU32::new(0.7_f32.to_bits()),
            feedback: AtomicU32::new(0.5_f32.to_bits()),
            wet_dry: AtomicU32::new(0.5_f32.to_bits()),
            lines: Vec::new(),
            feedback_state: Vec::new(),
            lfo: Lfo::new(),
            sample_rate: 48000,
        }
    }
}

impl DspEffect for Flanger {
    fn process(&mut self, buffer: &mut [f32], num_frames: i32, channel_count: i32) {
        let depth = load_f32(&self.depth);
        let feedback = load_f32(&self.feedback);
        let mix = load_f32(&self.wet_dry);
        let sample_rate = self.sample_rate as f32;
        for frame in 0..num_frames {
            let lfo_val = self.lfo.next_sample();
            let delay_samples = depth * 5.0 * (lfo_val * 0.5 + 0.5) * sample_rate / 1000.0;
            for ch in 0..channel_count {
                let idx = (frame * channel_count + ch) as usize;
                if idx >= buffer.len() {
                    break;
                }
                let Some(line) = self.lines.get_mut(ch as usize) else {
                    break;
                };
                let dry = buffer[idx];
                let delayed = line.read_linear(delay_samples);
                line.write(dry + delayed * feedback);
                buffer[idx] = dry * (1.0 - mix) + delayed * mix;
            }
        }
    }

    fn set_parameter(&mut self, param_id: i32, value: f32) {
        match param_id {
            0 => {
                store_f32(&self.rate_hz, value);
                self.lfo.set_frequency(value);
            }
            1 => store_f32(&self.depth, value),
            2 => store_f32(&self.feedback, value),
            3 => store_f32(&self.wet_dry, value),
            _ => {}
        }
    }

    fn get_parameter(&self, param_id: i32) -> f32 {
        match param_id {
            0 => load_f32(&self.rate_hz),
            1 => load_f32(&self.depth),
            2 => load_f32(&self.feedback),
            3 => load_f32(&self.wet_dry),
            _ => 0.0,
        }
    }

    fn prepare(&mut self, sample_rate: i32, channel_count: i32) {
        self.sample_rate = sample_rate;
        let max_capacity = (20.0 * sample_rate as f32 / 1000.0) as i32;
        let count = if channel_count > 0 {
            channel_count as usize
        } else {
            0
        };
        self.lines.resize(count, CircularBuffer::new(0));
        for line in &mut self.lines {
            line.set_size(max_capacity);
            line.clear();
        }
        self.feedback_state.resize(count, 0.0);
        self.lfo.prepare(sample_rate as f32);
        self.lfo.set_frequency(load_f32(&self.rate_hz));
    }

    fn reset(&mut self) {
        self.lfo.reset();
        for line in &mut self.lines {
            line.clear();
        }
        self.feedback_state.fill(0.0);
        store_f32(&self.rate_hz, 0.5);
        store_f32(&self.depth, 0.7);
        store_f32(&self.feedback, 0.5);
        store_f32(&self.wet_dry, 0.5);
    }
}

pub struct Phaser {
    rate_hz: AtomicU32,
    depth: AtomicU32,
    stages: AtomicU32,
    feedback: AtomicU32,
    allpasses: Vec<[Biquad; 8]>,
    feedback_state: Vec<f32>,
    lfo: Lfo,
    sample_rate: i32,
}

impl Phaser {
    pub fn new() -> Self {
        Self {
            rate_hz: AtomicU32::new(0.5_f32.to_bits()),
            depth: AtomicU32::new(0.5_f32.to_bits()),
            stages: AtomicU32::new(4.0_f32.to_bits()),
            feedback: AtomicU32::new(0.3_f32.to_bits()),
            allpasses: Vec::new(),
            feedback_state: Vec::new(),
            lfo: Lfo::new(),
            sample_rate: 48000,
        }
    }
}

impl DspEffect for Phaser {
    fn process(&mut self, buffer: &mut [f32], num_frames: i32, channel_count: i32) {
        let depth = load_f32(&self.depth);
        let feedback = load_f32(&self.feedback);
        let mut stages = load_f32(&self.stages) as i32;
        stages = stages.clamp(1, 8);
        let sample_rate = self.sample_rate as f32;
        for frame in 0..num_frames {
            let lfo_val = self.lfo.next_sample();
            let lfo_unipolar = lfo_val * 0.5 + 0.5;
            let center_freq = 200.0 + (4000.0 - 200.0) * lfo_unipolar * depth;
            for ch in 0..channel_count {
                let idx = (frame * channel_count + ch) as usize;
                if idx >= buffer.len() {
                    break;
                }
                let Some(channel) = self.allpasses.get_mut(ch as usize) else {
                    break;
                };
                let Some(state) = self.feedback_state.get_mut(ch as usize) else {
                    break;
                };
                let input = buffer[idx] + *state * feedback;
                let mut allpass_out = input;
                for stage in 0..stages {
                    let biquad = &mut channel[stage as usize];
                    biquad.configure(BiquadKind::AllPass, center_freq, 0.707, 0.0, sample_rate);
                    allpass_out = biquad.process(allpass_out);
                }
                *state = allpass_out;
                buffer[idx] = input + allpass_out;
            }
        }
    }

    fn set_parameter(&mut self, param_id: i32, value: f32) {
        match param_id {
            0 => {
                store_f32(&self.rate_hz, value);
                self.lfo.set_frequency(value);
            }
            1 => store_f32(&self.depth, value),
            2 => store_f32(&self.stages, value),
            3 => store_f32(&self.feedback, value),
            _ => {}
        }
    }

    fn get_parameter(&self, param_id: i32) -> f32 {
        match param_id {
            0 => load_f32(&self.rate_hz),
            1 => load_f32(&self.depth),
            2 => load_f32(&self.stages),
            3 => load_f32(&self.feedback),
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
        self.allpasses.resize(count, [Biquad::new(); 8]);
        for channel in &mut self.allpasses {
            for biquad in channel {
                biquad.reset();
            }
        }
        self.feedback_state.resize(count, 0.0);
        self.feedback_state.fill(0.0);
        self.lfo.prepare(sample_rate as f32);
        self.lfo.set_frequency(load_f32(&self.rate_hz));
    }

    fn reset(&mut self) {
        self.lfo.reset();
        for channel in &mut self.allpasses {
            for biquad in channel {
                biquad.reset();
            }
        }
        self.feedback_state.fill(0.0);
        store_f32(&self.rate_hz, 0.5);
        store_f32(&self.depth, 0.5);
        store_f32(&self.stages, 4.0);
        store_f32(&self.feedback, 0.3);
    }
}

pub struct Tremolo {
    rate_hz: AtomicU32,
    depth: AtomicU32,
    lfo: Lfo,
}

impl Tremolo {
    pub fn new() -> Self {
        Self {
            rate_hz: AtomicU32::new(5.0_f32.to_bits()),
            depth: AtomicU32::new(0.5_f32.to_bits()),
            lfo: Lfo::new(),
        }
    }
}

impl DspEffect for Tremolo {
    fn process(&mut self, buffer: &mut [f32], num_frames: i32, channel_count: i32) {
        let depth = load_f32(&self.depth);
        for frame in 0..num_frames {
            let lfo_val = self.lfo.next_sample();
            let modulation = (1.0 - depth) + depth * (lfo_val * 0.5 + 0.5);
            for ch in 0..channel_count {
                let index = (frame * channel_count + ch) as usize;
                if index < buffer.len() {
                    buffer[index] *= modulation;
                }
            }
        }
    }

    fn set_parameter(&mut self, param_id: i32, value: f32) {
        match param_id {
            0 => {
                store_f32(&self.rate_hz, value);
                self.lfo.set_frequency(value);
            }
            1 => store_f32(&self.depth, value),
            _ => {}
        }
    }

    fn get_parameter(&self, param_id: i32) -> f32 {
        match param_id {
            0 => load_f32(&self.rate_hz),
            1 => load_f32(&self.depth),
            _ => 0.0,
        }
    }

    fn prepare(&mut self, sample_rate: i32, _channel_count: i32) {
        self.lfo.prepare(sample_rate as f32);
        self.lfo.set_frequency(load_f32(&self.rate_hz));
    }

    fn reset(&mut self) {
        self.lfo.reset();
        store_f32(&self.rate_hz, 5.0);
        store_f32(&self.depth, 0.5);
    }
}
