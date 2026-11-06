//! FFT, metering, and linear resampling.
//!
//! `klarinet_fft_forward` and `klarinet_resample_linear` do not allocate, lock,
//! or panic. Constructors own the twiddle tables, the Hann window, and the
//! resampler ring. These entry points are not the device callback.

use std::os::raw::c_void;
use std::ptr;

const RESAMPLER_CAPACITY: usize = 4096;

fn power_of_two_size(size: i32) -> Option<usize> {
    if size > 0 && size & (size - 1) == 0 {
        Some(size as usize)
    } else {
        None
    }
}

pub(crate) struct Fft {
    size: usize,
    half: usize,
    cos: Vec<f32>,
    sin: Vec<f32>,
}

impl Fft {
    fn try_new(size: i32) -> Option<Self> {
        let size = power_of_two_size(size)?;
        let half = size / 2;
        let mut cos = Vec::with_capacity(half);
        let mut sin = Vec::with_capacity(half);
        for i in 0..half {
            let angle = 2.0 * std::f64::consts::PI * i as f64 / size as f64;
            cos.push(angle.cos() as f32);
            sin.push(angle.sin() as f32);
        }
        Some(Self {
            size,
            half,
            cos,
            sin,
        })
    }

    fn forward(&self, real: &mut [f32], imag: &mut [f32]) {
        let size = self.size;
        if real.len() < size || imag.len() < size || size == 0 {
            return;
        }
        let mut j = 0usize;
        for i in 0..size.saturating_sub(1) {
            if i < j {
                real.swap(i, j);
                imag.swap(i, j);
            }
            let mut k = self.half;
            while k <= j {
                j -= k;
                k >>= 1;
            }
            j += k;
        }

        let mut step = 1usize;
        while step < size {
            let double_step = step << 1;
            let table_step = self.half / step;
            let mut group = 0usize;
            while group < size {
                let mut table_idx = 0usize;
                for pair in group..(group + step) {
                    let matched = pair + step;
                    let cos = self.cos[table_idx];
                    let sin = self.sin[table_idx];
                    let t_real = cos * real[matched] + sin * imag[matched];
                    let t_imag = cos * imag[matched] - sin * real[matched];
                    real[matched] = real[pair] - t_real;
                    imag[matched] = imag[pair] - t_imag;
                    real[pair] += t_real;
                    imag[pair] += t_imag;
                    table_idx += table_step;
                }
                group += double_step;
            }
            step = double_step;
        }
    }

    fn magnitude(&self, real: &[f32], imag: &[f32], out: &mut [f32]) {
        let bins = self.half + 1;
        let count = bins.min(out.len()).min(real.len()).min(imag.len());
        for i in 0..count {
            let re = real[i];
            let im = imag[i];
            out[i] = (re * re + im * im).sqrt();
        }
    }
}

fn hann_into(out: &mut [f32]) {
    let size = out.len();
    if size <= 1 {
        for sample in out {
            *sample = 1.0;
        }
        return;
    }
    let denom = (size - 1) as f64;
    for (i, sample) in out.iter_mut().enumerate() {
        let angle = 2.0 * std::f64::consts::PI * i as f64 / denom;
        *sample = (0.5 * (1.0 - angle.cos())) as f32;
    }
}

fn linear_to_db(linear: f32) -> f32 {
    if linear <= 0.0 {
        f32::NEG_INFINITY
    } else {
        20.0 * linear.log10()
    }
}

fn db_to_linear(db: f32) -> f32 {
    10.0f32.powf(db / 20.0)
}

fn rms(samples: &[f32]) -> f32 {
    if samples.is_empty() {
        return 0.0;
    }
    let mut sum = 0.0f64;
    for sample in samples {
        let value = f64::from(*sample);
        sum += value * value;
    }
    (sum / samples.len() as f64).sqrt() as f32
}

fn peak(samples: &[f32]) -> f32 {
    let mut max = 0.0f32;
    for sample in samples {
        let abs = sample.abs();
        if abs > max {
            max = abs;
        }
    }
    max
}

pub(crate) struct Analyzer {
    fft: Fft,
    window: Vec<f32>,
    real: Vec<f32>,
    imag: Vec<f32>,
}

impl Analyzer {
    fn try_new(fft_size: i32) -> Option<Self> {
        let fft = Fft::try_new(fft_size)?;
        let mut window = vec![0.0; fft.size];
        hann_into(&mut window);
        Some(Self {
            real: vec![0.0; fft.size],
            imag: vec![0.0; fft.size],
            fft,
            window,
        })
    }

