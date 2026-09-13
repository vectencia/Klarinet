package com.vectencia.klarinet

actual class AudioStream internal constructor(actual val config: AudioStreamConfig) : AutoCloseable {

    /** Native stream handle, set by [AudioEngine.openStream] after construction. */
    internal var streamHandle: Long = 0L

    actual val state: StreamState
        get() {
            if (streamHandle == 0L) return StreamState.CLOSED
            val ordinal = JniBridge.nativeGetStreamState(streamHandle)
            return StreamState.entries.getOrElse(ordinal) { StreamState.UNINITIALIZED }
        }

    actual val latencyInfo: LatencyInfo
        get() {
            if (streamHandle == 0L) return LatencyInfo(0.0, 0.0)
            return LatencyInfo(
                inputLatencyMs = JniBridge.nativeGetInputLatencyMs(streamHandle),
                outputLatencyMs = JniBridge.nativeGetOutputLatencyMs(streamHandle),
            )
        }

    actual fun start() {
        requireActive(streamHandle != 0L, "AudioStream")
        if (config.direction == StreamDirection.INPUT) {
            throwIfRecordAudioDenied(AndroidHostContext.hasRecordAudioPermission())
        }
        JniBridge.nativeStartStream(streamHandle)
    }

    actual fun pause() {
        requireActive(streamHandle != 0L, "AudioStream")
        JniBridge.nativePauseStream(streamHandle)
    }

    actual fun stop() {
        requireActive(streamHandle != 0L, "AudioStream")
        JniBridge.nativeStopStream(streamHandle)
    }

    actual override fun close() {
        if (streamHandle != 0L) {
            JniBridge.nativeCloseStream(streamHandle)
            streamHandle = 0L
        }
    }

    actual fun write(data: FloatArray, numFrames: Int, timeoutNanos: Long): Int {
        requireActive(streamHandle != 0L, "AudioStream")
        return JniBridge.nativeWriteStream(streamHandle, data, numFrames, timeoutNanos)
    }

    actual fun read(data: FloatArray, numFrames: Int, timeoutNanos: Long): Int {
        requireActive(streamHandle != 0L, "AudioStream")
        return JniBridge.nativeReadStream(streamHandle, data, numFrames, timeoutNanos)
    }

    actual var effectChain: AudioEffectChain? = null
        set(value) {
            requireActive(streamHandle != 0L, "AudioStream")
            if (value != null) {
                JniBridge.nativeSetStreamEffectChain(value.engineHandle, streamHandle, value.chainHandle)
            } else {
                field?.let { JniBridge.nativeClearStreamEffectChain(it.engineHandle, streamHandle) }
            }
            field = value
        }

    actual val peakLevel: Float get() = peakLevelAtomic.get()

    internal actual val peakLevelAtomic = AtomicFloat(0f)
}
