@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.vectencia.klarinet

import cnames.structs.*
import klarinet_native.*
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.StableRef

actual class AudioStream internal constructor(actual val config: AudioStreamConfig) {

    internal var devicePtr: CPointer<KlarinetDevice>? = null
    internal var callbackRef: StableRef<AudioCallbackData>? = null
    private var _state: StreamState = StreamState.OPEN

    actual val state: StreamState get() = _state

    actual val latencyInfo: LatencyInfo
        get() {
            val dev = devicePtr ?: return LatencyInfo(0.0, 0.0)
            val ms = klarinet_device_get_latency_ms(dev)
            return when (config.direction) {
                StreamDirection.OUTPUT -> LatencyInfo(0.0, ms)
                StreamDirection.INPUT -> LatencyInfo(ms, 0.0)
            }
        }

    actual fun start() {
        val dev = devicePtr ?: throw StreamOperationException("Stream has been closed")
        _state = StreamState.STARTING
        klarinet_device_start(dev)
        _state = StreamState.STARTED
    }

    actual fun pause() {
        val dev = devicePtr ?: throw StreamOperationException("Stream has been closed")
        _state = StreamState.PAUSING
        klarinet_device_stop(dev)
        _state = StreamState.PAUSED
    }

    actual fun stop() {
        val dev = devicePtr ?: throw StreamOperationException("Stream has been closed")
        _state = StreamState.STOPPING
        klarinet_device_stop(dev)
        _state = StreamState.STOPPED
    }

    actual fun close() {
        devicePtr?.let { dev ->
            if (_state == StreamState.STARTED || _state == StreamState.PAUSED) {
                try { klarinet_device_stop(dev) } catch (_: Exception) {}
            }
            _state = StreamState.CLOSING
            klarinet_device_uninit(dev)
        }
        devicePtr = null
        callbackRef?.dispose()
        callbackRef = null
        _state = StreamState.CLOSED
    }

    actual fun write(data: FloatArray, numFrames: Int, timeoutNanos: Long): Int = -1
    actual fun read(data: FloatArray, numFrames: Int, timeoutNanos: Long): Int = -1

    actual var effectChain: AudioEffectChain? = null
    actual val peakLevel: Float get() = peakLevelAtomic.get()
    internal actual val peakLevelAtomic = AtomicFloat(0f)
}