    fn analyze(
        &mut self,
        buffer: &[f32],
        num_frames: usize,
        channel_count: i32,
    ) -> Option<(f32, f32, f32, f32)> {
        self.real.fill(0.0);
        self.imag.fill(0.0);
        let mut sum_sq = 0.0f64;
        let mut peak = 0.0f32;
        if channel_count == 1 {
            // `FloatArray.copyOf` zero-pads when the caller asks for more frames
            // than the array holds. AnalyzingCallback does that.
            for frame in 0..num_frames {
                let sample = buffer.get(frame).copied().unwrap_or(0.0);
                accumulate(&mut sum_sq, &mut peak, sample);
                if frame < self.fft.size {
                    self.real[frame] = sample * self.window[frame];
                }
            }
        } else if channel_count > 1 {
            let channels = channel_count as usize;
            let needed = num_frames.checked_mul(channels)?;
            if buffer.len() < needed {
                return None;
            }
            for frame in 0..num_frames {
                let base = frame * channels;
                let mut sum = 0.0f32;
                for channel in 0..channels {
                    sum += buffer[base + channel];
                }
                let sample = sum / channel_count as f32;
                accumulate(&mut sum_sq, &mut peak, sample);
                if frame < self.fft.size {
                    self.real[frame] = sample * self.window[frame];
                }
            }
        } else {
            // `0 until channelCount` is empty. `0f / channelCount` is NaN at 0
            // and a zero at any negative count.
            let sample = if channel_count == 0 {
                f32::NAN
            } else {
                0.0 / channel_count as f32
            };
            for frame in 0..num_frames {
                accumulate(&mut sum_sq, &mut peak, sample);
                if frame < self.fft.size {
                    self.real[frame] = sample * self.window[frame];
                }
            }
        }
        self.fft.forward(&mut self.real, &mut self.imag);
        let rms = if num_frames == 0 {
            0.0
        } else {
            (sum_sq / num_frames as f64).sqrt() as f32
        };
        Some((rms, linear_to_db(rms), peak, linear_to_db(peak)))
    }
}

fn accumulate(sum_sq: &mut f64, peak: &mut f32, sample: f32) {
    let value = f64::from(sample);
    *sum_sq += value * value;
    let abs = sample.abs();
    if abs > *peak {
        *peak = abs;
    }
}

fn band_energy(spectrum: &[f32], resolution: f32, low_hz: f32, high_hz: f32) -> f32 {
    if spectrum.is_empty() {
        return 0.0;
    }
    let low_bin = ((low_hz / resolution) as i32).max(0);
    let high_bin = ((high_hz / resolution) as i32).min(spectrum.len() as i32 - 1);
    if high_bin < low_bin {
        return 0.0;
    }
    let mut energy = 0.0f32;
    for bin in low_bin..=high_bin {
        let magnitude = spectrum[bin as usize];
        energy += magnitude * magnitude;
    }
    let count = (high_bin - low_bin + 1) as f32;
    (energy / count).sqrt()
}

type ResamplerRead = unsafe extern "C" fn(*mut c_void, *mut f32, i32) -> i32;

enum Fill {
    Ready,
    Ended,
    Failed,
}

pub(crate) struct Resampler {
    source_rate: i32,
    channels: usize,
    ring: Vec<f32>,
    head: usize,
    size: usize,
    phase: f64,
}

impl Resampler {
    fn try_new(source_rate: i32, channels: i32) -> Option<Self> {
        if source_rate <= 0 || channels <= 0 {
            return None;
        }
        let channels = channels as usize;
        let samples = RESAMPLER_CAPACITY.checked_mul(channels)?;
        Some(Self {
            source_rate,
            channels,
            ring: vec![0.0; samples],
            head: 0,
            size: 0,
            phase: 0.0,
        })
    }

    fn sample(&self, offset: usize, channel: usize) -> f32 {
        let frame = (self.head + offset) % RESAMPLER_CAPACITY;
        self.ring
            .get(frame * self.channels + channel)
            .copied()
            .unwrap_or(0.0)
    }

    fn consume(&mut self, step: f64) {
        self.phase += step;
        while self.phase >= 1.0 && self.size > 0 {
            self.phase -= 1.0;
            self.head = (self.head + 1) % RESAMPLER_CAPACITY;
            self.size -= 1;
        }
        if self.size == 0 {
            self.phase = 0.0;
        }
    }

