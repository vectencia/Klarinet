@file:OptIn(ExperimentalWasmJsInterop::class)

package com.vectencia.klarinet

import kotlin.js.ExperimentalWasmJsInterop

internal data class DecodedWav(
    val samples: FloatArray,
    val sampleRate: Int,
    val channelCount: Int,
)

internal fun encodeWav16(samples: FloatArray, sampleRate: Int, channelCount: Int): ByteArray {
    val count = samples.size
    val outCap = 44 + count * 2
    val wasm = wasmExports()
    val packed: dynamic = js(
        """
        var inBytes = count * 4;
        var inPtr = count === 0 ? 0 : wasm.klarinet_wasm_alloc(inBytes);
        var outPtr = wasm.klarinet_wasm_alloc(outCap);
        if ((count !== 0 && !inPtr) || !outPtr) {
          throw new Error('wasm alloc failed');
        }
        if (count !== 0) {
          var view = new Float32Array(wasm.memory.buffer, inPtr, count);
          for (var i = 0; i < count; i++) view[i] = samples[i];
        }
        var n = wasm.encode_wav16(inPtr, count, sampleRate | 0, channelCount | 0, outPtr, outCap);
        if (inPtr) wasm.klarinet_wasm_free(inPtr, inBytes);
        if (n < 0) {
          wasm.klarinet_wasm_free(outPtr, outCap);
          throw new Error('encode failed');
        }
        var copy = new Uint8Array(wasm.memory.buffer, outPtr, n);
        var out = new Array(n);
        for (var j = 0; j < n; j++) out[j] = copy[j] > 127 ? copy[j] - 256 : copy[j];
        wasm.klarinet_wasm_free(outPtr, outCap);
        out
        """,
    )
    val length = jsLength(packed)
    return ByteArray(length) { index -> (jsIndex(packed, index) as Int).toByte() }
}

internal fun decodeWav(bytes: ByteArray): DecodedWav {
    val byteLen = bytes.size
    val sampleCap = if (byteLen > 0) byteLen / 2 else 0
    val wasm = wasmExports()
    val decoded: dynamic = js(
        """
        var inPtr = byteLen === 0 ? 0 : wasm.klarinet_wasm_alloc(byteLen);
        var outBytes = sampleCap * 4;
        var outPtr = sampleCap === 0 ? 0 : wasm.klarinet_wasm_alloc(outBytes);
        var infoPtr = wasm.klarinet_wasm_alloc(16);
        if ((byteLen !== 0 && !inPtr) || (sampleCap !== 0 && !outPtr) || !infoPtr) {
          throw new Error('wasm alloc failed');
        }
        if (byteLen !== 0) {
          var input = new Uint8Array(wasm.memory.buffer, inPtr, byteLen);
          for (var i = 0; i < byteLen; i++) input[i] = bytes[i] & 255;
        }
        var n = wasm.decode_wav(inPtr, byteLen, outPtr, sampleCap, infoPtr, infoPtr + 4, infoPtr + 8, infoPtr + 12);
        var info = new Int32Array(wasm.memory.buffer, infoPtr, 4);
        var rate = info[0];
        var channels = info[1];
        var format = info[2];
        var bits = info[3];
        var samples = [];
        if (n > 0) {
          var view = new Float32Array(wasm.memory.buffer, outPtr, n);
          for (var s = 0; s < n; s++) samples.push(view[s]);
        }
        if (inPtr) wasm.klarinet_wasm_free(inPtr, byteLen);
        if (outPtr) wasm.klarinet_wasm_free(outPtr, outBytes);
        wasm.klarinet_wasm_free(infoPtr, 16);
        ({ code: n, rate: rate, channels: channels, format: format, bits: bits, samples: samples })
        """,
    )
    val code = decoded.code as Int
    if (code < 0) {
        wasmDecodeError(code, decoded.format as Int, decoded.bits as Int)
    }
    val sampleList = decoded.samples
    val length = jsLength(sampleList)
    val samples = FloatArray(length) { index -> (jsIndex(sampleList, index) as Float) }
    return DecodedWav(samples, decoded.rate as Int, decoded.channels as Int)
}

private fun wasmDecodeError(code: Int, format: Int, bits: Int): Nothing {
    throw AudioFileException(
        when (code) {
            -2 -> "WAV file is missing a data chunk"
            -3 -> "Invalid WAV fmt chunk"
            -4 -> "Unsupported WAV format (format=$format bits=$bits)"
            else -> "Not a WAV file"
        },
    )
}
