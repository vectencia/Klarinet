package com.vectencia.klarinet

/**
 * Platform-specific audio session helpers.
 *
 * AVAudioSession is available on iOS and tvOS but not on macOS.
 * Each platform source set provides its own actual implementations:
 * - **iosMain / tvosMain**: Full AVAudioSession integration.
 * - **macosMain**: No-op (macOS does not use AVAudioSession).
 */
internal expect fun configurePlatformAudioSession(
    category: AudioSessionCategory,
    mode: AudioSessionMode,
)

internal expect fun setPlatformAudioSessionActive(active: Boolean)

internal expect fun observePlatformRouteChanges(listener: (AudioRouteChangeInfo) -> Unit)

/**
 * Configure the audio session for recording input.
 * On iOS/tvOS, sets category to PlayAndRecord so the inputNode is available.
 * On macOS, no-op.
 */
internal expect fun configurePlatformAudioSessionForInput()

/**
 * Install an audio tap on the input node's bus 0.
 * The bus parameter type differs across Apple targets, so this must be per-platform.
 */
internal expect fun installPlatformInputTap(
    engine: platform.AVFAudio.AVAudioEngine,
    bufferSize: UInt,
    callback: (platform.AVFAudio.AVAudioPCMBuffer?) -> Unit,
)

/**
 * Remove the audio tap from the input node's bus 0.
 */
internal expect fun removePlatformInputTap(engine: platform.AVFAudio.AVAudioEngine)
