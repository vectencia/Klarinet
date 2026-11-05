package com.vectencia.klarinet

import android.util.Log

/**
 * Receives Oboe `AudioStreamRoutingCallback` events from JNI.
 *
 * [AudioSessionManager.observeRouteChanges] installs the single listener.
 * The native routing thread is not the audio callback thread.
 */
internal object AndroidRouteEvents {
    @Volatile
    private var listener: ((AudioRouteChangeInfo) -> Unit)? = null

    fun setListener(listener: ((AudioRouteChangeInfo) -> Unit)?) {
        this.listener = listener
    }

    @JvmStatic
    fun dispatch(previousDeviceIds: IntArray, deviceIds: IntArray) {
        val info = routingChangedInfo(previousDeviceIds)
        Log.i(
            TAG,
            "Routing changed from ${info.previousRoute} to ${formatRoutedDeviceIds(deviceIds)}",
        )
        val current = listener ?: return
        try {
            current(info)
        } catch (error: Throwable) {
            Log.e(TAG, "Route change listener failed", error)
        }
    }

    private const val TAG = "KlarinetRoute"
}
