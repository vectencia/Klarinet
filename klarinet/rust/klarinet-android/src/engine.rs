//! Stream, effect, and chain tables.
//!
//! The engine mutex is taken on the control thread. `on_audio` does not take
//! it. Start, pause, stop, write, and read copy the owner and drop the mutex
//! before the Oboe call. Close holds the mutex across `close`, matching
//! `KlarinetEngine::closeStream`.

use std::collections::{HashMap, HashSet};
use std::os::raw::c_void;

use crate::audio::{on_audio_thunk, on_route_thunk, AudioState};
use crate::dsp;
use crate::jni::{self, JniObject};
use crate::oboe::{self, OboeInfo, OboeOpen};
use crate::{i64_to_usize, lock, usize_to_i64};

const TAG_ENGINE: &str = "KlarinetEngine";
const DEVICE_CAPACITY: i32 = 32;

pub struct OpenRequest {
    pub sample_rate: i32,
    pub channel_count: i32,
    pub audio_format: i32,
    pub buffer_capacity_in_frames: i32,
    pub performance_mode: i32,
    pub sharing_mode: i32,
    pub direction: i32,
    pub device_id: i32,
}

struct StreamEntry {
    owner: *mut c_void,
    audio: Box<AudioState>,
}

struct Tables {
    streams: HashMap<i64, StreamEntry>,
    effects: HashSet<i64>,
    chains: HashSet<i64>,
}

pub struct Engine {
    tables: std::sync::Mutex<Tables>,
}

impl Engine {
    pub fn new() -> Self {
        Self {
            tables: std::sync::Mutex::new(Tables {
                streams: HashMap::new(),
                effects: HashSet::new(),
                chains: HashSet::new(),
            }),
        }
    }

    pub unsafe fn open(
        &self,
        env: *mut jni::JniEnv,
        request: OpenRequest,
        callback: JniObject,
    ) -> i64 {
        let mut audio = AudioState::create(env, callback);
        let mut devices = [0i32; DEVICE_CAPACITY as usize];
        let mut info = OboeInfo {
            frames_per_burst: 0,
            channel_count: 0,
            direction: 0,
            sample_rate: 0,
            device_count: 0,
            device_capacity: DEVICE_CAPACITY,
            device_ids: devices.as_mut_ptr(),
        };
        let user = (&mut *audio) as *mut AudioState as *mut c_void;
        let spec = OboeOpen {
            sample_rate: request.sample_rate,
            channel_count: request.channel_count,
            audio_format: request.audio_format,
            buffer_capacity_in_frames: request.buffer_capacity_in_frames,
            performance_mode: request.performance_mode,
            sharing_mode: request.sharing_mode,
            direction: request.direction,
            device_id: request.device_id,
            audio: Some(on_audio_thunk),
            route: Some(on_route_thunk),
            user,
        };
        let owner = oboe::open(&spec, &mut info);
        if owner.is_null() {
            return 0;
        }
        audio.prepare(info.frames_per_burst, info.channel_count, info.direction);
        let count = info.device_count.clamp(0, DEVICE_CAPACITY) as usize;
        audio.note_routed(&devices[..count]);
        let handle = usize_to_i64(owner as usize);
        lock(&self.tables)
            .streams
            .insert(handle, StreamEntry { owner, audio });
        oboe::log_info(
            TAG_ENGINE,
            &format!(
                "Opened stream: handle={handle}, sampleRate={}, channels={}",
                info.sample_rate, info.channel_count
            ),
        );
        handle
    }

    pub fn start(&self, handle: i64) -> i32 {
        self.with_owner(handle, oboe::invalid_handle(), oboe::request_start)
    }

    pub fn pause(&self, handle: i64) -> i32 {
        self.with_owner(handle, oboe::invalid_handle(), oboe::request_pause)
    }

    pub fn stop(&self, handle: i64) -> i32 {
        self.with_owner(handle, oboe::invalid_handle(), oboe::request_stop)
    }

    pub fn close(&self, handle: i64) {
        let mut tables = lock(&self.tables);
        let Some(entry) = tables.streams.remove(&handle) else {
            return;
        };
        oboe::close(entry.owner);
        entry.audio.shutdown();
    }

    pub fn write(&self, handle: i64, data: *const f32, num_frames: i32, timeout_nanos: i64) -> i32 {
        match self.owner(handle) {
            Some(owner) => oboe::write(owner, data, num_frames, timeout_nanos),
            None => {
                self.log_missing(handle);
                -1
            }
        }
    }

