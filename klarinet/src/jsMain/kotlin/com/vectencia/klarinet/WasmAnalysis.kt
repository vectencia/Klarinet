@file:OptIn(ExperimentalWasmJsInterop::class)

package com.vectencia.klarinet

import kotlin.js.ExperimentalWasmJsInterop

private const val KIND_FFT = 1
private const val KIND_ANALYZER = 2
private const val KIND_RESAMPLER = 3

internal fun registerWasmHandle(owner: Any, kind: Int, handle: Int) {
    wasmExports()
    js(
        """
        if (!globalThis.__klarinetFinalizers) {
          globalThis.__klarinetFinalizers = new FinalizationRegistry(function(held) {
            var exports = globalThis.__klarinetWav;
            if (!exports || !held || !held.handle) return;
            if (held.kind === 1) exports.klarinet_fft_destroy(held.handle);
            else if (held.kind === 2) exports.klarinet_analyzer_destroy(held.handle);
            else if (held.kind === 3) exports.klarinet_resampler_destroy(held.handle);
          });
        }
        globalThis.__klarinetFinalizers.register(owner, { kind: kind, handle: handle });
        """,
    )
}

internal fun wasmFftCreate(size: Int): Int {
    val wasm = wasmExports()
    return js("wasm.klarinet_fft_create(size | 0) | 0") as Int
}

internal fun wasmFftForward(handle: Int, real: FloatArray, imag: FloatArray, length: Int) {
    val wasm = wasmExports()
    js(
        """
        var bytes = length * 4;
        var realPtr = wasm.klarinet_wasm_alloc(bytes);
        var imagPtr = wasm.klarinet_wasm_alloc(bytes);
        if (!realPtr || !imagPtr) {
          if (realPtr) wasm.klarinet_wasm_free(realPtr, bytes);
          if (imagPtr) wasm.klarinet_wasm_free(imagPtr, bytes);
          return;
        }
        var realView = new Float32Array(wasm.memory.buffer, realPtr, length);
        var imagView = new Float32Array(wasm.memory.buffer, imagPtr, length);
        for (var i = 0; i < length; i++) {
          realView[i] = real[i];
          imagView[i] = imag[i];
        }
        wasm.klarinet_fft_forward(handle, realPtr, imagPtr, length | 0);
        for (var j = 0; j < length; j++) {
          real[j] = realView[j];
          imag[j] = imagView[j];
        }
        wasm.klarinet_wasm_free(realPtr, bytes);
        wasm.klarinet_wasm_free(imagPtr, bytes);
        """,
    )
}

internal fun wasmFftMagnitude(handle: Int, real: FloatArray, imag: FloatArray, out: FloatArray) {
    val length = real.size
    val outLen = out.size
    val wasm = wasmExports()
    js(
        """
        var realPtr = wasm.klarinet_wasm_alloc(length * 4);
        var imagPtr = wasm.klarinet_wasm_alloc(length * 4);
        var outPtr = wasm.klarinet_wasm_alloc(outLen * 4);
        if (!realPtr || !imagPtr || !outPtr) {
          if (realPtr) wasm.klarinet_wasm_free(realPtr, length * 4);
          if (imagPtr) wasm.klarinet_wasm_free(imagPtr, length * 4);
          if (outPtr) wasm.klarinet_wasm_free(outPtr, outLen * 4);
          return;
        }
        var realView = new Float32Array(wasm.memory.buffer, realPtr, length);
        var imagView = new Float32Array(wasm.memory.buffer, imagPtr, length);
        var outView = new Float32Array(wasm.memory.buffer, outPtr, outLen);
        outView.fill(0);
        for (var i = 0; i < length; i++) {
          realView[i] = real[i];
          imagView[i] = imag[i];
        }
        wasm.klarinet_fft_magnitude(handle, realPtr, imagPtr, outPtr, outLen | 0);
        for (var j = 0; j < outLen; j++) out[j] = outView[j];
        wasm.klarinet_wasm_free(realPtr, length * 4);
        wasm.klarinet_wasm_free(imagPtr, length * 4);
        wasm.klarinet_wasm_free(outPtr, outLen * 4);
        """,
    )
}

