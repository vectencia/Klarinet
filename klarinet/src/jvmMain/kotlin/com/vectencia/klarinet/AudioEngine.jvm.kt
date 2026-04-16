package com.vectencia.klarinet

actual class AudioEngine private constructor() {
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
        check(contextPtr != 0L) { "AudioEngine has been released" }
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

        val devicePtr = JniBridge.nativeDeviceInit(
            contextPtr = contextPtr,
            sampleRate = config.sampleRate,
            channelCount = config.channelCount,
            bufferCapacityInFrames = config.bufferCapacityInFrames,
            direction = config.direction.ordinal,
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
        check(contextPtr != 0L) { "AudioEngine has been released" }
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

    actual fun createEffect(type: AudioEffectType): AudioEffect = AudioEffect(type)
    actual fun createEffectChain(): AudioEffectChain = AudioEffectChain()

    actual fun release() {
        streams.forEach { it.close() }
        streams.clear()
        if (contextPtr != 0L) {
            JniBridge.nativeContextUninit(contextPtr)
            contextPtr = 0L
        }
    }
}
