package com.vectencia.klarinet

actual class AudioSessionManager {
    actual fun configure(category: AudioSessionCategory, mode: AudioSessionMode) { /* no-op on Android */ }
    actual fun setActive(active: Boolean) { /* no-op on Android */ }
    actual fun observeRouteChanges(listener: (AudioRouteChangeInfo) -> Unit) { /* no-op on Android */ }
}