    fn ensure(&mut self, needed: usize, read: ResamplerRead, user: *mut c_void) -> Fill {
        while self.size < needed {
            let space = RESAMPLER_CAPACITY - self.size;
            if space == 0 {
                return if self.size >= needed {
                    Fill::Ready
                } else {
                    Fill::Ended
                };
            }
            let tail = (self.head + self.size) % RESAMPLER_CAPACITY;
            let contiguous = space.min(RESAMPLER_CAPACITY - tail);
            let index = tail * self.channels;
            // SAFETY: `tail + contiguous` stays inside the 4096-frame ring, and
            // `read` writes at most `contiguous` frames of `channels` floats.
            // The callback does not touch any other part of this resampler.
            // A negative return means the callback failed and must not be called again.
            let got = unsafe { read(user, self.ring.as_mut_ptr().add(index), contiguous as i32) };
            if got < 0 {
                return Fill::Failed;
            }
            if got == 0 {
                return if self.size >= needed {
                    Fill::Ready
                } else {
                    Fill::Ended
                };
            }
            self.size += (got as usize).min(contiguous);
        }
        Fill::Ready
    }

    fn render(
        &mut self,
        dest_rate: i32,
        dest: *mut f32,
        dest_frames: i32,
        read: ResamplerRead,
        user: *mut c_void,
    ) -> i32 {
        if dest.is_null() || dest_frames <= 0 || dest_rate <= 0 || self.channels == 0 {
            return 0;
        }
        let max_out = dest_frames;
        if dest_rate == self.source_rate && self.size == 0 && self.phase == 0.0 {
            // SAFETY: `dest` holds `dest_frames` frames of `channels` floats.
            // `read` writes at most that many frames and does not retain `dest`.
            let got = unsafe { read(user, dest, dest_frames) };
            if got < 0 {
                return -1;
            }
            return if got == 0 { 0 } else { got.min(dest_frames) };
        }
        let step = f64::from(self.source_rate) / f64::from(dest_rate);
        let mut out = 0i32;
        while out < max_out {
            match self.ensure(2, read, user) {
                Fill::Failed => return -1,
                Fill::Ended => {
                    if self.size == 0 {
                        break;
                    }
                    write_frame(self, dest, out);
                    self.consume(step);
                    out += 1;
                    continue;
                }
                Fill::Ready => {}
            }
            let t = self.phase;
            let one_minus = 1.0 - t;
            let dest_off = out as usize * self.channels;
            for channel in 0..self.channels {
                let a = f64::from(self.sample(0, channel));
                let b = f64::from(self.sample(1, channel));
                // SAFETY: `out < dest_frames` and `channel < channels`, so
                // `dest_off + channel` is inside the destination the caller passed.
                unsafe {
                    *dest.add(dest_off + channel) = (a * one_minus + b * t) as f32;
                }
            }
            self.consume(step);
            out += 1;
        }
        out
    }
}

fn write_frame(resampler: &Resampler, dest: *mut f32, out: i32) {
    let dest_off = out as usize * resampler.channels;
    for channel in 0..resampler.channels {
        let sample = resampler.sample(0, channel);
        // SAFETY: same destination bound as `Resampler::render`.
        unsafe {
            *dest.add(dest_off + channel) = sample;
        }
    }
}

fn write_f32(slot: *mut f32, value: f32) {
    if !slot.is_null() {
        // SAFETY: a non-null out pointer is one float the caller allows us to store.
        unsafe { *slot = value }
    }
}

fn samples<'a>(data: *const f32, offset: i32, length: i32) -> Option<&'a [f32]> {
    if data.is_null() || offset < 0 || length <= 0 {
        return None;
    }
    // SAFETY: the caller guarantees `data` covers `offset + length` floats.
    Some(unsafe { std::slice::from_raw_parts(data.add(offset as usize), length as usize) })
}

/// # Safety
/// See `klarinet_dsp.h`. A null return means `size` is not a positive power of two.
#[no_mangle]
pub extern "C" fn klarinet_fft_create(size: i32) -> *mut Fft {
    match Fft::try_new(size) {
        Some(fft) => Box::into_raw(Box::new(fft)),
        None => ptr::null_mut(),
    }
}

/// # Safety
/// `handle` is null or a pointer returned by `klarinet_fft_create` and not yet destroyed.
#[no_mangle]
pub extern "C" fn klarinet_fft_destroy(handle: *mut Fft) {
    if handle.is_null() {
        return;
    }
    // SAFETY: `handle` is the unique owner from `klarinet_fft_create`.
    unsafe { drop(Box::from_raw(handle)) }
}

