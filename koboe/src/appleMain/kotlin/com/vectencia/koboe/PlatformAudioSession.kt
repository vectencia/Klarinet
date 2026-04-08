package com.vectencia.koboe

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
