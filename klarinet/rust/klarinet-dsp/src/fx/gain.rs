use std::sync::atomic::{AtomicU32, Ordering};

use super::{load_f32, store_f32, DspEffect};

pub struct Gain {
    gain_db: AtomicU32,
    target_linear: AtomicU32,
    fade_ms: AtomicU32,
    generation: AtomicU32,
    current_linear: f32,
    ramp_start_linear: f32,
    ramp_target_linear: f32,
    ramp_position: i32,
    ramp_length: i32,
    seen_generation: u32,
    sample_rate: i32,
}

impl Gain {
    pub fn new() -> Self {
        Self {
            gain_db: AtomicU32::new(0.0_f32.to_bits()),
            target_linear: AtomicU32::new(1.0_f32.to_bits()),
            fade_ms: AtomicU32::new(0.0_f32.to_bits()),
            generation: AtomicU32::new(0),
            current_linear: 1.0,
            ramp_start_linear: 1.0,
            ramp_target_linear: 1.0,
            ramp_position: 0,
            ramp_length: 0,
            seen_generation: 0,
            sample_rate: 48000,
        }
    }
}

impl DspEffect for Gain {
    fn process(&mut self, buffer: &mut [f32], num_frames: i32, channel_count: i32) {
        if num_frames <= 0 || channel_count <= 0 {
            return;
        }
        let gen = self.generation.load(Ordering::Acquire);
        if gen != self.seen_generation {
            self.seen_generation = gen;
            let fade_ms = load_f32(&self.fade_ms);
            self.ramp_start_linear = self.current_linear;
            self.ramp_target_linear = load_f32(&self.target_linear);
            self.ramp_length = fade_length_frames(fade_ms, self.sample_rate);
            if self.ramp_length < 1 {
                self.ramp_length = 0;
                self.ramp_position = 0;
                self.current_linear = self.ramp_target_linear;
            } else {
                self.ramp_position = 0;
            }
        }
        if self.ramp_length <= 0 || self.ramp_position >= self.ramp_length {
            let linear = self.current_linear;
            let total = (num_frames as usize).saturating_mul(channel_count as usize);
            let count = total.min(buffer.len());
            for sample in &mut buffer[..count] {
                *sample *= linear;
            }
            return;
        }
        for frame in 0..num_frames {
            let linear = if self.ramp_position >= self.ramp_length {
                self.ramp_target_linear
            } else {
                self.ramp_position += 1;
                let t = self.ramp_position as f32 / self.ramp_length as f32;
                self.ramp_start_linear + (self.ramp_target_linear - self.ramp_start_linear) * t
            };
            self.current_linear = linear;
            let base = frame * channel_count;
            for ch in 0..channel_count {
                let index = (base + ch) as usize;
                if index < buffer.len() {
                    buffer[index] *= linear;
                }
            }
        }
    }

    fn set_parameter(&mut self, param_id: i32, value: f32) {
        if param_id == 0 {
            store_f32(&self.gain_db, value);
            store_f32(&self.target_linear, db_to_linear(value));
            self.generation.fetch_add(1, Ordering::Release);
        } else if param_id == 1 {
            let ms = if value > 0.0 { value } else { 0.0 };
            store_f32(&self.fade_ms, ms);
        }
    }

    fn get_parameter(&self, param_id: i32) -> f32 {
        if param_id == 0 {
            load_f32(&self.gain_db)
        } else if param_id == 1 {
            load_f32(&self.fade_ms)
        } else {
            0.0
        }
    }

    fn prepare(&mut self, sample_rate: i32, _channel_count: i32) {
        self.sample_rate = sample_rate;
    }

    fn reset(&mut self) {
        store_f32(&self.gain_db, 0.0);
        store_f32(&self.target_linear, 1.0);
        store_f32(&self.fade_ms, 0.0);
        self.generation.fetch_add(1, Ordering::Release);
    }
}

fn db_to_linear(db: f32) -> f32 {
    10.0_f32.powf(db / 20.0)
}

fn fade_length_frames(fade_ms: f32, sample_rate: i32) -> i32 {
    if fade_ms <= 0.0 || sample_rate <= 0 {
        return 0;
    }
    let frames = f64::from(fade_ms) * 0.001 * f64::from(sample_rate);
    if frames >= f64::from(i32::MAX) {
        return i32::MAX;
    }
    let length = frames as i32;
    if length < 1 {
        0
    } else {
        length
    }
}

pub struct Pan {
    pan: AtomicU32,
}

impl Pan {
    pub fn new() -> Self {
        Self {
            pan: AtomicU32::new(0.0_f32.to_bits()),
        }
    }
}

impl DspEffect for Pan {
    fn process(&mut self, buffer: &mut [f32], num_frames: i32, channel_count: i32) {
        if channel_count < 2 {
            return;
        }
        let p = load_f32(&self.pan);
        let angle = (p + 1.0) * std::f32::consts::PI / 4.0;
        let gain_l = angle.cos();
        let gain_r = angle.sin();
        for frame in 0..num_frames {
            let idx = (frame * channel_count) as usize;
            if idx + 1 < buffer.len() {
                buffer[idx] *= gain_l;
                buffer[idx + 1] *= gain_r;
            }
        }
    }

    fn set_parameter(&mut self, param_id: i32, value: f32) {
        if param_id == 0 {
            store_f32(&self.pan, value);
        }
    }

    fn get_parameter(&self, param_id: i32) -> f32 {
        if param_id == 0 {
            load_f32(&self.pan)
        } else {
            0.0
        }
    }

    fn prepare(&mut self, _sample_rate: i32, _channel_count: i32) {}

    fn reset(&mut self) {
        store_f32(&self.pan, 0.0);
    }
}

pub struct MuteSolo {
    muted: AtomicU32,
    soloed: AtomicU32,
}

impl MuteSolo {
    pub fn new() -> Self {
        Self {
            muted: AtomicU32::new(0.0_f32.to_bits()),
            soloed: AtomicU32::new(0.0_f32.to_bits()),
        }
    }
}

impl DspEffect for MuteSolo {
    fn process(&mut self, buffer: &mut [f32], num_frames: i32, channel_count: i32) {
        if load_f32(&self.muted) > 0.5 {
            let count = (num_frames as usize).saturating_mul(channel_count as usize);
            let count = count.min(buffer.len());
            for sample in &mut buffer[..count] {
                *sample = 0.0;
            }
        }
    }

    fn set_parameter(&mut self, param_id: i32, value: f32) {
        if param_id == 0 {
            store_f32(&self.muted, value);
        } else if param_id == 1 {
            store_f32(&self.soloed, value);
        }
    }

    fn get_parameter(&self, param_id: i32) -> f32 {
        if param_id == 0 {
            load_f32(&self.muted)
        } else if param_id == 1 {
            load_f32(&self.soloed)
        } else {
            0.0
        }
    }

    fn prepare(&mut self, _sample_rate: i32, _channel_count: i32) {}

    fn reset(&mut self) {
        store_f32(&self.muted, 0.0);
        store_f32(&self.soloed, 0.0);
    }
}
