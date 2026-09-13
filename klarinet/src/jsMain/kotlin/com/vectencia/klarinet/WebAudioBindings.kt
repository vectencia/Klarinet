@file:OptIn(ExperimentalWasmJsInterop::class)

package com.vectencia.klarinet

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.Promise
import kotlin.js.unsafeCast

internal fun newAudioContext(): AudioContext {
    return try {
        js("new (window.AudioContext || window.webkitAudioContext)()")
    } catch (error: Throwable) {
        throw StreamCreationException("Web Audio API is not available in this environment", error)
    }
}

internal external class AudioContext {
    val destination: AudioNode
    val sampleRate: Float
    val state: String
    val currentTime: Double
    fun createGain(): GainNode
    fun createBiquadFilter(): BiquadFilterNode
    fun createDynamicsCompressor(): DynamicsCompressorNode
    fun createDelay(maxDelayTime: Double = definedExternally): DelayNode
    fun createStereoPanner(): StereoPannerNode
    fun createOscillator(): OscillatorNode
    fun createConvolver(): ConvolverNode
    fun createConstantSource(): ConstantSourceNode
    fun createAnalyser(): AnalyserNode
    fun createMediaStreamSource(stream: MediaStream): MediaStreamAudioSourceNode
    fun createBuffer(numberOfChannels: Int, length: Int, sampleRate: Float): AudioBuffer
    fun decodeAudioData(audioData: ArrayBuffer): Promise<AudioBuffer>
    fun resume(): Promise<JsAny?>
    fun suspend(): Promise<JsAny?>
    fun close(): Promise<JsAny?>
}

internal external interface AudioNode {
    val context: AudioContext
    val numberOfInputs: Int
    val numberOfOutputs: Int
    fun connect(destination: AudioNode, output: Int = definedExternally, input: Int = definedExternally): AudioNode
    fun connect(destination: AudioParam): AudioParam
    fun disconnect()
    fun disconnect(destination: AudioNode)
}

internal external interface AudioParam {
    var value: Float
    fun setValueAtTime(value: Float, startTime: Double): AudioParam
    fun linearRampToValueAtTime(value: Float, endTime: Double): AudioParam
    fun cancelScheduledValues(cancelTime: Double): AudioParam
}

internal external interface GainNode : AudioNode {
    val gain: AudioParam
}

internal external interface BiquadFilterNode : AudioNode {
    var type: String
    val frequency: AudioParam
    val Q: AudioParam
    val gain: AudioParam
}

internal external interface DynamicsCompressorNode : AudioNode {
    val threshold: AudioParam
    val knee: AudioParam
    val ratio: AudioParam
    val attack: AudioParam
    val release: AudioParam
}

internal external interface DelayNode : AudioNode {
    val delayTime: AudioParam
}

internal external interface StereoPannerNode : AudioNode {
    val pan: AudioParam
}

internal external interface OscillatorNode : AudioNode {
    var type: String
    val frequency: AudioParam
    fun start(when_: Double = definedExternally)
    fun stop(when_: Double = definedExternally)
}

internal external interface ConvolverNode : AudioNode {
    var buffer: AudioBuffer?
}

internal external interface ConstantSourceNode : AudioNode {
    val offset: AudioParam
    fun start(when_: Double = definedExternally)
    fun stop(when_: Double = definedExternally)
}

internal external interface AnalyserNode : AudioNode {
    var fftSize: Int
    val frequencyBinCount: Int
    fun getFloatTimeDomainData(array: Float32Array)
}

internal abstract external class AudioWorkletNode : AudioNode {
    val port: MessagePort
}

internal external interface MessagePort {
    var onmessage: ((MessageEvent) -> Unit)?
    fun postMessage(message: dynamic)
}

internal external interface MessageEvent {
    val data: dynamic
}

internal external interface MediaStreamAudioSourceNode : AudioNode

internal external interface AudioBuffer {
    val sampleRate: Float
    val length: Int
    val duration: Double
    val numberOfChannels: Int
    fun getChannelData(channel: Int): Float32Array
}

internal fun createWorkletModuleUrl(source: String): String =
    js("URL.createObjectURL(new Blob([source], { type: 'application/javascript' }))")

internal fun addAudioWorkletModule(ctx: AudioContext, url: String): Promise<JsAny?> =
    js("ctx.audioWorklet.addModule(url)")

internal fun createAudioWorkletNode(ctx: AudioContext, channelCount: Int): AudioWorkletNode =
    js(
        """
        new AudioWorkletNode(ctx, 'klarinet-processor', {
            numberOfInputs: 1,
            numberOfOutputs: 1,
            outputChannelCount: [channelCount],
            channelCount: channelCount,
            channelCountMode: 'explicit'
        })
        """,
    )

internal fun postWorkletOutput(port: MessagePort, samples: Float32Array) {
    js("port.postMessage({ type: 'out', samples: samples })")
}

internal fun floatArrayToFloat32(src: FloatArray, samples: Int): Float32Array {
    val out: Float32Array = js("new Float32Array(samples)")
    for (i in 0 until samples) {
        float32Set(out, i, src[i])
    }
    return out
}

internal fun copyFloat32ToFloatArray(src: Float32Array, dest: FloatArray, samples: Int) {
    val n = minOf(samples, dest.size, src.length)
    for (i in 0 until n) {
        dest[i] = float32Get(src, i)
    }
}

internal external class Float32Array {
    val length: Int
    fun get(index: Int): Float
    fun set(index: Int, value: Float)
}

internal external class ArrayBuffer

internal external interface MediaStream {
    fun getTracks(): Array<MediaStreamTrack>
    fun getAudioTracks(): Array<MediaStreamTrack>
}

internal external interface MediaStreamTrack {
    val label: String
    fun stop()
}

internal external interface MediaDeviceInfo {
    val deviceId: String
    val kind: String
    val label: String
}

internal external interface MediaDevices {
    fun getUserMedia(constraints: dynamic): Promise<MediaStream>
    fun enumerateDevices(): Promise<Array<MediaDeviceInfo>>
}

internal external interface Navigator {
    val mediaDevices: MediaDevices
}

internal external val navigator: Navigator

internal external fun fetch(input: String): Promise<Response>

internal external interface Response {
    fun arrayBuffer(): Promise<ArrayBuffer>
}

internal fun float32Get(array: Float32Array, index: Int): Float =
    js("array[index]")

internal fun float32Set(array: Float32Array, index: Int, value: Float) {
    js("array[index] = value")
}

internal fun jsLength(value: dynamic): Int = js("value.length")

internal fun jsIndex(value: dynamic, index: Int): dynamic = js("value[index]")

internal fun waitMs(ms: Int): Promise<JsAny?> =
    js("new Promise(function(resolve){ setTimeout(resolve, ms); })")

internal fun trySetSinkId(ctx: AudioContext, sinkId: String) {
    js("if (ctx.setSinkId) { ctx.setSinkId(sinkId); }")
}

internal fun byteArrayToArrayBuffer(bytes: ByteArray): ArrayBuffer {
    val length = bytes.size
    val view: dynamic = js("new Uint8Array(length)")
    for (i in 0 until length) {
        view[i] = bytes[i]
    }
    return view.buffer.unsafeCast<ArrayBuffer>()
}

internal fun arrayBufferToByteArray(buffer: ArrayBuffer): ByteArray {
    val view: dynamic = js("new Uint8Array(buffer)")
    val length = view.length as Int
    val bytes = ByteArray(length)
    for (i in 0 until length) {
        bytes[i] = (view[i] as Int).toByte()
    }
    return bytes
}