internal fun wasmMathRms(data: FloatArray, offset: Int, length: Int): Float {
    val wasm = wasmExports()
    return js(
        """
        var bytes = (offset + length) * 4;
        var ptr = wasm.klarinet_wasm_alloc(bytes);
        if (!ptr) return 0;
        var view = new Float32Array(wasm.memory.buffer, ptr, offset + length);
        for (var i = offset; i < offset + length; i++) view[i] = data[i];
        var value = wasm.klarinet_math_rms(ptr, offset | 0, length | 0);
        wasm.klarinet_wasm_free(ptr, bytes);
        value
        """,
    ) as Float
}

internal fun wasmMathPeak(data: FloatArray, offset: Int, length: Int): Float {
    val wasm = wasmExports()
    return js(
        """
        var bytes = (offset + length) * 4;
        var ptr = wasm.klarinet_wasm_alloc(bytes);
        if (!ptr) return 0;
        var view = new Float32Array(wasm.memory.buffer, ptr, offset + length);
        for (var i = offset; i < offset + length; i++) view[i] = data[i];
        var value = wasm.klarinet_math_peak(ptr, offset | 0, length | 0);
        wasm.klarinet_wasm_free(ptr, bytes);
        value
        """,
    ) as Float
}

internal fun wasmMathLinearToDb(linear: Float): Float {
    val wasm = wasmExports()
    return js("wasm.klarinet_math_linear_to_db(linear)") as Float
}

internal fun wasmMathDbToLinear(db: Float): Float {
    val wasm = wasmExports()
    return js("wasm.klarinet_math_db_to_linear(db)") as Float
}

internal fun wasmMathHann(out: FloatArray) {
    val size = out.size
    val wasm = wasmExports()
    js(
        """
        var bytes = size * 4;
        var ptr = wasm.klarinet_wasm_alloc(bytes);
        if (!ptr) return;
        wasm.klarinet_math_hann(ptr, size | 0);
        var view = new Float32Array(wasm.memory.buffer, ptr, size);
        for (var i = 0; i < size; i++) out[i] = view[i];
        wasm.klarinet_wasm_free(ptr, bytes);
        """,
    )
}

internal fun wasmAnalyzerCreate(fftSize: Int, sampleRate: Int): Int {
    val wasm = wasmExports()
    return js("wasm.klarinet_analyzer_create(fftSize | 0, sampleRate | 0) | 0") as Int
}

internal fun wasmAnalyzerAnalyze(
    handle: Int,
    buffer: FloatArray,
    numFrames: Int,
    channelCount: Int,
    spectrum: FloatArray,
    levels: FloatArray,
) {
    val bufferSamples = buffer.size
    val spectrumLen = spectrum.size
    val wasm = wasmExports()
    js(
        """
        var inBytes = bufferSamples * 4;
        var specBytes = spectrumLen * 4;
        var inPtr = bufferSamples === 0 ? 0 : wasm.klarinet_wasm_alloc(inBytes);
        var specPtr = wasm.klarinet_wasm_alloc(specBytes);
        var levelPtr = wasm.klarinet_wasm_alloc(16);
        if ((bufferSamples !== 0 && !inPtr) || !specPtr || !levelPtr) {
          if (inPtr) wasm.klarinet_wasm_free(inPtr, inBytes);
          if (specPtr) wasm.klarinet_wasm_free(specPtr, specBytes);
          if (levelPtr) wasm.klarinet_wasm_free(levelPtr, 16);
          return;
        }
        if (inPtr) {
          var input = new Float32Array(wasm.memory.buffer, inPtr, bufferSamples);
          for (var i = 0; i < bufferSamples; i++) input[i] = buffer[i];
        }
        var spec = new Float32Array(wasm.memory.buffer, specPtr, spectrumLen);
        var level = new Float32Array(wasm.memory.buffer, levelPtr, 4);
        spec.fill(0);
        level.fill(0);
        wasm.klarinet_analyzer_analyze(
          handle, inPtr, bufferSamples | 0, numFrames | 0, channelCount | 0,
          specPtr, spectrumLen | 0, levelPtr, levelPtr + 4, levelPtr + 8, levelPtr + 12
        );
        for (var s = 0; s < spectrumLen; s++) spectrum[s] = spec[s];
        for (var n = 0; n < 4; n++) levels[n] = level[n];
        if (inPtr) wasm.klarinet_wasm_free(inPtr, inBytes);
        wasm.klarinet_wasm_free(specPtr, specBytes);
        wasm.klarinet_wasm_free(levelPtr, 16);
        """,
    )
}

