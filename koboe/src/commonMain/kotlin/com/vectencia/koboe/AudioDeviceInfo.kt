package com.vectencia.koboe

/**
 * Information about an available audio device.
 */
data class AudioDeviceInfo(
    val id: Int,
    val name: String,
    val isInput: Boolean,
    val isOutput: Boolean,
    val sampleRates: List<Int>,
    val channelCounts: List<Int>,
)
