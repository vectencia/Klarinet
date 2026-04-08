package com.vectencia.klarinet

actual class AudioSessionManager {

    actual fun configure(category: AudioSessionCategory, mode: AudioSessionMode) {
        configurePlatformAudioSession(category, mode)
    }

    actual fun setActive(active: Boolean) {
        setPlatformAudioSessionActive(active)
    }

    actual fun observeRouteChanges(listener: (AudioRouteChangeInfo) -> Unit) {
        observePlatformRouteChanges(listener)
    }
}
