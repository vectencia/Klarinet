package com.vectencia.klarinet

actual class AudioSessionManager actual constructor() {
    internal val interruptions = InterruptionController()

    actual fun configure(category: AudioSessionCategory, mode: AudioSessionMode) {}
    actual fun setActive(active: Boolean) {}
    actual fun observeRouteChanges(listener: (AudioRouteChangeInfo) -> Unit) {}
    actual fun clearRouteChanges() {}
    actual fun observeInterruptions(listener: (AudioInterruptionInfo) -> Unit) {
        interruptions.observe(listener)
    }
    actual fun clearInterruptions(listener: (AudioInterruptionInfo) -> Unit) {
        interruptions.remove(listener)
    }
    actual fun hasRecordPermission(): Boolean = true
    actual fun requestRecordPermission(onResult: (granted: Boolean) -> Unit) {
        onResult(true)
    }
    actual fun attach(stream: AudioStream) = interruptions.attach(stream)
    actual fun detach(stream: AudioStream) = interruptions.detach(stream)
}
