package com.vectencia.klarinet

actual class AudioEngine private constructor() {
    private var engineHandle: Long = 0L
    private val streams = mutableListOf<AudioStream>()

    actual companion object {
        actual fun create(): AudioEngine {
            val engine = AudioEngine()
            engine.engineHandle = JniBridge.nativeCreateEngine()
            if (engine.engineHandle == 0L) {
                throw StreamCreationException("Failed to create native audio engine")
            }
            return engine
        }
    }

    actual fun openStream(config: AudioStreamConfig, callback: AudioStreamCallback?): AudioStream {
        check(engineHandle != 0L) { "AudioEngine has been released" }
        val stream = AudioStream(config)

        val wrappedCallback = if (callback != null) {
            object : AudioStreamCallback {
                override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
                    val result = callback.onAudioReady(buffer, numFrames)
                    var peak = 0f
                    for (i in buffer.indices) {
                        val abs = if (buffer[i] >= 0f) buffer[i] else -buffer[i]
                        if (abs > peak) peak = abs
                    }
                    stream.peakLevelAtomic.set(peak)
                    return result
                }

                override fun onStreamStateChanged(stream: AudioStream, state: StreamState) {
                    callback.onStreamStateChanged(stream, state)
                }

                override fun onStreamError(stream: AudioStream, error: KlarinetException) {
                    callback.onStreamError(stream, error)
                }

                override fun onStreamUnderrun(stream: AudioStream, count: Int) {
                    callback.onStreamUnderrun(stream, count)
                }
            }
        } else null

        val streamHandle = JniBridge.nativeOpenStream(
            engineHandle = engineHandle,
            sampleRate = config.sampleRate,
            channelCount = config.channelCount,
            audioFormat = config.audioFormat.ordinal,
            bufferCapacityInFrames = config.bufferCapacityInFrames,
            performanceMode = config.performanceMode.ordinal,
            sharingMode = config.sharingMode.ordinal,
            direction = config.direction.ordinal,
            callbackObj = wrappedCallback,
        )
        if (streamHandle == 0L) {
            throw StreamCreationException("Failed to open native audio stream")
        }
        stream.streamHandle = streamHandle
        streams.add(stream)
        return stream
    }

    actual fun getAvailableDevices(): List<AudioDeviceInfo> {
        val count = JniBridge.nativeGetDeviceCount()
        return (0 until count).map { index ->
            val info = JniBridge.nativeGetDeviceInfo(index)
            AudioDeviceInfo(
                id = info[0],
                name = JniBridge.nativeGetDeviceName(index),
                isInput = info[1] == 1,
                isOutput = info[2] == 1,
                sampleRates = JniBridge.nativeGetDeviceSampleRates(index).toList(),
                channelCounts = JniBridge.nativeGetDeviceChannelCounts(index).toList(),
            )
        }
    }

    actual fun getDefaultDevice(direction: StreamDirection): AudioDeviceInfo? {
        return getAvailableDevices().firstOrNull { device ->
            when (direction) {
                StreamDirection.OUTPUT -> device.isOutput
                StreamDirection.INPUT -> device.isInput
            }
        }
    }

    actual fun createEffect(type: AudioEffectType): AudioEffect {
        check(engineHandle != 0L) { "AudioEngine has been released" }
        val handle = JniBridge.nativeCreateEffect(engineHandle, type.ordinal)
        if (handle == 0L) throw StreamCreationException("Failed to create audio effect")
        return AudioEffect(type, engineHandle, handle)
    }

    actual fun createEffectChain(): AudioEffectChain {
        check(engineHandle != 0L) { "AudioEngine has been released" }
        val handle = JniBridge.nativeCreateEffectChain(engineHandle)
        if (handle == 0L) throw StreamCreationException("Failed to create effect chain")
        return AudioEffectChain(engineHandle, handle)
    }

    actual fun release() {
        streams.forEach { it.close() }
        streams.clear()
        if (engineHandle != 0L) {
            JniBridge.nativeDestroyEngine(engineHandle)
            engineHandle = 0L
        }
    }
}
