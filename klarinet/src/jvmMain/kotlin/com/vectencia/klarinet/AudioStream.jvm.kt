package com.vectencia.klarinet

actual class AudioStream internal constructor(actual val config: AudioStreamConfig) : AutoCloseable {

    internal var devicePtr: Long = 0L
    private var _state: StreamState = StreamState.OPEN

    actual val state: StreamState get() = _state

    actual val latencyInfo: LatencyInfo
        get() {
            if (devicePtr == 0L) return LatencyInfo(0.0, 0.0)
            val latencyMs = JniBridge.nativeDeviceGetLatencyMs(devicePtr)
            return when (config.direction) {
                StreamDirection.OUTPUT -> LatencyInfo(0.0, latencyMs)
                StreamDirection.INPUT -> LatencyInfo(latencyMs, 0.0)
            }
        }

    actual fun start() {
        requireActive(devicePtr != 0L, "AudioStream")
        _state = StreamState.STARTING
        JniBridge.nativeDeviceStart(devicePtr)
        _state = StreamState.STARTED
    }

    actual fun pause() {
        requireActive(devicePtr != 0L, "AudioStream")
        _state = StreamState.PAUSING
        JniBridge.nativeDeviceStop(devicePtr)
        _state = StreamState.PAUSED
    }

    actual fun stop() {
        requireActive(devicePtr != 0L, "AudioStream")
        _state = StreamState.STOPPING
        JniBridge.nativeDeviceStop(devicePtr)
        _state = StreamState.STOPPED
    }

    actual override fun close() {
        if (devicePtr != 0L) {
            if (_state == StreamState.STARTED || _state == StreamState.PAUSED) {
                try { JniBridge.nativeDeviceStop(devicePtr) } catch (_: Exception) {}
            }
            _state = StreamState.CLOSING
            JniBridge.nativeDeviceUninit(devicePtr)
            devicePtr = 0L
            _state = StreamState.CLOSED
        }
    }

    actual fun write(data: FloatArray, numFrames: Int, timeoutNanos: Long): Int {
        requireActive(devicePtr != 0L, "AudioStream")
        return JniBridge.nativeDeviceWriteFloat(devicePtr, data, numFrames, timeoutNanos)
    }

    actual fun read(data: FloatArray, numFrames: Int, timeoutNanos: Long): Int {
        requireActive(devicePtr != 0L, "AudioStream")
        return JniBridge.nativeDeviceReadFloat(devicePtr, data, numFrames, timeoutNanos)
    }

    actual var effectChain: AudioEffectChain? = null
        set(value) {
            requireActive(devicePtr != 0L, "AudioStream")
            if (value != null) {
                JniBridge.nativeChainPrepare(
                    value.chainHandle,
                    config.sampleRate,
                    config.channelCount,
                )
                JniBridge.nativeSetDeviceEffectChain(devicePtr, value.chainHandle)
            } else {
                JniBridge.nativeClearDeviceEffectChain(devicePtr)
            }
            field = value
        }
    actual val peakLevel: Float get() = peakLevelAtomic.get()
    internal actual val peakLevelAtomic = AtomicFloat(0f)
}
