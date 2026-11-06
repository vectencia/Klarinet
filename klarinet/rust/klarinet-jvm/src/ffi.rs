//! C symbols implemented by the miniaudio wrapper and the DSP static library.

use std::os::raw::{c_char, c_void};

pub type UserAudioFn = unsafe extern "C" fn(*mut c_void, *mut f32, i32, i32) -> i32;
pub type XrunFn = unsafe extern "C" fn(*mut c_void, i32);
pub type DataFn = unsafe extern "C" fn(*mut c_void, i32, *mut f32, *const f32, i32);

#[repr(C)]
pub struct MaDeviceConfig {
    pub sample_rate: i32,
    pub channel_count: i32,
    pub period_frames: i32,
    pub direction: i32,
    pub device_id: i32,
    pub data_callback: Option<DataFn>,
    pub user: *mut c_void,
}

extern "C" {
    pub fn klarinet_ma_context_init() -> *mut c_void;
    pub fn klarinet_ma_context_uninit(context: *mut c_void);
    pub fn klarinet_ma_playback_count(context: *mut c_void) -> i32;
    pub fn klarinet_ma_capture_count(context: *mut c_void) -> i32;
    pub fn klarinet_ma_playback_name(
        context: *mut c_void,
        index: i32,
        out: *mut c_char,
        out_len: i32,
    ) -> i32;
    pub fn klarinet_ma_capture_name(
        context: *mut c_void,
        index: i32,
        out: *mut c_char,
        out_len: i32,
    ) -> i32;
    pub fn klarinet_ma_check_device_id(context: *mut c_void, direction: i32, device_id: i32)
        -> i32;
    pub fn klarinet_ma_device_init(
        context: *mut c_void,
        config: *const MaDeviceConfig,
    ) -> *mut c_void;
    pub fn klarinet_ma_device_start(device: *mut c_void);
    pub fn klarinet_ma_device_stop(device: *mut c_void);
    pub fn klarinet_ma_device_uninit(device: *mut c_void);
    pub fn klarinet_ma_device_state(device: *mut c_void) -> i32;
    pub fn klarinet_ma_device_latency_ms(device: *mut c_void) -> f64;
    pub fn klarinet_ma_decoder_open(path: *const c_char) -> *mut c_void;
    pub fn klarinet_ma_decoder_sample_rate(decoder: *mut c_void) -> i32;
    pub fn klarinet_ma_decoder_channels(decoder: *mut c_void) -> i32;
    pub fn klarinet_ma_decoder_total_frames(decoder: *mut c_void) -> i64;
    pub fn klarinet_ma_decoder_read(decoder: *mut c_void, dst: *mut f32, frame_count: i32) -> i64;
    pub fn klarinet_ma_decoder_seek(decoder: *mut c_void, frame_index: i64) -> i32;
    pub fn klarinet_ma_decoder_close(decoder: *mut c_void);

    pub fn klarinet_offload_create(
        frames_per_burst: i32,
        channel_count: i32,
        is_capture: i32,
        callback: UserAudioFn,
        user: *mut c_void,
    ) -> *mut c_void;
    pub fn klarinet_offload_destroy(handle: *mut c_void);
    pub fn klarinet_offload_set_xrun_callback(handle: *mut c_void, callback: XrunFn);
    pub fn klarinet_offload_process(handle: *mut c_void, audio: *mut f32, num_frames: i32);

    pub fn klarinet_create_effect(effect_type: i32) -> *mut c_void;
    pub fn klarinet_effect_destroy(handle: *mut c_void);
    pub fn klarinet_effect_set_parameter(handle: *mut c_void, param_id: i32, value: f32);
    pub fn klarinet_effect_get_parameter(handle: *mut c_void, param_id: i32) -> f32;
    pub fn klarinet_effect_set_enabled(handle: *mut c_void, enabled: i32);
    pub fn klarinet_effect_is_enabled(handle: *mut c_void) -> i32;
    pub fn klarinet_effect_prepare(handle: *mut c_void, sample_rate: i32, channel_count: i32);
    pub fn klarinet_chain_create() -> *mut c_void;
    pub fn klarinet_chain_destroy(chain: *mut c_void);
    pub fn klarinet_chain_add(chain: *mut c_void, effect: *mut c_void);
    pub fn klarinet_chain_remove(chain: *mut c_void, effect: *mut c_void);
    pub fn klarinet_chain_clear(chain: *mut c_void);
    pub fn klarinet_chain_prepare(chain: *mut c_void, sample_rate: i32, channel_count: i32);
    pub fn klarinet_chain_get_effect_count(chain: *mut c_void) -> i32;
    pub fn klarinet_chain_enqueue_param(
        chain: *mut c_void,
        effect: *mut c_void,
        param_id: i32,
        value: f32,
    );
    pub fn klarinet_chain_process(
        chain: *mut c_void,
        buffer: *mut f32,
        num_frames: i32,
        channel_count: i32,
    );

    pub fn klarinet_fft_create(size: i32) -> *mut c_void;
    pub fn klarinet_fft_destroy(handle: *mut c_void);
    pub fn klarinet_fft_forward(handle: *mut c_void, real: *mut f32, imag: *mut f32, length: i32);
    pub fn klarinet_fft_magnitude(
        handle: *const c_void,
        real: *const f32,
        imag: *const f32,
        out: *mut f32,
        out_len: i32,
    );
    pub fn klarinet_math_rms(data: *const f32, offset: i32, length: i32) -> f32;
    pub fn klarinet_math_peak(data: *const f32, offset: i32, length: i32) -> f32;
    pub fn klarinet_math_linear_to_db(linear: f32) -> f32;
    pub fn klarinet_math_db_to_linear(db: f32) -> f32;
    pub fn klarinet_math_hann(out: *mut f32, size: i32);
    pub fn klarinet_analyzer_create(fft_size: i32, sample_rate: i32) -> *mut c_void;
    pub fn klarinet_analyzer_destroy(handle: *mut c_void);
    pub fn klarinet_analyzer_analyze(
        handle: *mut c_void,
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
    );
    pub fn klarinet_analyzer_band_energy(
        spectrum: *const f32,
        spectrum_len: i32,
        resolution: f32,
        low_hz: f32,
        high_hz: f32,
    ) -> f32;
    pub fn klarinet_resampler_create(source_rate: i32, channels: i32) -> *mut c_void;
    pub fn klarinet_resampler_destroy(handle: *mut c_void);
    pub fn klarinet_resampler_has_buffered(handle: *const c_void) -> i32;
    pub fn klarinet_resample_linear(
        handle: *mut c_void,
        dest_rate: i32,
        dest: *mut f32,
        dest_frames: i32,
        read: Option<unsafe extern "C" fn(*mut c_void, *mut f32, i32) -> i32>,
        user: *mut c_void,
    ) -> i32;
}

pub fn ptr_from(value: i64) -> *mut c_void {
    value as usize as *mut c_void
}

pub fn bits_from(value: *mut c_void) -> i64 {
    value as usize as i64
}
