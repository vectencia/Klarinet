@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.vectencia.klarinet

import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionPortDescription
import platform.AVFAudio.availableInputs
import platform.AVFAudio.currentRoute

internal actual fun listPlatformAudioDevices(): List<AudioDeviceInfo> {
    val session = AVAudioSession.sharedInstance()
    val devices = mutableListOf<AudioDeviceInfo>()

    session.currentRoute.outputs
        .filterIsInstance<AVAudioSessionPortDescription>()
        .forEach { port ->
            devices.add(port.toDeviceInfo(isInput = false, isOutput = true))
        }

    val inputs = session.availableInputs?.filterIsInstance<AVAudioSessionPortDescription>()
        ?: session.currentRoute.inputs.filterIsInstance<AVAudioSessionPortDescription>()
    inputs.forEach { port ->
        devices.add(port.toDeviceInfo(isInput = true, isOutput = false))
    }

    return devices.distinctBy { it.id }
}

internal actual fun applyPlatformAudioDevice(engine: AVAudioEngine, config: AudioStreamConfig) {
    val requested = config.deviceId ?: return
    if (config.direction != StreamDirection.INPUT) return

    val session = AVAudioSession.sharedInstance()
    val port = session.availableInputs
        ?.filterIsInstance<AVAudioSessionPortDescription>()
        ?.firstOrNull { it.stableId() == requested }
        ?: throw DeviceNotFoundException("Audio device $requested was not found")
    session.setPreferredInput(port, null)
}

private fun AVAudioSessionPortDescription.toDeviceInfo(
    isInput: Boolean,
    isOutput: Boolean,
): AudioDeviceInfo = AudioDeviceInfo(
    id = stableId(),
    name = portName,
    isInput = isInput,
    isOutput = isOutput,
    sampleRates = listOf(44_100, 48_000),
    channelCounts = if (isOutput) listOf(1, 2) else listOf(1),
)

private fun AVAudioSessionPortDescription.stableId(): Int =
    UID.hashCode()
