@file:OptIn(ExperimentalWasmJsInterop::class)

package com.vectencia.klarinet

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.Promise

internal const val KLARINET_WORKLET_NAME = "klarinet-processor"

private const val WORKLET_SOURCE = """
class KlarinetProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    this.pending = [];
    var self = this;
    this.port.onmessage = function(event) {
      if (event.data && event.data.type === 'out' && event.data.samples) {
        self.pending.push(event.data.samples);
      }
    };
  }
  process(inputs, outputs) {
    var output = outputs[0];
    if (!output || output.length === 0) {
      return true;
    }
    var frames = output[0].length;
    var channels = output.length;
    var input = inputs[0];
    var interleavedIn = null;
    if (input && input.length > 0 && input[0] && input[0].length > 0) {
      interleavedIn = new Float32Array(frames * input.length);
      for (var f = 0; f < frames; f++) {
        for (var c = 0; c < input.length; c++) {
          var ch = input[c];
          interleavedIn[f * input.length + c] = ch ? ch[f] : 0;
        }
      }
    }
    this.port.postMessage({ type: 'io', frames: frames, channels: channels, input: interleavedIn });
    var next = this.pending.shift();
    if (next && next.length) {
      var srcCh = Math.max(1, (next.length / frames) | 0);
      for (var oc = 0; oc < channels; oc++) {
        var dest = output[oc];
        var sc = oc < srcCh ? oc : 0;
        for (var of = 0; of < frames; of++) {
          dest[of] = next[of * srcCh + sc] || 0;
        }
      }
    }
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
