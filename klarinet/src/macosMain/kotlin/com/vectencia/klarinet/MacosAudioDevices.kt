@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.vectencia.klarinet

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value
import platform.AVFAudio.AVAudioEngine
import platform.AudioToolbox.AudioUnitSetProperty
import platform.CoreAudio.AudioDeviceIDVar
import platform.CoreAudio.AudioObjectGetPropertyData
import platform.CoreAudio.AudioObjectGetPropertyDataSize
import platform.CoreAudio.AudioObjectPropertyAddress
import platform.CoreAudio.kAudioDevicePropertyDeviceNameCFString
import platform.CoreAudio.kAudioDevicePropertyScopeInput
import platform.CoreAudio.kAudioDevicePropertyScopeOutput
import platform.CoreAudio.kAudioDevicePropertyStreamConfiguration
import platform.CoreAudio.kAudioHardwarePropertyDevices
import platform.CoreAudio.kAudioObjectPropertyElementMain
import platform.CoreAudio.kAudioObjectPropertyScopeGlobal
import platform.CoreAudio.kAudioObjectSystemObject
import platform.CoreAudioTypes.AudioBufferList
import platform.Foundation.CFBridgingRelease

private const val AUDIO_OUTPUT_UNIT_CURRENT_DEVICE: UInt = 2000u
private const val AUDIO_UNIT_SCOPE_GLOBAL: UInt = 0u

internal actual fun listPlatformAudioDevices(): List<AudioDeviceInfo> = memScoped {
    val address = alloc<AudioObjectPropertyAddress>()
    address.mSelector = kAudioHardwarePropertyDevices
    address.mScope = kAudioObjectPropertyScopeGlobal
    address.mElement = kAudioObjectPropertyElementMain

    val dataSize = alloc<UIntVar>()
    if (AudioObjectGetPropertyDataSize(
            kAudioObjectSystemObject.toUInt(),
            address.ptr,
            0u,
            null,
            dataSize.ptr,
        ) != 0
    ) {
        return emptyList()
    }

    val count = (dataSize.value.toInt() / sizeOf<AudioDeviceIDVar>().toInt())
    if (count <= 0) return emptyList()

    val ids = allocArray<AudioDeviceIDVar>(count)
    if (AudioObjectGetPropertyData(
            kAudioObjectSystemObject.toUInt(),
            address.ptr,
            0u,
            null,
            dataSize.ptr,
            ids,
        ) != 0
    ) {
        return emptyList()
    }

    (0 until count).map { index ->
        val id = ids[index]
        AudioDeviceInfo(
            id = id.toInt(),
            name = deviceName(id) ?: "Audio device $id",
            isInput = deviceHasScope(id, kAudioDevicePropertyScopeInput),
            isOutput = deviceHasScope(id, kAudioDevicePropertyScopeOutput),
            sampleRates = listOf(44_100, 48_000),
            channelCounts = listOf(1, 2),
        )
    }.filter { it.isInput || it.isOutput }
}

internal actual fun applyPlatformAudioDevice(engine: AVAudioEngine, config: AudioStreamConfig) {
    val requested = config.deviceId ?: return
    val node = when (config.direction) {
        StreamDirection.OUTPUT -> engine.outputNode
        StreamDirection.INPUT -> engine.inputNode
    }
    val audioUnit = node.audioUnit ?: throw StreamCreationException(
        "Cannot select audio device $requested: node has no AudioUnit",
    )
    memScoped {
        val deviceId = alloc<AudioDeviceIDVar>()
        deviceId.value = requested.toUInt()
        val status = AudioUnitSetProperty(
            audioUnit,
            AUDIO_OUTPUT_UNIT_CURRENT_DEVICE,
            AUDIO_UNIT_SCOPE_GLOBAL,
            0u,
            deviceId.ptr,
            sizeOf<AudioDeviceIDVar>().toUInt(),
        )
        if (status != 0) {
            throw StreamCreationException(
                "Failed to set Core Audio device $requested (status $status)",
            )
        }
    }
}

private fun deviceName(id: UInt): String? = memScoped {
    val address = alloc<AudioObjectPropertyAddress>()
    address.mSelector = kAudioDevicePropertyDeviceNameCFString
    address.mScope = kAudioObjectPropertyScopeGlobal
    address.mElement = kAudioObjectPropertyElementMain
    val cfString = alloc<platform.CoreFoundation.CFStringRefVar>()
    val size = alloc<UIntVar>()
    size.value = sizeOf<platform.CoreFoundation.CFStringRefVar>().toUInt()
    if (AudioObjectGetPropertyData(id, address.ptr, 0u, null, size.ptr, cfString.ptr) != 0) {
        return null
    }
    val bridged = CFBridgingRelease(cfString.value)
    bridged?.toString()
}

private fun deviceHasScope(id: UInt, scope: UInt): Boolean = memScoped {
    val address = alloc<AudioObjectPropertyAddress>()
    address.mSelector = kAudioDevicePropertyStreamConfiguration
    address.mScope = scope
    address.mElement = kAudioObjectPropertyElementMain
    val size = alloc<UIntVar>()
    if (AudioObjectGetPropertyDataSize(id, address.ptr, 0u, null, size.ptr) != 0) {
        return false
    }
    if (size.value == 0u) return false
    val data = allocArray<ByteVar>(size.value.toInt())
    if (AudioObjectGetPropertyData(id, address.ptr, 0u, null, size.ptr, data) != 0) {
        return false
    }
    data.reinterpret<AudioBufferList>().pointed.mNumberBuffers > 0u
}
