package com.vectencia.koboe

/**
 * macOS does not use AVAudioSession. All audio session operations are no-ops.
 *
 * On macOS, audio routing and session management is handled at the system level
 * via Core Audio HAL. The AVAudioEngine itself handles device selection.
 */

internal actual fun configurePlatformAudioSession(
    category: AudioSessionCategory,
    mode: AudioSessionMode,
) {
    // No-op on macOS: AVAudioSession is not available.
}

internal actual fun setPlatformAudioSessionActive(active: Boolean) {
    // No-op on macOS: AVAudioSession is not available.
}

internal actual fun configurePlatformAudioSessionForInput() {
    // No-op on macOS: no AVAudioSession needed for input access.
}

internal actual fun observePlatformRouteChanges(listener: (AudioRouteChangeInfo) -> Unit) {
    // No-op on macOS: AVAudioSession route change notifications are not available.
    // macOS apps can use Core Audio's AudioObjectAddPropertyListener for similar
    // functionality, but that is beyond the scope of the AudioSessionManager API.
}
