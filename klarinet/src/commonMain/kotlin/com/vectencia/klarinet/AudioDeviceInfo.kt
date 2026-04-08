package com.vectencia.klarinet

/**
 * Describes an available audio device on the current platform.
 *
 * Obtain the list of available devices via [AudioEngine.getAvailableDevices]
 * or query the default device for a given direction with
 * [AudioEngine.getDefaultDevice].
 *
 * A single physical device (e.g., a USB audio interface) may support both
 * input and output, in which case both [isInput] and [isOutput] will be
 * `true`.
 *
 * @property id Platform-specific unique identifier for this device.
 *   On Android this corresponds to the `AudioDeviceInfo.getId()` value;
 *   on Apple platforms it maps to the Core Audio device ID.
 * @property name Human-readable name of the device (e.g., "Built-in Speaker",
 *   "USB Microphone"). Suitable for display in a device-selection UI.
 * @property isInput `true` if this device supports audio input (recording).
 * @property isOutput `true` if this device supports audio output (playback).
 * @property sampleRates List of sample rates (in Hz) supported by this device.
 *   Common values include 44100, 48000, and 96000. An empty list indicates
 *   that the platform did not report supported rates.
 * @property channelCounts List of channel counts supported by this device.
 *   For example, `[1, 2]` means the device supports both mono and stereo.
 *   An empty list indicates that the platform did not report supported
 *   channel configurations.
 *
 * @see AudioEngine.getAvailableDevices
 * @see AudioEngine.getDefaultDevice
 */
data class AudioDeviceInfo(
    val id: Int,
    val name: String,
    val isInput: Boolean,
    val isOutput: Boolean,
    val sampleRates: List<Int>,
    val channelCounts: List<Int>,
)
