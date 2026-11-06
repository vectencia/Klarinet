@file:OptIn(ExperimentalWasmJsInterop::class)

package com.vectencia.klarinet

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.Promise

internal const val KLARINET_WORKLET_NAME = "klarinet-processor"

private const val WORKLET_SOURCE = """
class KlarinetProcessor extends AudioWorkletProcessor {
  constructor(options) {
    super();
    var opts = (options && options.processorOptions) || {};
    this.role = opts.role || 'io';
    this.direction = opts.direction || 'output';
    this.channels = opts.channels || 2;
    this.slotCount = 32;
    this.slotSamples = 1024;
    this.pendingSlots = [];
    this.captureSlots = [];
    this.playCounts = new Int32Array(this.slotCount);
    this.captureCounts = new Int32Array(this.slotCount);
    this.playRead = 0;
    this.playWrite = 0;
    this.playFilled = 0;
    this.captureRead = 0;
    this.captureWrite = 0;
    this.captureFilled = 0;
    this.extra = [];
    this.ids = {};
    this.lastGainDb = {};
    this.chain = 0;
    this.exports = null;
    this.view = new Float32Array(this.slotSamples);
    this.ptr = 0;
    for (var s = 0; s < this.slotCount; s++) {
      this.pendingSlots.push(new Float32Array(this.slotSamples));
      this.captureSlots.push(new Float32Array(this.slotSamples));
    }
    var self = this;
    this.port.onmessage = function(event) { self.onControl(event); };
  }
  refreshView() {
    if (!this.exports || !this.ptr) return;
    this.view = new Float32Array(this.exports.memory.buffer, this.ptr, this.slotSamples);
  }
  linearToDb(linear) {
    if (!(linear > 0)) return -1000;
    return 20 * Math.log(linear) / Math.LN10;
  }
  installModule(module) {
    if (this.exports || !module) return;
    var instance = new WebAssembly.Instance(module, {
      env: { klarinet_js_resampler_pull: function() { return 0; } }
    });
    this.exports = instance.exports;
    this.ptr = this.exports.klarinet_worklet_scratch(this.slotSamples);
    this.refreshView();
    if (this.pendingSlots.length === 0) {
      for (var s = 0; s < this.slotCount; s++) {
        this.pendingSlots.push(new Float32Array(this.slotSamples));
        this.captureSlots.push(new Float32Array(this.slotSamples));
      }
    }
  }
  destroyInstalled() {
    if (!this.exports) {
      this.ids = {};
      this.lastGainDb = {};
      this.chain = 0;
      return;
    }
    var exports = this.exports;
    exports.klarinet_worklet_use_chain(0);
    if (this.chain) {
      exports.klarinet_chain_destroy(this.chain);
      this.chain = 0;
    }
    for (var key in this.ids) {
      if (this.ids[key]) exports.klarinet_effect_destroy(this.ids[key]);
    }
    this.ids = {};
    this.lastGainDb = {};
    this.refreshView();
  }
  onControl(event) {
    var data = event.data;
    if (!data) return;
    if (data.type === 'wasm') {
      this.installModule(data.module);
      return;
    }
    if (data.type === 'wasm-bytes' && data.bytes) {
      this.installModule(new WebAssembly.Module(data.bytes));
      return;
    }
    if (data.type === 'stop' || data.type === 'start') return;
    if (data.type === 'flush') {
      this.flushControl();
      return;
    }
    if (!this.exports) return;
    if (data.type === 'out' && data.samples) {
      this.storePlay(data.samples);
      return;
    }
    if (data.type === 'drop') {
      this.dropId(data.id);
      return;
    }
    if (data.type === 'param') {
      this.setParam(data);
      return;
    }
    if (data.type === 'chain') {
      this.installChain(data);
    }
  }
  storePlay(samples) {
    var slot;
    var index;
    if (this.playFilled < this.slotCount) {
      index = this.playWrite;
      slot = this.pendingSlots[index];
      this.playWrite = (this.playWrite + 1) % this.slotCount;
      this.playFilled = this.playFilled + 1;
    } else {
      slot = new Float32Array(this.slotSamples);
      this.extra.push(slot);
      index = -1;
    }
    var n = samples.length < slot.length ? samples.length : slot.length;
    for (var i = 0; i < n; i++) slot[i] = +samples[i];
    if (index >= 0) this.playCounts[index] = n;
    else slot._count = n;
  }
  dropId(id) {
    var handle = this.ids[id];
    if (!handle) return;
    delete this.ids[id];
    delete this.lastGainDb[id];
    if (this.chain) this.exports.klarinet_chain_remove(this.chain, handle);
    this.exports.klarinet_effect_destroy(handle);
    this.refreshView();
  }
  setParam(data) {
    var handle = this.ids[data.id];
    if (!handle) return;
    this.exports.klarinet_effect_set_parameter(handle, 1, +data.fadeMs || 0);
    this.exports.klarinet_effect_set_enabled(handle, data.enabled | 0);
    var db = this.linearToDb(+data.linear);
    if (this.lastGainDb[data.id] !== db) {
      this.lastGainDb[data.id] = db;
      this.exports.klarinet_effect_set_parameter(handle, 0, db);
    }
  }
  installChain(data) {
    this.destroyInstalled();
    var effects = data.effects || [];
    var sampleRate = data.sampleRate | 0;
    var channels = data.channels | 0;
    if (!effects.length || sampleRate <= 0 || channels <= 0) return;
    var chain = this.exports.klarinet_chain_create();
    if (!chain) return;
    this.chain = chain;
    for (var i = 0; i < effects.length; i++) {
      var spec = effects[i];
      var fx = this.exports.klarinet_create_effect(0);
      if (!fx) continue;
      this.exports.klarinet_effect_prepare(fx, sampleRate, channels);
      this.exports.klarinet_effect_set_parameter(fx, 1, +spec.fadeMs || 0);
      var db = this.linearToDb(+spec.linear);
      this.exports.klarinet_effect_set_parameter(fx, 0, db);
      this.lastGainDb[spec.id] = db;
      this.exports.klarinet_effect_set_enabled(fx, spec.enabled | 0);
      this.exports.klarinet_chain_add(chain, fx);
      this.ids[spec.id] = fx;
    }
    this.exports.klarinet_chain_prepare(chain, sampleRate, channels);
    this.exports.klarinet_worklet_use_chain(chain);
    this.refreshView();
  }
  flushControl() {
    while (this.extra.length > 0 && this.playFilled < this.slotCount) {
      var moved = this.extra.shift();
      var index = this.playWrite;
      var slot = this.pendingSlots[index];
      var n = moved._count || 0;
      if (n > slot.length) n = slot.length;
      for (var i = 0; i < n; i++) slot[i] = moved[i];
      this.playCounts[index] = n;
      this.playWrite = (this.playWrite + 1) % this.slotCount;
      this.playFilled = this.playFilled + 1;
    }
    while (this.captureFilled > 0) {
      var capIndex = this.captureRead;
      var capSlot = this.captureSlots[capIndex];
      var capCount = this.captureCounts[capIndex];
      var copy = new Float32Array(capCount);
      for (var c = 0; c < capCount; c++) copy[c] = capSlot[c];
      this.captureRead = (this.captureRead + 1) % this.slotCount;
      this.captureFilled = this.captureFilled - 1;
      var capFrames = this.channels > 0 ? (capCount / this.channels) | 0 : 0;
      this.port.postMessage({ type: 'io', frames: capFrames, channels: this.channels, input: copy });
    }
  }
  readInput(inputs, frames, channels, count) {
    var input = inputs && inputs[0];
    var i;
    if (!input || !input.length) {
      for (i = 0; i < count; i++) this.view[i] = 0;
      return;
    }
    for (var f = 0; f < frames; f++) {
      for (var ch = 0; ch < channels; ch++) {
        var idx = f * channels + ch;
        if (idx >= count) return;
        var lane = input[ch];
        this.view[idx] = lane && f < lane.length ? +lane[f] : 0;
      }
    }
  }
  takePlayIntoView(count) {
    var i;
    if (!this.view) return;
    if (this.playFilled <= 0) {
      for (i = 0; i < count; i++) this.view[i] = 0;
      return;
    }
    var slot = this.pendingSlots[this.playRead];
    var n = this.playCounts[this.playRead];
    if (n > count) n = count;
    for (i = 0; i < n; i++) this.view[i] = slot[i];
    for (i = n; i < count; i++) this.view[i] = 0;
    this.playRead = (this.playRead + 1) % this.slotCount;
    this.playFilled = this.playFilled - 1;
  }
  storeCapture(count) {
    if (!this.view || this.captureFilled >= this.slotCount) return;
    var slot = this.captureSlots[this.captureWrite];
    var n = count < slot.length ? count : slot.length;
    for (var i = 0; i < n; i++) slot[i] = this.view[i];
    this.captureCounts[this.captureWrite] = n;
    this.captureWrite = (this.captureWrite + 1) % this.slotCount;
    this.captureFilled = this.captureFilled + 1;
  }
  writeOutput(output, frames, channels, count) {
    for (var oc = 0; oc < channels; oc++) {
      var dest = output[oc];
      if (!dest) continue;
      var limit = dest.length < frames ? dest.length : frames;
      for (var of = 0; of < limit; of++) {
        var idx = of * channels + oc;
        dest[of] = this.view && idx < count ? this.view[idx] : 0;
      }
    }
  }
  process(inputs, outputs) {
    var output = outputs && outputs[0];
    if (!output || !output.length || !output[0]) return true;
    var frames = output[0].length;
    var channels = output.length;
    if (frames <= 0 || channels <= 0) return true;
    this.channels = channels;
    var count = frames * channels;
    if (count > this.view.length) {
      frames = (this.view.length / channels) | 0;
      count = frames * channels;
    }
    var fromInput = this.role === 'insert' || this.direction === 'input';
    if (fromInput) {
      this.readInput(inputs, frames, channels, count);
      if (this.chain && this.ptr) this.exports.klarinet_worklet_process(this.ptr, frames, channels);
      if (this.role === 'io') {
        this.storeCapture(count);
        if (this.playFilled > 0) this.takePlayIntoView(count);
      }
    } else {
      this.takePlayIntoView(count);
      if (this.chain && this.ptr) this.exports.klarinet_worklet_process(this.ptr, frames, channels);
    }
    this.writeOutput(output, frames, channels, count);
    return true;
  }
}
registerProcessor('klarinet-processor', KlarinetProcessor);
"""

private var workletUrl: String? = null

internal fun ensureKlarinetWorklet(ctx: AudioContext): Promise<JsAny?> {
    val url = workletUrl ?: createWorkletModuleUrl(WORKLET_SOURCE).also { workletUrl = it }
    return addAudioWorkletModule(ctx, url)
}

internal fun workletPrefetchQuanta(bufferCapacityInFrames: Int): Int =
    (bufferCapacityInFrames / 128).coerceIn(2, 16)
