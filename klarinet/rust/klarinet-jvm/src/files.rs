//! Decoder and encoder JNI. These run on the calling thread, not the audio thread.
//!
//! WAV bytes go through `klarinet_wav`. Every other decoder stays on miniaudio.

use std::ffi::CStr;
use std::fs::File;
use std::io::{BufWriter, Read};
use std::os::raw::c_void;

use klarinet_wav::{WavReader, WavWriter};

use crate::ffi;
use crate::jni::{self, JniEnv, JniObject};

enum OpenedDecoder {
    Wav(WavReader<File>),
    Miniaudio(*mut c_void),
}

struct WavEncoder {
    channels: usize,
    writer: Option<WavWriter<BufWriter<File>>>,
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeDecoderInitFile(
    env: *mut JniEnv,
    _class: JniObject,
    path: JniObject,
) -> i64 {
    unsafe {
        match jni::with_utf(env, path, open_decoder) {
            Some(Some(decoder)) => ffi::bits_from(decoder as *mut c_void),
            _ => 0,
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeDecoderGetSampleRate(
    _env: *mut JniEnv,
    _class: JniObject,
    decoder_ptr: i64,
) -> i32 {
    unsafe { decoder_sample_rate(decoder_from(decoder_ptr)) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeDecoderGetChannels(
    _env: *mut JniEnv,
    _class: JniObject,
    decoder_ptr: i64,
) -> i32 {
    unsafe { decoder_channels(decoder_from(decoder_ptr)) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeDecoderGetTotalFrames(
    _env: *mut JniEnv,
    _class: JniObject,
    decoder_ptr: i64,
) -> i64 {
    unsafe { decoder_total_frames(decoder_from(decoder_ptr)) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeDecoderReadFrames(
    env: *mut JniEnv,
    _class: JniObject,
    decoder_ptr: i64,
    output: JniObject,
    frame_count: i32,
) -> i64 {
    unsafe {
        let samples = jni::get_float_elements(env, output);
        let frames = decoder_read(decoder_from(decoder_ptr), samples, frame_count);
        jni::release_float_elements(env, output, samples, 0);
        frames
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeDecoderSeek(
    _env: *mut JniEnv,
    _class: JniObject,
    decoder_ptr: i64,
    frame_index: i64,
) -> u8 {
    unsafe {
        if decoder_seek(decoder_from(decoder_ptr), frame_index) {
            1
        } else {
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeDecoderUninit(
    _env: *mut JniEnv,
    _class: JniObject,
    decoder_ptr: i64,
) {
    unsafe { decoder_close(decoder_from(decoder_ptr)) }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeEncoderInitFile(
    env: *mut JniEnv,
    _class: JniObject,
    path: JniObject,
    _format: i32,
    channels: i32,
    sample_rate: i32,
) -> i64 {
    unsafe {
        match jni::with_utf(env, path, |chars| {
            open_encoder(chars, channels, sample_rate)
        }) {
            Some(Some(encoder)) => ffi::bits_from(encoder as *mut c_void),
            _ => 0,
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeEncoderWriteFrames(
    env: *mut JniEnv,
    _class: JniObject,
    encoder_ptr: i64,
    data: JniObject,
    frame_count: i32,
) -> u8 {
    unsafe {
        let samples = jni::get_float_elements(env, data);
        let ok = encoder_write(encoder_from(encoder_ptr), samples, frame_count);
        jni::release_float_elements(env, data, samples, jni::JNI_ABORT);
        if ok {
            1
        } else {
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vectencia_klarinet_JniBridge_nativeEncoderUninit(
    _env: *mut JniEnv,
    _class: JniObject,
    encoder_ptr: i64,
) {
    unsafe { encoder_close(encoder_from(encoder_ptr)) }
}

fn open_decoder(path: *const std::os::raw::c_char) -> Option<*mut OpenedDecoder> {
    let text = unsafe { CStr::from_ptr(path) }.to_str().ok()?;
    if let Some(prefix) = read_prefix(text, 12) {
        if is_wav(&prefix) {
            if let Ok(file) = File::open(text) {
                if let Ok(reader) = WavReader::open(file) {
                    return Some(Box::into_raw(Box::new(OpenedDecoder::Wav(reader))));
                }
            }
        }
    }
    let decoder = unsafe { ffi::klarinet_ma_decoder_open(path) };
    if decoder.is_null() {
        None
    } else {
        Some(Box::into_raw(Box::new(OpenedDecoder::Miniaudio(decoder))))
    }
}

fn open_encoder(
    path: *const std::os::raw::c_char,
    channels: i32,
    sample_rate: i32,
) -> Option<*mut WavEncoder> {
    if channels <= 0 || sample_rate <= 0 {
        return None;
    }
    let text = unsafe { CStr::from_ptr(path) }.to_str().ok()?;
    let file = File::create(text).ok()?;
    let writer = WavWriter::new(BufWriter::new(file), sample_rate, channels).ok()?;
    Some(Box::into_raw(Box::new(WavEncoder {
        channels: channels as usize,
        writer: Some(writer),
    })))
}

unsafe fn decoder_from(value: i64) -> *mut OpenedDecoder {
    ffi::ptr_from(value) as *mut OpenedDecoder
}

unsafe fn encoder_from(value: i64) -> *mut WavEncoder {
    ffi::ptr_from(value) as *mut WavEncoder
}

unsafe fn decoder_sample_rate(decoder: *mut OpenedDecoder) -> i32 {
    match unsafe { decoder.as_ref() } {
        Some(OpenedDecoder::Wav(reader)) => reader.sample_rate(),
        Some(OpenedDecoder::Miniaudio(inner)) => unsafe {
            ffi::klarinet_ma_decoder_sample_rate(*inner)
        },
        None => 0,
    }
}

unsafe fn decoder_channels(decoder: *mut OpenedDecoder) -> i32 {
    match unsafe { decoder.as_ref() } {
        Some(OpenedDecoder::Wav(reader)) => reader.channel_count(),
        Some(OpenedDecoder::Miniaudio(inner)) => unsafe {
            ffi::klarinet_ma_decoder_channels(*inner)
        },
        None => 0,
    }
}

unsafe fn decoder_total_frames(decoder: *mut OpenedDecoder) -> i64 {
    match unsafe { decoder.as_ref() } {
        Some(OpenedDecoder::Wav(reader)) => reader.total_frames(),
        Some(OpenedDecoder::Miniaudio(inner)) => unsafe {
            ffi::klarinet_ma_decoder_total_frames(*inner)
        },
        None => 0,
    }
}

unsafe fn decoder_read(decoder: *mut OpenedDecoder, dst: *mut f32, frame_count: i32) -> i64 {
    match unsafe { decoder.as_mut() } {
        Some(OpenedDecoder::Wav(reader)) => read_wav(reader, dst, frame_count),
        Some(OpenedDecoder::Miniaudio(inner)) => unsafe {
            ffi::klarinet_ma_decoder_read(*inner, dst, frame_count)
        },
        None => 0,
    }
}

unsafe fn decoder_seek(decoder: *mut OpenedDecoder, frame_index: i64) -> bool {
    match unsafe { decoder.as_mut() } {
        Some(OpenedDecoder::Wav(reader)) => {
            if frame_index < 0 {
                return false;
            }
            reader.seek_frame(frame_index as u64).is_ok()
        }
        Some(OpenedDecoder::Miniaudio(inner)) => unsafe {
            ffi::klarinet_ma_decoder_seek(*inner, frame_index) != 0
        },
        None => false,
    }
}

unsafe fn decoder_close(decoder: *mut OpenedDecoder) {
    if decoder.is_null() {
        return;
    }
    let opened = unsafe { Box::from_raw(decoder) };
    if let OpenedDecoder::Miniaudio(inner) = *opened {
        unsafe { ffi::klarinet_ma_decoder_close(inner) };
    }
}

unsafe fn encoder_write(encoder: *mut WavEncoder, data: *const f32, frame_count: i32) -> bool {
    let Some(encoder) = (unsafe { encoder.as_mut() }) else {
        return false;
    };
    if frame_count < 0 {
        return false;
    }
    if frame_count == 0 {
        let Some(writer) = encoder.writer.as_mut() else {
            return true;
        };
        return writer.flush_header().is_ok();
    }
    if data.is_null() {
        return false;
    }
    let Some(writer) = encoder.writer.as_mut() else {
        return false;
    };
    let count = (frame_count as usize).saturating_mul(encoder.channels);
    let samples = unsafe { std::slice::from_raw_parts(data, count) };
    writer.write_frames(samples).is_ok()
}

unsafe fn encoder_close(encoder: *mut WavEncoder) {
    if encoder.is_null() {
        return;
    }
    drop(unsafe { Box::from_raw(encoder) });
}

fn read_wav(reader: &mut WavReader<File>, dst: *mut f32, frame_count: i32) -> i64 {
    if frame_count <= 0 || dst.is_null() {
        return 0;
    }
    let channels = reader.channel_count().max(1) as usize;
    let count = (frame_count as usize).saturating_mul(channels);
    let dst = unsafe { std::slice::from_raw_parts_mut(dst, count) };
    match reader.read_samples(dst) {
        Ok(samples) => (samples / channels) as i64,
        Err(_) => 0,
    }
}

fn read_prefix(path: &str, limit: usize) -> Option<Vec<u8>> {
    let mut file = std::fs::File::open(path).ok()?;
    let mut buf = vec![0u8; limit];
    let read = file.read(&mut buf).ok()?;
    buf.truncate(read);
    Some(buf)
}

fn is_wav(prefix: &[u8]) -> bool {
    prefix.len() >= 12 && &prefix[0..4] == b"RIFF" && &prefix[8..12] == b"WAVE"
}
