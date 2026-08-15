package com.vectencia.klarinet

import platform.AVFAudio.AVAudioEngine

actual class AudioEngine private constructor() : AutoCloseable {
    private var avEngine: AVAudioEngine? = null
    private val streams = mutableListOf<AudioStream>()

    actual companion object {
        actual fun create(): AudioEngine {
            val engine = AudioEngine()
            engine.avEngine = AVAudioEngine()
            return engine
        }
    }

    actual fun openStream(config: AudioStreamConfig, callback: AudioStreamCallback?): AudioStream {
        val engine = avEngine ?: throw ResourceReleasedException("AudioEngine has been released")
        requireRequestedDevice(config)
        val stream = AudioStream(config, engine, callback)
        streams.add(stream)
        return stream
    }

    actual fun getAvailableDevices(): List<AudioDeviceInfo> {
        requireActive(avEngine != null, "AudioEngine")
        val devices = mutableListOf<AudioDeviceInfo>()
        avEngine?.let {
            devices.add(
                AudioDeviceInfo(
                    id = 0,
                    name = "Default Output",
                    isInput = false,
                    isOutput = true,
                    sampleRates = listOf(44100, 48000),
                    channelCounts = listOf(1, 2),
                )
            )
            devices.add(
                AudioDeviceInfo(
                    id = 1,
                    name = "Default Input",
                    isInput = true,
                    isOutput = false,
                    sampleRates = listOf(44100, 48000),
                    channelCounts = listOf(1),
                )
            )
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
        requireActive(avEngine != null, "AudioEngine")
        return AudioEffect(type)
    }

    actual fun createEffectChain(): AudioEffectChain {
        requireActive(avEngine != null, "AudioEngine")
        return AudioEffectChain()
    }

    actual fun release() {
        streams.forEach { it.close() }
        streams.clear()
        avEngine?.stop()
        avEngine = null
    }

    actual override fun close() = release()
}
