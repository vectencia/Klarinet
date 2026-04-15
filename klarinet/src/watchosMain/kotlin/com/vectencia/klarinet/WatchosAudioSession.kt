@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.vectencia.klarinet

import kotlinx.cinterop.*
import platform.AVFAudio.*
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
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

private var routeChangeObserver: NSObjectProtocol? = null

internal actual fun observePlatformRouteChanges(listener: (AudioRouteChangeInfo) -> Unit) {
    routeChangeObserver?.let {
        NSNotificationCenter.defaultCenter.removeObserver(it)
    }

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