    pub fn read(&self, handle: i64, data: *mut f32, num_frames: i32, timeout_nanos: i64) -> i32 {
        match self.owner(handle) {
            Some(owner) => oboe::read(owner, data, num_frames, timeout_nanos),
            None => {
                self.log_missing(handle);
                -1
            }
        }
    }

    pub fn state(&self, handle: i64) -> i32 {
        match self.owner(handle) {
            Some(owner) => oboe::state(owner),
            None => {
                self.log_missing(handle);
                0
            }
        }
    }

    pub fn latency_ms(&self, handle: i64) -> f64 {
        match self.owner(handle) {
            Some(owner) => oboe::latency_ms(owner),
            None => {
                self.log_missing(handle);
                0.0
            }
        }
    }

    pub fn create_effect(&self, effect_type: i32) -> i64 {
        let effect = dsp::create_effect(effect_type);
        if effect.is_null() {
            return 0;
        }
        let handle = usize_to_i64(effect as usize);
        lock(&self.tables).effects.insert(handle);
        handle
    }

    pub fn destroy_effect(&self, handle: i64) {
        let mut tables = lock(&self.tables);
        if tables.effects.remove(&handle) {
            dsp::effect_destroy(bits_ptr(handle));
        }
    }

    pub fn set_effect_parameter(&self, handle: i64, param_id: i32, value: f32) {
        let tables = lock(&self.tables);
        if tables.effects.contains(&handle) {
            dsp::effect_set_parameter(bits_ptr(handle), param_id, value);
        }
    }

    pub fn effect_parameter(&self, handle: i64, param_id: i32) -> f32 {
        let tables = lock(&self.tables);
        if tables.effects.contains(&handle) {
            dsp::effect_get_parameter(bits_ptr(handle), param_id)
        } else {
            0.0
        }
    }

    pub fn set_effect_enabled(&self, handle: i64, enabled: bool) {
        let tables = lock(&self.tables);
        if tables.effects.contains(&handle) {
            dsp::effect_set_enabled(bits_ptr(handle), enabled);
        }
    }

    pub fn effect_enabled(&self, handle: i64) -> bool {
        let tables = lock(&self.tables);
        tables.effects.contains(&handle) && dsp::effect_is_enabled(bits_ptr(handle))
    }

    pub fn create_chain(&self) -> i64 {
        let chain = dsp::chain_create();
        if chain.is_null() {
            return 0;
        }
        let handle = usize_to_i64(chain as usize);
        lock(&self.tables).chains.insert(handle);
        handle
    }

    pub fn destroy_chain(&self, handle: i64) {
        let mut tables = lock(&self.tables);
        if tables.chains.remove(&handle) {
            // `KlarinetEngine::destroyEffectChain` frees the chain and leaves
            // any stream pointer already stored. Callers clear the stream first.
            dsp::chain_destroy(bits_ptr(handle));
        }
    }

    pub fn chain_add(&self, chain: i64, effect: i64) {
        let tables = lock(&self.tables);
        if tables.chains.contains(&chain) && tables.effects.contains(&effect) {
            dsp::chain_add(bits_ptr(chain), bits_ptr(effect));
        }
    }

    pub fn chain_remove(&self, chain: i64, effect: i64) {
        let tables = lock(&self.tables);
        if tables.chains.contains(&chain) && tables.effects.contains(&effect) {
            dsp::chain_remove(bits_ptr(chain), bits_ptr(effect));
        }
    }

    pub fn chain_clear(&self, chain: i64) {
        let tables = lock(&self.tables);
        if tables.chains.contains(&chain) {
            dsp::chain_clear(bits_ptr(chain));
        }
    }

    pub fn chain_prepare(&self, chain: i64, sample_rate: i32, channel_count: i32) {
        let tables = lock(&self.tables);
        if tables.chains.contains(&chain) {
            dsp::chain_prepare(bits_ptr(chain), sample_rate, channel_count);
        }
    }

    pub fn chain_count(&self, chain: i64) -> i32 {
        let tables = lock(&self.tables);
        if tables.chains.contains(&chain) {
            dsp::chain_count(bits_ptr(chain))
        } else {
            0
        }
    }

    pub fn chain_enqueue(&self, chain: i64, effect: i64, param_id: i32, value: f32) {
        let tables = lock(&self.tables);
        if tables.chains.contains(&chain) && tables.effects.contains(&effect) {
            dsp::chain_enqueue(bits_ptr(chain), bits_ptr(effect), param_id, value);
        }
    }

