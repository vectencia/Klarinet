package com.vectencia.klarinet

actual class AudioEngine private constructor() : AutoCloseable {
    internal val context: AudioContext = newAudioContext()
    private val streams = mutableListOf<AudioStream>()
    private var active = true
    private var devices: List<AudioDeviceInfo> = listOf(
        AudioDeviceInfo(
            id = 0,
            name = "Default Output",
            isInput = false,
            isOutput = true,
            sampleRates = listOf(44100, 48000),
            channelCounts = listOf(1, 2),
        ),
        AudioDeviceInfo(
            id = 1,
            name = "Default Input",
            isInput = true,
            isOutput = false,
            sampleRates = listOf(44100, 48000),
            channelCounts = listOf(1, 2),
        ),
    )

    actual companion object {
        actual fun create(): AudioEngine {
            return try {
                AudioEngine()
            } catch (error: StreamCreationException) {
                throw error
            } catch (error: Throwable) {
                throw StreamCreationException("Failed to create Web Audio engine", error)
            }
        }
    }

    init {
        refreshDevices()
    }

    actual fun openStream(config: AudioStreamConfig, callback: AudioStreamCallback?): AudioStream {
        requireActive(active, "AudioEngine")
        requireRequestedDevice(config)
        val stream = AudioStream(this, config, callback)
        streams.add(stream)
        return stream
    }

    actual fun getAvailableDevices(): List<AudioDeviceInfo> {
        requireActive(active, "AudioEngine")
        return devices.toList()
    }

    actual fun getDefaultDevice(direction: StreamDirection): AudioDeviceInfo? {
        requireActive(active, "AudioEngine")
        return devices.firstOrNull { device ->
            when (direction) {
                StreamDirection.OUTPUT -> device.isOutput
                StreamDirection.INPUT -> device.isInput
            }
        }
    }

    actual fun createEffect(type: AudioEffectType): AudioEffect {
        requireActive(active, "AudioEngine")
        return AudioEffect(type)
    }

    actual fun createEffectChain(): AudioEffectChain {
        requireActive(active, "AudioEngine")
        return AudioEffectChain()
    }

    actual fun release() {
        if (!active) return
        streams.forEach { stream -> stream.close() }
        streams.clear()
        context.close()
        active = false
    }

    actual override fun close() = release()

    private fun refreshDevices() {
        try {
            navigator.mediaDevices.enumerateDevices().then { infos ->
                val next = mutableListOf(
                    AudioDeviceInfo(
                        id = 0,
                        name = "Default Output",
                        isInput = false,
                        isOutput = true,
                        sampleRates = listOf(44100, 48000),
                        channelCounts = listOf(1, 2),
                    ),
                    AudioDeviceInfo(
                        id = 1,
                        name = "Default Input",
                        isInput = true,
                        isOutput = false,
                        sampleRates = listOf(44100, 48000),
                        channelCounts = listOf(1, 2),
                    ),
                )
                val count = jsLength(infos)
                for (i in 0 until count) {
                    val info = jsIndex(infos, i).unsafeCast<MediaDeviceInfo>()
                    val isInput = info.kind == "audioinput"
                    val isOutput = info.kind == "audiooutput"
                    if (!isInput && !isOutput) continue
                    val name = info.label.ifBlank {
                        if (isInput) "Microphone" else "Speakers"
                    }
                    next.add(
                        AudioDeviceInfo(
                            id = WebDeviceRegistry.register(info.deviceId),
                            name = name,
                            isInput = isInput,
                            isOutput = isOutput,
                            sampleRates = listOf(44100, 48000),
                            channelCounts = listOf(1, 2),
                        ),
                    )
                }
                devices = next
                null
            }
        } catch (_: Throwable) {
        }
    }
}
