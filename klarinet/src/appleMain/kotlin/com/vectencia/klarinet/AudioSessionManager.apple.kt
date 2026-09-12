package com.vectencia.klarinet

actual class AudioSessionManager {
    private val interruptions = InterruptionController()
    private var interruptionHooked = false

    actual fun configure(category: AudioSessionCategory, mode: AudioSessionMode) {
        configurePlatformAudioSession(category, mode)
    }

    actual fun setActive(active: Boolean) {
        setPlatformAudioSessionActive(active)
    }

    actual fun observeRouteChanges(listener: (AudioRouteChangeInfo) -> Unit) {
        observePlatformRouteChanges(listener)
    }

    actual fun observeInterruptions(listener: (AudioInterruptionInfo) -> Unit) {
        interruptions.observe(listener)
        ensureInterruptionHook()
    }

    actual fun attach(stream: AudioStream) {
        interruptions.attach(stream)
        ensureInterruptionHook()
    }

    actual fun detach(stream: AudioStream) = interruptions.detach(stream)

    private fun ensureInterruptionHook() {
        if (interruptionHooked) return
        interruptionHooked = true
        observePlatformInterruptions { info -> interruptions.dispatch(info) }
    }
}