    pub fn set_stream_chain(&self, stream: i64, chain: i64) {
        let tables = lock(&self.tables);
        if !tables.chains.contains(&chain) {
            return;
        }
        if let Some(entry) = tables.streams.get(&stream) {
            entry.audio.set_chain(bits_ptr(chain));
        }
    }

    pub fn clear_stream_chain(&self, stream: i64) {
        let tables = lock(&self.tables);
        if let Some(entry) = tables.streams.get(&stream) {
            entry.audio.set_chain(std::ptr::null_mut());
        }
    }

    fn owner(&self, handle: i64) -> Option<*mut c_void> {
        lock(&self.tables)
            .streams
            .get(&handle)
            .map(|entry| entry.owner)
    }

    fn with_owner(&self, handle: i64, missing: i32, call: fn(*mut c_void) -> i32) -> i32 {
        match self.owner(handle) {
            Some(owner) => call(owner),
            None => {
                self.log_missing(handle);
                missing
            }
        }
    }

    fn log_missing(&self, handle: i64) {
        oboe::log_error(TAG_ENGINE, &format!("Stream handle not found: {handle}"));
    }

    #[cfg(test)]
    pub fn insert_stream_for_test(&self, handle: i64) {
        let audio = crate::audio::silent_for_test();
        let owner = handle as *mut c_void;
        lock(&self.tables)
            .streams
            .insert(handle, StreamEntry { owner, audio });
    }

    #[cfg(test)]
    pub fn chain_for_test(&self, stream: i64) -> *mut c_void {
        lock(&self.tables)
            .streams
            .get(&stream)
            .map(|entry| entry.audio.chain())
            .unwrap_or(std::ptr::null_mut())
    }
}

impl Drop for Engine {
    fn drop(&mut self) {
        let mut tables = lock(&self.tables);
        let streams = std::mem::take(&mut tables.streams);
        let chains = std::mem::take(&mut tables.chains);
        let effects = std::mem::take(&mut tables.effects);
        for (_, entry) in streams {
            oboe::stop_blocking(entry.owner);
            oboe::close(entry.owner);
            entry.audio.shutdown();
        }
        drop(tables);
        for chain in chains {
            dsp::chain_destroy(bits_ptr(chain));
        }
        for effect in effects {
            dsp::effect_destroy(bits_ptr(effect));
        }
    }
}

fn bits_ptr(handle: i64) -> *mut c_void {
    i64_to_usize(handle) as *mut c_void
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn effect_and_chain_calls_require_membership() {
        dsp::reset_effect_calls();
        let engine = Engine::new();
        dsp::fail_next_effect(true);
        assert_eq!(engine.create_effect(1), 0);
        dsp::fail_next_effect(false);
        let effect = engine.create_effect(1);
        let chain = engine.create_chain();
        assert_ne!(effect, 0);
        assert_ne!(chain, 0);

        engine.chain_add(0, effect);
        engine.chain_add(chain, 0x99);
        assert_eq!(dsp::add_calls(), 0);
        engine.chain_add(chain, effect);
        assert_eq!(dsp::add_calls(), 1);

        engine.set_effect_parameter(0x99, 1, 0.5);
        assert_eq!(dsp::set_calls(), 0);
        engine.set_effect_parameter(effect, 1, 0.5);
        assert_eq!(dsp::set_calls(), 1);

        engine.insert_stream_for_test(7);
        engine.set_stream_chain(7, 0x99);
        assert!(engine.chain_for_test(7).is_null());
        engine.set_stream_chain(7, chain);
        assert_eq!(engine.chain_for_test(7), bits_ptr(chain));
        engine.clear_stream_chain(7);
        assert!(engine.chain_for_test(7).is_null());

        let effects_before = dsp::destroyed_effects();
        engine.destroy_effect(0x99);
        assert_eq!(dsp::destroyed_effects(), effects_before);
        engine.destroy_effect(effect);
        assert_eq!(dsp::destroyed_effects(), effects_before + 1);

        let chains_before = dsp::destroyed_chains();
        engine.destroy_chain(0x99);
        assert_eq!(dsp::destroyed_chains(), chains_before);
        engine.destroy_chain(chain);
        assert_eq!(dsp::destroyed_chains(), chains_before + 1);
    }
}
