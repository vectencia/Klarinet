@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.vectencia.klarinet

import cnames.structs.*
import klarinet_native.*
import kotlinx.cinterop.*

actual class AudioEngine private constructor() {
    private var contextPtr: COpaquePointer? = null
    private val streams = mutableListOf<AudioStream>()

    actual companion object {
        actual fun create(): AudioEngine {
            val engine = AudioEngine()
            engine.contextPtr = klarinet_context_init()
                ?: throw StreamCreationException("Failed to create native audio context")
            return engine
        }
    }

    actual fun openStream(config: AudioStreamConfig, callback: AudioStreamCallback?): AudioStream {
        val ctx = contextPtr?.reinterpret<KlarinetContext>()
            ?: throw StreamCreationException("AudioEngine has been released")
        val stream = AudioStream(config)

        val stableRef = if (callback != null) {
            StableRef.create(AudioCallbackData(callback, stream))
        } else null

        val cbFunc: KlarinetDataCallback? = if (callback != null) {
            staticCFunction { userData, buffer, frameCount, channelCount, isCapture ->
                val ref = userData?.asStableRef<AudioCallbackData>() ?: return@staticCFunction
                val data = ref.get()
                val totalSamples = frameCount * channelCount
                val kotlinBuffer = FloatArray(totalSamples)

                if (isCapture == 1 && buffer != null) {
                    for (i in 0 until totalSamples) { kotlinBuffer[i] = buffer[i] }
                }

                data.callback.onAudioReady(kotlinBuffer, frameCount)

                if (isCapture == 0 && buffer != null) {
                    for (i in 0 until totalSamples) { buffer[i] = kotlinBuffer[i] }
                }

                var peak = 0f
                for (i in kotlinBuffer.indices) {
                    val abs = if (kotlinBuffer[i] >= 0f) kotlinBuffer[i] else -kotlinBuffer[i]
                    if (abs > peak) peak = abs
                }
                data.stream.peakLevelAtomic.set(peak)
            }
        } else null

        val devicePtr = klarinet_device_init(
            ctx, config.sampleRate, config.channelCount,
            config.bufferCapacityInFrames, config.direction.ordinal,
            cbFunc, stableRef?.asCPointer()
        ) ?: throw StreamCreationException("Failed to open native audio device")

        stream.devicePtr = devicePtr
        stream.callbackRef = stableRef
        streams.add(stream)
        return stream
    }

    actual fun getAvailableDevices(): List<AudioDeviceInfo> {
        val ctx = contextPtr?.reinterpret<KlarinetContext>() ?: return emptyList()
        val devices = mutableListOf<AudioDeviceInfo>()
        val playbackCount = klarinet_get_playback_device_count(ctx)
        for (i in 0 until playbackCount) {
            val name = klarinet_get_playback_device_name(ctx, i)?.toKString() ?: "Unknown"
            devices.add(AudioDeviceInfo(id = i, name = name, isInput = false, isOutput = true,
                sampleRates = listOf(44100, 48000), channelCounts = listOf(1, 2)))
        }
        val captureCount = klarinet_get_capture_device_count(ctx)
        for (i in 0 until captureCount) {
            val name = klarinet_get_capture_device_name(ctx, i)?.toKString() ?: "Unknown"
            devices.add(AudioDeviceInfo(id = playbackCount + i, name = name, isInput = true, isOutput = false,
                sampleRates = listOf(44100, 48000), channelCounts = listOf(1)))
        }
        return devices
    }

    actual fun getDefaultDevice(direction: StreamDirection): AudioDeviceInfo? {
        return getAvailableDevices().firstOrNull { when (direction) {
            StreamDirection.OUTPUT -> it.isOutput; StreamDirection.INPUT -> it.isInput
        }}
    }

    actual fun createEffect(type: AudioEffectType): AudioEffect = AudioEffect(type)
    actual fun createEffectChain(): AudioEffectChain = AudioEffectChain()

    actual fun release() {
        streams.forEach { it.close() }
        streams.clear()
        contextPtr?.reinterpret<KlarinetContext>()?.let { klarinet_context_uninit(it) }
        contextPtr = null
    }
}

internal data class AudioCallbackData(
    val callback: AudioStreamCallback,
    val stream: AudioStream,
)
