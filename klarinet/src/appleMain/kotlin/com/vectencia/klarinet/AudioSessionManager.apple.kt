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

    actual fun clearRouteChanges() {
        clearPlatformRouteChanges()
    }

    actual fun observeInterruptions(listener: (AudioInterruptionInfo) -> Unit) {
        interruptions.observe(listener)
        ensureInterruptionHook()
    }

    actual fun clearInterruptions(listener: (AudioInterruptionInfo) -> Unit) {
        interruptions.remove(listener)
        releaseInterruptionHookIfIdle()
    }

    actual fun hasRecordPermission(): Boolean = platformHasRecordPermission()

    actual fun requestRecordPermission(onResult: (granted: Boolean) -> Unit) {
        requestPlatformRecordPermission(onResult)
    }

    actual fun attach(stream: AudioStream) {
        interruptions.attach(stream)
        ensureInterruptionHook()
    }

    actual fun detach(stream: AudioStream) {
        interruptions.detach(stream)
        releaseInterruptionHookIfIdle()
    }

    private fun ensureInterruptionHook() {
        if (interruptionHooked) return
        interruptionHooked = true
        observePlatformInterruptions { info -> interruptions.dispatch(info) }
    }

    private fun releaseInterruptionHookIfIdle() {
        if (!interruptions.isIdle()) return
        if (!interruptionHooked) return
        clearPlatformInterruptions()
        interruptionHooked = false
    }
}