internal fun wasmBandEnergy(
    spectrum: FloatArray,
    resolution: Float,
    lowHz: Float,
    highHz: Float,
): Float {
    val spectrumLen = spectrum.size
    val wasm = wasmExports()
    return js(
        """
        var bytes = spectrumLen * 4;
        var ptr = spectrumLen === 0 ? 0 : wasm.klarinet_wasm_alloc(bytes);
        if (spectrumLen !== 0 && !ptr) return 0;
        if (ptr) {
          var view = new Float32Array(wasm.memory.buffer, ptr, spectrumLen);
          for (var i = 0; i < spectrumLen; i++) view[i] = spectrum[i];
        }
        var energy = wasm.klarinet_analyzer_band_energy(ptr, spectrumLen | 0, resolution, lowHz, highHz);
        if (ptr) wasm.klarinet_wasm_free(ptr, bytes);
        energy
        """,
    ) as Float
}

internal fun wasmResamplerCreate(sourceRate: Int, channels: Int): Int {
    val wasm = wasmExports()
    return js("wasm.klarinet_resampler_create(sourceRate | 0, channels | 0) | 0") as Int
}

internal fun wasmResamplerHasBuffered(handle: Int): Int {
    val wasm = wasmExports()
    return js("wasm.klarinet_resampler_has_buffered(handle) | 0") as Int
}

internal fun wasmResampleLinear(
    handle: Int,
    destRate: Int,
    dest: FloatArray,
    destFrames: Int,
    channels: Int,
    readFrames: (Int) -> FloatArray,
): Int {
    val wasm = wasmExports()
    return js(
        """
        var bytes = destFrames * channels * 4;
        var destPtr = wasm.klarinet_wasm_alloc(bytes);
        if (!destPtr) return 0;
        globalThis.__klarinetReadChannels = channels | 0;
        globalThis.__klarinetReadFrames = function(maxFrames) {
          var src = readFrames(maxFrames | 0);
          if (!src || src.length <= 0) return null;
          var copy = new Array(src.length);
          for (var i = 0; i < src.length; i++) copy[i] = src[i];
          return copy;
        };
        var frames = 0;
        try {
          frames = wasm.klarinet_resample_linear(
            handle, destRate | 0, destPtr, destFrames | 0, wasm.klarinet_js_pull_fn(), 0
          );
          if (frames > 0) {
            var view = new Float32Array(wasm.memory.buffer, destPtr, frames * channels);
            for (var j = 0; j < view.length; j++) dest[j] = view[j];
          }
        } finally {
          globalThis.__klarinetReadFrames = null;
          wasm.klarinet_wasm_free(destPtr, bytes);
        }
        frames < 0 ? 0 : frames | 0
        """,
    ) as Int
}

internal const val WASM_KIND_FFT: Int = KIND_FFT
internal const val WASM_KIND_ANALYZER: Int = KIND_ANALYZER
internal const val WASM_KIND_RESAMPLER: Int = KIND_RESAMPLER

internal fun wasmExports(): dynamic {
    val base64 = KLARINET_WASM_BASE64
    return js(
        """
        if (!globalThis.__klarinetWav) {
          var encoded = Uint8Array.from(atob(base64), function(c) { return c.charCodeAt(0); });
          var instance = new WebAssembly.Instance(new WebAssembly.Module(encoded), {
            env: {
              klarinet_js_resampler_pull: function(user, dst, maxFrames) {
                var read = globalThis.__klarinetReadFrames;
                var channels = globalThis.__klarinetReadChannels | 0;
                var wasm = globalThis.__klarinetWav;
                if (!read || !wasm || maxFrames <= 0 || channels <= 0) return 0;
                var samples = read(maxFrames);
                if (!samples || samples.length <= 0) return 0;
                var frames = Math.min(maxFrames, (samples.length / channels) | 0);
                if (frames <= 0) return 0;
                var count = frames * channels;
                var view = new Float32Array(wasm.memory.buffer, dst, count);
                for (var i = 0; i < count; i++) view[i] = samples[i];
                return frames;
              }
            }
          });
          globalThis.__klarinetWav = instance.exports;
        }
        globalThis.__klarinetWav
        """,
    )
}
