use std::sync::atomic::AtomicU32;

use crate::primitives::CircularBuffer;

use super::{load_f32, store_f32, DspEffect};

const MAX_DELAY_SECONDS: f32 = 2.0;
const MAX_FEEDBACK: f32 = 0.95;

pub struct Delay {
    time_ms: AtomicU32,
    feedback: AtomicU32,
    wet_dry: AtomicU32,
    lines: Vec<CircularBuffer>,
    sample_rate: i32,
}

impl Delay {
    pub fn new() -> Self {
        Self {
            time_ms: AtomicU32::new(250.0_f32.to_bits()),
            feedback: AtomicU32::new(0.4_f32.to_bits()),
            wet_dry: AtomicU32::new(0.3_f32.to_bits()),
            lines: Vec::new(),
            sample_rate: 48000,
        }
    }
}

impl DspEffect for Delay {
    fn process(&mut self, buffer: &mut [f32], num_frames: i32, channel_count: i32) {
        let delay_samples = load_f32(&self.time_ms) * 0.001 * self.sample_rate as f32;
        let feedback = load_f32(&self.feedback).clamp(0.0, MAX_FEEDBACK);
        let mix = load_f32(&self.wet_dry).clamp(0.0, 1.0);
        let num_channels = channel_count.min(self.lines.len() as i32);
        for frame in 0..num_frames {
            for ch in 0..num_channels {
                let idx = (frame * channel_count + ch) as usize;
                if idx >= buffer.len() {
                    break;
                }
                let dry = buffer[idx];
                let delayed = self.lines[ch as usize].read_linear(delay_samples);
                self.lines[ch as usize].write(dry + delayed * feedback);
                buffer[idx] = dry * (1.0 - mix) + delayed * mix;
            }
        }
    }

    fn set_parameter(&mut self, param_id: i32, value: f32) {
        match param_id {
            0 => store_f32(&self.time_ms, value),
            1 => store_f32(&self.feedback, value),
            2 => store_f32(&self.wet_dry, value),
            _ => {}
        }
    }

    fn get_parameter(&self, param_id: i32) -> f32 {
        match param_id {
            0 => load_f32(&self.time_ms),
            1 => load_f32(&self.feedback),
            2 => load_f32(&self.wet_dry),
            _ => 0.0,
        }
    }

    fn prepare(&mut self, sample_rate: i32, channel_count: i32) {
        self.sample_rate = sample_rate;
        let max_samples = (MAX_DELAY_SECONDS * sample_rate as f32) as i32 + 1;
        self.lines.clear();
        let count = if channel_count > 0 { channel_count } else { 0 };
        self.lines.reserve(count as usize);
        for _ in 0..count {
            self.lines.push(CircularBuffer::new(max_samples));
        }
    }

    fn reset(&mut self) {
        for line in &mut self.lines {
            line.clear();
        }
    }
}
