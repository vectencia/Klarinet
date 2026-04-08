package com.vectencia.koboe

import platform.AVFAudio.AVAudioEngine

actual class AudioEngine private constructor() {
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
        val engine = avEngine ?: throw StreamCreationException("AudioEngine has been released")
        val stream = AudioStream(config, engine, callback)
        streams.add(stream)
        return stream
    }

    actual fun getAvailableDevices(): List<AudioDeviceInfo> {
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

    actual fun release() {
        streams.forEach { it.close() }
        streams.clear()
        avEngine?.stop()
        avEngine = null
    }
}
