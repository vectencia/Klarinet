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
        if (config.direction == StreamDirection.INPUT && !platformHasRecordPermission()) {
            throw PermissionException("Microphone permission is not granted")
        }
        applyPlatformAudioDevice(engine, config)
        val stream = AudioStream(config, engine, callback)
        streams.add(stream)
        return stream
    }

    actual fun getAvailableDevices(): List<AudioDeviceInfo> {
        requireActive(avEngine != null, "AudioEngine")
        return listPlatformAudioDevices()
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