/// # Safety
/// `real` and `imag` are each null or point at `length` floats this call may overwrite.
#[no_mangle]
pub extern "C" fn klarinet_fft_forward(
    handle: *mut Fft,
    real: *mut f32,
    imag: *mut f32,
    length: i32,
) {
    if handle.is_null() || real.is_null() || imag.is_null() || length <= 0 {
        return;
    }
    let fft = unsafe { &*handle };
    if (length as usize) < fft.size {
        return;
    }
    // SAFETY: both pointers cover at least `fft.size` floats for this call.
    let real = unsafe { std::slice::from_raw_parts_mut(real, fft.size) };
    let imag = unsafe { std::slice::from_raw_parts_mut(imag, fft.size) };
    fft.forward(real, imag);
}

/// # Safety
/// `real` and `imag` cover `out_len` readable floats. `out` covers `out_len` writable floats.
#[no_mangle]
pub extern "C" fn klarinet_fft_magnitude(
    handle: *const Fft,
    real: *const f32,
    imag: *const f32,
    out: *mut f32,
    out_len: i32,
) {
    if handle.is_null() || real.is_null() || imag.is_null() || out.is_null() || out_len <= 0 {
        return;
    }
    let fft = unsafe { &*handle };
    let len = out_len as usize;
    // SAFETY: the three pointers each cover `out_len` floats.
    let real = unsafe { std::slice::from_raw_parts(real, len) };
    let imag = unsafe { std::slice::from_raw_parts(imag, len) };
    let out = unsafe { std::slice::from_raw_parts_mut(out, len) };
    fft.magnitude(real, imag, out);
}

/// # Safety
/// `data` is null or covers `offset + length` floats when both are non-negative.
#[no_mangle]
pub extern "C" fn klarinet_math_rms(data: *const f32, offset: i32, length: i32) -> f32 {
    if length <= 0 {
        return 0.0;
    }
    samples(data, offset, length).map(rms).unwrap_or(0.0)
}

/// # Safety
/// `data` is null or covers `offset + length` floats when both are non-negative.
#[no_mangle]
pub extern "C" fn klarinet_math_peak(data: *const f32, offset: i32, length: i32) -> f32 {
    if length <= 0 {
        return 0.0;
    }
    samples(data, offset, length).map(peak).unwrap_or(0.0)
}

#[no_mangle]
pub extern "C" fn klarinet_math_linear_to_db(linear: f32) -> f32 {
    linear_to_db(linear)
}

#[no_mangle]
pub extern "C" fn klarinet_math_db_to_linear(db: f32) -> f32 {
    db_to_linear(db)
}

/// # Safety
/// `out` is null or covers `size` writable floats. `size` <= 0 is a no-op.
#[no_mangle]
pub extern "C" fn klarinet_math_hann(out: *mut f32, size: i32) {
    if out.is_null() || size <= 0 {
        return;
    }
    // SAFETY: `out` covers `size` floats.
    hann_into(unsafe { std::slice::from_raw_parts_mut(out, size as usize) });
}

/// # Safety
/// A null return means `source_rate` or `channels` is not positive, or the ring does not fit.
#[no_mangle]
pub extern "C" fn klarinet_resampler_create(source_rate: i32, channels: i32) -> *mut Resampler {
    match Resampler::try_new(source_rate, channels) {
        Some(resampler) => Box::into_raw(Box::new(resampler)),
        None => ptr::null_mut(),
    }
}

/// # Safety
/// `handle` is null or a pointer returned by `klarinet_resampler_create` and not yet destroyed.
#[no_mangle]
pub extern "C" fn klarinet_resampler_destroy(handle: *mut Resampler) {
    if handle.is_null() {
        return;
    }
    // SAFETY: `handle` is the unique owner from `klarinet_resampler_create`.
    unsafe { drop(Box::from_raw(handle)) }
}

/// # Safety
/// `handle` is null or a live resampler pointer.
#[no_mangle]
pub extern "C" fn klarinet_resampler_has_buffered(handle: *const Resampler) -> i32 {
    if handle.is_null() {
        return 0;
    }
    let resampler = unsafe { &*handle };
    i32::from(resampler.size > 0)
}

/// # Safety
/// `dest` is null or covers `dest_frames` frames of this resampler's channel count.
/// `read` is null or writes at most the requested frames into the pointer it is given
/// and does not call `klarinet_resample_linear` on `handle`.
#[no_mangle]
pub extern "C" fn klarinet_resample_linear(
    handle: *mut Resampler,
    dest_rate: i32,
    dest: *mut f32,
    dest_frames: i32,
    read: Option<ResamplerRead>,
    user: *mut c_void,
) -> i32 {
    let Some(read) = read else {
        return 0;
    };
    if handle.is_null() {
        return 0;
    }
    let resampler = unsafe { &mut *handle };
    resampler.render(dest_rate, dest, dest_frames, read, user)
}

