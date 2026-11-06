//! One-pole envelope follower. `process` does not allocate.

#[derive(Clone, Copy, Debug)]
pub struct EnvelopeFollower {
    attack_coeff: f32,
    release_coeff: f32,
    envelope: f32,
    sample_rate: f32,
}

impl Default for EnvelopeFollower {
    fn default() -> Self {
        Self::new()
    }
}

impl EnvelopeFollower {
    pub fn new() -> Self {
        Self {
            attack_coeff: 0.0,
            release_coeff: 0.0,
            envelope: 0.0,
            sample_rate: 48000.0,
        }
    }

    pub fn prepare(&mut self, sample_rate: f32) {
        self.sample_rate = sample_rate;
    }

    pub fn set_attack_ms(&mut self, ms: f32) {
        self.attack_coeff = ms_to_coeff(ms, self.sample_rate);
    }

    pub fn set_release_ms(&mut self, ms: f32) {
        self.release_coeff = ms_to_coeff(ms, self.sample_rate);
    }

    pub fn process(&mut self, input: f32) -> f32 {
        let input_abs = input.abs();
        let coeff = if input_abs > self.envelope {
            self.attack_coeff
        } else {
            self.release_coeff
        };
        self.envelope += coeff * (input_abs - self.envelope);
        self.envelope
    }

    pub fn reset(&mut self) {
        self.envelope = 0.0;
    }
}

fn ms_to_coeff(ms: f32, sample_rate: f32) -> f32 {
    if ms <= 0.0 || sample_rate <= 0.0 {
        return 1.0;
    }
    1.0 - (-1.0 / (ms * 0.001 * sample_rate)).exp()
}
