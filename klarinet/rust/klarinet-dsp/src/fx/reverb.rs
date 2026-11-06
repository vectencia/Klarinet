use std::sync::atomic::AtomicU32;

use crate::primitives::CircularBuffer;

use super::{load_f32, store_f32, DspEffect};

const NUM_COMBS: usize = 8;
const NUM_ALLPASSES: usize = 4;
const STEREO_SPREAD: i32 = 23;
const FIXED_GAIN: f32 = 0.015;
const ALLPASS_FEEDBACK: f32 = 0.5;
const REFERENCE_RATE: f32 = 44100.0;
const COMB_TUNINGS: [i32; NUM_COMBS] = [1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617];
const ALLPASS_TUNINGS: [i32; NUM_ALLPASSES] = [556, 441, 341, 225];

pub struct Reverb {
    room_size: AtomicU32,
    damping: AtomicU32,
    wet_dry: AtomicU32,
    width: AtomicU32,
    comb_l: [CircularBuffer; NUM_COMBS],
    comb_r: [CircularBuffer; NUM_COMBS],
    allpass_l: [CircularBuffer; NUM_ALLPASSES],
    allpass_r: [CircularBuffer; NUM_ALLPASSES],
    filter_l: [f32; NUM_COMBS],
    filter_r: [f32; NUM_COMBS],
}

impl Reverb {
    pub fn new() -> Self {
        Self {
            room_size: AtomicU32::new(0.5_f32.to_bits()),
            damping: AtomicU32::new(0.5_f32.to_bits()),
            wet_dry: AtomicU32::new(0.3_f32.to_bits()),
            width: AtomicU32::new(1.0_f32.to_bits()),
            comb_l: std::array::from_fn(|_| CircularBuffer::new(0)),
            comb_r: std::array::from_fn(|_| CircularBuffer::new(0)),
            allpass_l: std::array::from_fn(|_| CircularBuffer::new(0)),
            allpass_r: std::array::from_fn(|_| CircularBuffer::new(0)),
            filter_l: [0.0; NUM_COMBS],
            filter_r: [0.0; NUM_COMBS],
        }
    }
}

impl DspEffect for Reverb {
    fn process(&mut self, buffer: &mut [f32], num_frames: i32, channel_count: i32) {
        if channel_count <= 0 {
            return;
        }
        let room_size = load_f32(&self.room_size);
        let damp = load_f32(&self.damping);
        let mix = load_f32(&self.wet_dry).clamp(0.0, 1.0);
        let width = load_f32(&self.width).clamp(0.0, 1.0);
        let feedback = room_size * 0.28 + 0.7;
        let damp1 = damp;
        let damp2 = 1.0 - damp;
        let wet1 = mix * (width * 0.5 + 0.5);
        let wet2 = mix * ((1.0 - width) * 0.5);
        let dry = 1.0 - mix;
        let stereo = channel_count >= 2;
        for frame in 0..num_frames {
            let base = (frame * channel_count) as usize;
            if base >= buffer.len() {
                break;
            }
            let input_l = buffer[base];
            let input_r = if stereo && base + 1 < buffer.len() {
                buffer[base + 1]
            } else {
                input_l
            };
            let input_mono = (input_l + input_r) * 0.5 * FIXED_GAIN;
            let mut out_l = 0.0_f32;
            let mut out_r = 0.0_f32;
            for i in 0..NUM_COMBS {
                let comb_out_l = self.comb_l[i].read((self.comb_l[i].size() - 1) as f32);
                self.filter_l[i] = damp2 * comb_out_l + damp1 * self.filter_l[i];
                self.comb_l[i].write(input_mono + self.filter_l[i] * feedback);
                out_l += comb_out_l;
                let comb_out_r = self.comb_r[i].read((self.comb_r[i].size() - 1) as f32);
                self.filter_r[i] = damp2 * comb_out_r + damp1 * self.filter_r[i];
                self.comb_r[i].write(input_mono + self.filter_r[i] * feedback);
                out_r += comb_out_r;
            }
            for i in 0..NUM_ALLPASSES {
                let buf_out_l = self.allpass_l[i].read((self.allpass_l[i].size() - 1) as f32);
                self.allpass_l[i].write(out_l + buf_out_l * ALLPASS_FEEDBACK);
                out_l = buf_out_l - out_l;
                let buf_out_r = self.allpass_r[i].read((self.allpass_r[i].size() - 1) as f32);
                self.allpass_r[i].write(out_r + buf_out_r * ALLPASS_FEEDBACK);
                out_r = buf_out_r - out_r;
            }
            if stereo && base + 1 < buffer.len() {
                buffer[base] = input_l * dry + out_l * wet1 + out_r * wet2;
                buffer[base + 1] = input_r * dry + out_r * wet1 + out_l * wet2;
            } else {
                buffer[base] = input_l * dry + out_l * mix;
            }
        }
    }

    fn set_parameter(&mut self, param_id: i32, value: f32) {
        match param_id {
            0 => store_f32(&self.room_size, value),
            1 => store_f32(&self.damping, value),
            2 => store_f32(&self.wet_dry, value),
            3 => store_f32(&self.width, value),
            _ => {}
        }
    }

    fn get_parameter(&self, param_id: i32) -> f32 {
        match param_id {
            0 => load_f32(&self.room_size),
            1 => load_f32(&self.damping),
            2 => load_f32(&self.wet_dry),
            3 => load_f32(&self.width),
            _ => 0.0,
        }
    }

    fn prepare(&mut self, sample_rate: i32, _channel_count: i32) {
        let rate_scale = sample_rate as f32 / REFERENCE_RATE;
        for (i, tuning) in COMB_TUNINGS.iter().enumerate() {
            let size_l = (*tuning as f32 * rate_scale) as i32;
            let size_r = ((*tuning + STEREO_SPREAD) as f32 * rate_scale) as i32;
            self.comb_l[i].set_size(size_l);
            self.comb_r[i].set_size(size_r);
            self.comb_l[i].clear();
            self.comb_r[i].clear();
        }
        for (i, tuning) in ALLPASS_TUNINGS.iter().enumerate() {
            let size_l = (*tuning as f32 * rate_scale) as i32;
            let size_r = ((*tuning + STEREO_SPREAD) as f32 * rate_scale) as i32;
            self.allpass_l[i].set_size(size_l);
            self.allpass_r[i].set_size(size_r);
            self.allpass_l[i].clear();
            self.allpass_r[i].clear();
        }
        self.filter_l = [0.0; NUM_COMBS];
        self.filter_r = [0.0; NUM_COMBS];
    }

    fn reset(&mut self) {
        for i in 0..NUM_COMBS {
            self.comb_l[i].clear();
            self.comb_r[i].clear();
        }
        for i in 0..NUM_ALLPASSES {
            self.allpass_l[i].clear();
            self.allpass_r[i].clear();
        }
        self.filter_l = [0.0; NUM_COMBS];
        self.filter_r = [0.0; NUM_COMBS];
    }
}
