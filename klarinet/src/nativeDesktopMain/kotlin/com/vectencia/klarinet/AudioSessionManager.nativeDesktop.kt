package com.vectencia.klarinet

actual class AudioSessionManager {
    actual fun configure(category: AudioSessionCategory, mode: AudioSessionMode) {}
    actual fun setActive(active: Boolean) {}
    actual fun observeRouteChanges(listener: (AudioRouteChangeInfo) -> Unit) {}
}
