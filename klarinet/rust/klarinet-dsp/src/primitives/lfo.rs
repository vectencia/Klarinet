//! Phase-accumulator sine LFO. `next_sample` does not allocate.

use std::f32::consts::PI;

const TWO_PI: f32 = 2.0 * PI;

#[derive(Clone, Copy, Debug)]
pub struct Lfo {
    phase: f32,
    phase_increment: f32,
    sample_rate: f32,
}

impl Default for Lfo {
    fn default() -> Self {
        Self::new()
    }
}

impl Lfo {
    pub fn new() -> Self {
        Self {
            phase: 0.0,
            phase_increment: 0.0,
            sample_rate: 48000.0,
        }
    }

    pub fn prepare(&mut self, sample_rate: f32) {
        self.sample_rate = sample_rate;
    }

    pub fn set_frequency(&mut self, hz: f32) {
        self.phase_increment = TWO_PI * hz / self.sample_rate;
    }

    pub fn next_sample(&mut self) -> f32 {
        let value = self.phase.sin();
        self.phase += self.phase_increment;
        if self.phase >= TWO_PI {
            self.phase -= TWO_PI;
        }
        value
    }

    pub fn reset(&mut self) {
        self.phase = 0.0;
    }
}
