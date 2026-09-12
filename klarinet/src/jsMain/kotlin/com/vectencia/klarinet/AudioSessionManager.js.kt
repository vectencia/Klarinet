package com.vectencia.klarinet

actual class AudioSessionManager {
    private val interruptions = InterruptionController()

    actual fun configure(category: AudioSessionCategory, mode: AudioSessionMode) {}
    actual fun setActive(active: Boolean) {}
    actual fun observeRouteChanges(listener: (AudioRouteChangeInfo) -> Unit) {}
    actual fun observeInterruptions(listener: (AudioInterruptionInfo) -> Unit) {
        interruptions.observe(listener)
    }
    actual fun attach(stream: AudioStream) = interruptions.attach(stream)
    actual fun detach(stream: AudioStream) = interruptions.detach(stream)
}