/// # Safety
/// A null return means `fft_size` is not a positive power of two.
/// `sample_rate` is accepted so the constructor matches `AudioAnalyzer` and is not read.
#[no_mangle]
pub extern "C" fn klarinet_analyzer_create(fft_size: i32, _sample_rate: i32) -> *mut Analyzer {
    match Analyzer::try_new(fft_size) {
        Some(analyzer) => Box::into_raw(Box::new(analyzer)),
        None => ptr::null_mut(),
    }
}

/// # Safety
/// `handle` is null or a pointer returned by `klarinet_analyzer_create` and not yet destroyed.
#[no_mangle]
pub extern "C" fn klarinet_analyzer_destroy(handle: *mut Analyzer) {
    if handle.is_null() {
        return;
    }
    // SAFETY: `handle` is the unique owner from `klarinet_analyzer_create`.
    unsafe { drop(Box::from_raw(handle)) }
}

/// # Safety
/// `buffer` is null or covers `buffer_samples` floats. `spectrum` is null or covers
/// `spectrum_len` writable floats. Each level pointer is null or one writable float.
/// A short buffer writes nothing and returns.
#[no_mangle]
pub extern "C" fn klarinet_analyzer_analyze(
    handle: *mut Analyzer,
    buffer: *const f32,
    buffer_samples: i32,
    num_frames: i32,
    channel_count: i32,
    spectrum: *mut f32,
    spectrum_len: i32,
    rms: *mut f32,
    rms_db: *mut f32,
    peak: *mut f32,
    peak_db: *mut f32,
) {
    if handle.is_null() || spectrum.is_null() || spectrum_len <= 0 {
        return;
    }
    let analyzer = unsafe { &mut *handle };
    let frames = if num_frames > 0 {
        num_frames as usize
    } else {
        0
    };
    let input = if buffer.is_null() || buffer_samples <= 0 {
        &[][..]
    } else {
        // SAFETY: `buffer` covers `buffer_samples` floats.
        unsafe { std::slice::from_raw_parts(buffer, buffer_samples as usize) }
    };
    let Some((rms_v, rms_db_v, peak_v, peak_db_v)) = analyzer.analyze(input, frames, channel_count)
    else {
        return;
    };
    let len = spectrum_len as usize;
    // SAFETY: `spectrum` covers `spectrum_len` floats.
    let spectrum = unsafe { std::slice::from_raw_parts_mut(spectrum, len) };
    analyzer
        .fft
        .magnitude(&analyzer.real, &analyzer.imag, spectrum);
    write_f32(rms, rms_v);
    write_f32(rms_db, rms_db_v);
    write_f32(peak, peak_v);
    write_f32(peak_db, peak_db_v);
}

/// # Safety
/// `spectrum` is null or covers `spectrum_len` floats.
#[no_mangle]
pub extern "C" fn klarinet_analyzer_band_energy(
    spectrum: *const f32,
    spectrum_len: i32,
    resolution: f32,
    low_hz: f32,
    high_hz: f32,
) -> f32 {
    if spectrum.is_null() || spectrum_len <= 0 {
        return 0.0;
    }
    // SAFETY: `spectrum` covers `spectrum_len` floats.
    let spectrum = unsafe { std::slice::from_raw_parts(spectrum, spectrum_len as usize) };
    band_energy(spectrum, resolution, low_hz, high_hz)
}

pub(crate) fn keep_exports() {
    let exports: [*const (); 17] = [
        klarinet_fft_create as *const (),
        klarinet_fft_destroy as *const (),
        klarinet_fft_forward as *const (),
        klarinet_fft_magnitude as *const (),
        klarinet_math_rms as *const (),
        klarinet_math_peak as *const (),
        klarinet_math_linear_to_db as *const (),
        klarinet_math_db_to_linear as *const (),
        klarinet_math_hann as *const (),
        klarinet_resampler_create as *const (),
        klarinet_resampler_destroy as *const (),
        klarinet_resampler_has_buffered as *const (),
        klarinet_resample_linear as *const (),
        klarinet_analyzer_create as *const (),
        klarinet_analyzer_destroy as *const (),
        klarinet_analyzer_analyze as *const (),
        klarinet_analyzer_band_energy as *const (),
    ];
    let _ = std::hint::black_box(exports);
}
