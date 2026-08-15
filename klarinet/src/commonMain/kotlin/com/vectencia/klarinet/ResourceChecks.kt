package com.vectencia.klarinet

internal fun requireActive(active: Boolean, resource: String) {
    if (!active) throw ResourceReleasedException("$resource has been released")
}

internal fun AudioEngine.requireRequestedDevice(config: AudioStreamConfig) {
    val id = config.deviceId ?: return
    val device = getAvailableDevices().firstOrNull { it.id == id }
        ?: throw DeviceNotFoundException("Audio device $id was not found")
    val matchesDirection = when (config.direction) {
        StreamDirection.OUTPUT -> device.isOutput
        StreamDirection.INPUT -> device.isInput
    }
    if (!matchesDirection) {
        throw DeviceNotFoundException(
            "Audio device $id does not support ${config.direction.name.lowercase()}",
        )
    }
}

internal fun AudioStreamConfig.nativeDeviceId(): Int = deviceId ?: -1
