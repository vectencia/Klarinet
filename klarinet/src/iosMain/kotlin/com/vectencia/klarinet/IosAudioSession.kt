@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.vectencia.klarinet

import kotlinx.cinterop.*
import platform.AVFAudio.*
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.darwin.NSObjectProtocol

internal actual fun configurePlatformAudioSession(
    category: AudioSessionCategory,
    mode: AudioSessionMode,
) {
    val session = AVAudioSession.sharedInstance()
    val avCategory = when (category) {
        AudioSessionCategory.PLAYBACK -> AVAudioSessionCategoryPlayback
        AudioSessionCategory.RECORD -> AVAudioSessionCategoryRecord
        AudioSessionCategory.PLAY_AND_RECORD -> AVAudioSessionCategoryPlayAndRecord
        AudioSessionCategory.AMBIENT -> AVAudioSessionCategoryAmbient
    }
    val avMode = when (mode) {
        AudioSessionMode.DEFAULT -> AVAudioSessionModeDefault
        AudioSessionMode.MEASUREMENT -> AVAudioSessionModeMeasurement
        AudioSessionMode.VOICE_CHAT -> AVAudioSessionModeVoiceChat
        AudioSessionMode.GAME_CHAT -> AVAudioSessionModeGameChat
    }
    val errorPtr = nativeHeap.alloc<ObjCObjectVar<NSError?>>()
    val success = session.setCategory(avCategory, mode = avMode, options = 0u, error = errorPtr.ptr)
    val error = errorPtr.value
    nativeHeap.free(errorPtr)
    if (!success) {
        throw AudioSessionException(
            "Failed to configure audio session category=$category mode=$mode" +
                (error?.let { ": ${it.localizedDescription}" } ?: "")
        )
    }
}

internal actual fun setPlatformAudioSessionActive(active: Boolean) {
    val session = AVAudioSession.sharedInstance()
    val errorPtr = nativeHeap.alloc<ObjCObjectVar<NSError?>>()
    val success = session.setActive(active, error = errorPtr.ptr)
    val error = errorPtr.value
    nativeHeap.free(errorPtr)
    if (!success) {
        throw AudioSessionException(
            "Failed to set audio session active=$active" +
                (error?.let { ": ${it.localizedDescription}" } ?: "")
        )
    }
}

private var routeChangeObserver: NSObjectProtocol? = null
private var interruptionObserver: NSObjectProtocol? = null

internal actual fun platformHasRecordPermission(): Boolean =
    AVAudioSession.sharedInstance().recordPermission == AVAudioSessionRecordPermissionGranted

internal actual fun requestPlatformRecordPermission(onResult: (Boolean) -> Unit) {
    val session = AVAudioSession.sharedInstance()
    if (session.recordPermission == AVAudioSessionRecordPermissionGranted) {
        onResult(true)
        return
    }
    session.requestRecordPermission { granted -> onResult(granted) }
}

internal actual fun configurePlatformAudioSessionForInput() {
    val session = AVAudioSession.sharedInstance()
    val errorPtr = nativeHeap.alloc<ObjCObjectVar<NSError?>>()
    session.setCategory(
        AVAudioSessionCategoryPlayAndRecord,
        mode = AVAudioSessionModeDefault,
        options = 0u,
        error = errorPtr.ptr,
    )
    session.setActive(true, error = errorPtr.ptr)
    nativeHeap.free(errorPtr)
}

internal actual fun observePlatformInterruptions(listener: (AudioInterruptionInfo) -> Unit) {
    clearPlatformInterruptions()
    interruptionObserver = NSNotificationCenter.defaultCenter.addObserverForName(
        name = AVAudioSessionInterruptionNotification,
        `object` = AVAudioSession.sharedInstance(),
        queue = NSOperationQueue.mainQueue,
    ) { notification ->
        listener(audioInterruptionInfo(notification?.userInfo))
    }
}

internal actual fun clearPlatformInterruptions() {
    interruptionObserver?.let {
        NSNotificationCenter.defaultCenter.removeObserver(it)
    }
    interruptionObserver = null
}

internal fun isInterruptionObserverInstalled(): Boolean = interruptionObserver != null

internal actual fun observePlatformRouteChanges(listener: (AudioRouteChangeInfo) -> Unit) {
    clearPlatformRouteChanges()

    routeChangeObserver = NSNotificationCenter.defaultCenter.addObserverForName(
        name = AVAudioSessionRouteChangeNotification,
        `object` = AVAudioSession.sharedInstance(),
        queue = NSOperationQueue.mainQueue,
    ) { notification ->
        val userInfo = notification?.userInfo
        val reasonValue = userInfo?.get(AVAudioSessionRouteChangeReasonKey)
        val reason = reasonValue?.toString() ?: "Unknown"
        val previousRoute = AVAudioSession.sharedInstance().currentRoute
            .outputs
            .firstOrNull()
            ?.toString() ?: "Unknown"

        listener(
            AudioRouteChangeInfo(
                reason = reason,
                previousRoute = previousRoute,
            )
        )
    }
}

internal actual fun clearPlatformRouteChanges() {
    routeChangeObserver?.let {
        NSNotificationCenter.defaultCenter.removeObserver(it)
    }
    routeChangeObserver = null
}

internal fun isRouteChangeObserverInstalled(): Boolean = routeChangeObserver != null

internal actual fun installPlatformInputTap(
    engine: platform.AVFAudio.AVAudioEngine,
    bufferSize: UInt,
    callback: (platform.AVFAudio.AVAudioPCMBuffer?) -> Unit,
) {
    engine.inputNode.installTapOnBus(0u, bufferSize = bufferSize, format = null) { buffer, _ ->
        callback(buffer)
    }
}

internal actual fun removePlatformInputTap(engine: platform.AVFAudio.AVAudioEngine) {
    engine.inputNode.removeTapOnBus(0u)
}

private fun audioInterruptionInfo(userInfo: Map<Any?, *>?): AudioInterruptionInfo {
    val typeValue = (userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedLongValue
        ?: AVAudioSessionInterruptionTypeBegan
    val began = typeValue == AVAudioSessionInterruptionTypeBegan
    val shouldResume = ((userInfo?.get(AVAudioSessionInterruptionOptionKey) as? NSNumber)?.intValue ?: 0) != 0
    return audioInterruptionFromSession(typeBegan = began, optionShouldResume = shouldResume)
}
