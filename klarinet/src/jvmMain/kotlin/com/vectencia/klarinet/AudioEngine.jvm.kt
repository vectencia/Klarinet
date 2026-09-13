package com.vectencia.klarinet

actual class AudioEngine private constructor() : AutoCloseable {
    private var contextPtr: Long = 0L
    private val streams = mutableListOf<AudioStream>()

    actual companion object {
        actual fun create(): AudioEngine {
            val engine = AudioEngine()
            engine.contextPtr = JniBridge.nativeContextInit()
            if (engine.contextPtr == 0L) {
                throw StreamCreationException("Failed to create native audio context")
            }
            return engine
        }
    }

    actual fun openStream(config: AudioStreamConfig, callback: AudioStreamCallback?): AudioStream {
        requireActive(contextPtr != 0L, "AudioEngine")
        requireRequestedDevice(config)
        val stream = AudioStream(config)

        val wrappedCallback = callback?.let { StreamCallbackBridge(stream, it) }

        val devicePtr = JniBridge.nativeDeviceInit(
            contextPtr = contextPtr,
            sampleRate = config.sampleRate,
            channelCount = config.channelCount,
            bufferCapacityInFrames = config.bufferCapacityInFrames,
            direction = config.direction.ordinal,
            deviceId = config.nativeDeviceId(),
            callbackObj = wrappedCallback,
        )
        if (devicePtr == 0L) {
            throw StreamCreationException("Failed to open native audio device")
        }
        stream.devicePtr = devicePtr
        streams.add(stream)
        return stream
    }

    actual fun getAvailableDevices(): List<AudioDeviceInfo> {
        requireActive(contextPtr != 0L, "AudioEngine")
        val devices = mutableListOf<AudioDeviceInfo>()
        val playbackCount = JniBridge.nativeGetPlaybackDeviceCount(contextPtr)
        for (i in 0 until playbackCount) {
            devices.add(AudioDeviceInfo(
                id = i, name = JniBridge.nativeGetPlaybackDeviceName(contextPtr, i),
                isInput = false, isOutput = true,
                sampleRates = listOf(44100, 48000), channelCounts = listOf(1, 2),
            ))
        }
        val captureCount = JniBridge.nativeGetCaptureDeviceCount(contextPtr)
        for (i in 0 until captureCount) {
            devices.add(AudioDeviceInfo(
                id = playbackCount + i, name = JniBridge.nativeGetCaptureDeviceName(contextPtr, i),
                isInput = true, isOutput = false,
                sampleRates = listOf(44100, 48000), channelCounts = listOf(1),
            ))
        }
        return devices
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
        requireActive(contextPtr != 0L, "AudioEngine")
        val handle = JniBridge.nativeCreateEffect(type.ordinal)
        if (handle == 0L) throw StreamCreationException("Failed to create audio effect")
        return AudioEffect(type, handle)
    }

    actual fun createEffectChain(): AudioEffectChain {
        requireActive(contextPtr != 0L, "AudioEngine")
        val handle = JniBridge.nativeCreateEffectChain()
        if (handle == 0L) throw StreamCreationException("Failed to create effect chain")
        return AudioEffectChain(handle)
    }

    actual fun release() {
        streams.forEach { it.close() }
        streams.clear()
        if (contextPtr != 0L) {
            JniBridge.nativeContextUninit(contextPtr)
            contextPtr = 0L
        }
    }

    actual override fun close() = release()
}
