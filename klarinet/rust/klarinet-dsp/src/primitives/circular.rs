//! Fixed delay line. `write`, `read`, and `read_linear` do not allocate.

#[derive(Clone, Debug)]
pub struct CircularBuffer {
    buffer: Vec<f32>,
    write_pos: i32,
    size: i32,
}

impl Default for CircularBuffer {
    fn default() -> Self {
        Self::new(0)
    }
}

impl CircularBuffer {
    pub fn new(max_size_in_samples: i32) -> Self {
        let mut buffer = Self {
            buffer: Vec::new(),
            write_pos: 0,
            size: 0,
        };
        if max_size_in_samples > 0 {
            buffer.set_size(max_size_in_samples);
        }
        buffer
    }

    pub fn set_size(&mut self, size_in_samples: i32) {
        if size_in_samples <= 0 {
            self.size = 0;
            self.buffer.clear();
            self.write_pos = 0;
            return;
        }
        self.size = size_in_samples;
        self.buffer.resize(size_in_samples as usize, 0.0);
        self.write_pos %= self.size;
    }

    pub fn clear(&mut self) {
        self.buffer.fill(0.0);
        self.write_pos = 0;
    }

    pub fn write(&mut self, sample: f32) {
        if self.size == 0 {
            return;
        }
        self.buffer[self.write_pos as usize] = sample;
        self.write_pos = (self.write_pos + 1) % self.size;
    }

    pub fn read(&self, delay_samples: f32) -> f32 {
        if self.size == 0 {
            return 0.0;
        }
        let delay = delay_samples as i32;
        let read_pos = self.write_pos - 1 - delay;
        self.buffer[wrap(read_pos, self.size) as usize]
    }

    pub fn read_linear(&self, delay_samples: f32) -> f32 {
        if self.size == 0 {
            return 0.0;
        }
        let frac = delay_samples - delay_samples.floor();
        let delay0 = delay_samples.floor() as i32;
        let delay1 = delay0 + 1;
        let read0 = wrap(self.write_pos - 1 - delay0, self.size) as usize;
        let read1 = wrap(self.write_pos - 1 - delay1, self.size) as usize;
        let s0 = self.buffer[read0];
        let s1 = self.buffer[read1];
        s0 + frac * (s1 - s0)
    }

    pub fn size(&self) -> i32 {
        self.size
    }
}

fn wrap(pos: i32, size: i32) -> i32 {
    pos.rem_euclid(size)
}
