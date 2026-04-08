package com.vectencia.klarinet

internal object JniBridge {
    init {
        System.loadLibrary("klarinet-jni")
    }

    // Engine lifecycle
    external fun nativeCreateEngine(): Long
    external fun nativeDestroyEngine(engineHandle: Long)

    // Stream lifecycle
    external fun nativeOpenStream(
        engineHandle: Long,
        sampleRate: Int, channelCount: Int, audioFormat: Int,
        bufferCapacityInFrames: Int, performanceMode: Int,
        sharingMode: Int, direction: Int,
        callbackObj: AudioStreamCallback?,
    ): Long

    external fun nativeStartStream(streamHandle: Long)
    external fun nativePauseStream(streamHandle: Long)
    external fun nativeStopStream(streamHandle: Long)
    external fun nativeCloseStream(streamHandle: Long)

    // Stream I/O
    external fun nativeWriteStream(streamHandle: Long, data: FloatArray, numFrames: Int, timeoutNanos: Long): Int
    external fun nativeReadStream(streamHandle: Long, data: FloatArray, numFrames: Int, timeoutNanos: Long): Int

    // Stream properties
    external fun nativeGetStreamState(streamHandle: Long): Int
    external fun nativeGetOutputLatencyMs(streamHandle: Long): Double
    external fun nativeGetInputLatencyMs(streamHandle: Long): Double

    // Effects
    external fun nativeCreateEffect(engineHandle: Long, effectType: Int): Long
    external fun nativeSetEffectParameter(engineHandle: Long, effectHandle: Long, paramId: Int, value: Float)
    external fun nativeGetEffectParameter(engineHandle: Long, effectHandle: Long, paramId: Int): Float
    external fun nativeSetEffectEnabled(engineHandle: Long, effectHandle: Long, enabled: Boolean)
    external fun nativeIsEffectEnabled(engineHandle: Long, effectHandle: Long): Boolean
    external fun nativeDestroyEffect(engineHandle: Long, effectHandle: Long)

    // Effect chains
    external fun nativeCreateEffectChain(engineHandle: Long): Long
    external fun nativeChainAddEffect(engineHandle: Long, chainHandle: Long, effectHandle: Long)
    external fun nativeChainRemoveEffect(engineHandle: Long, chainHandle: Long, effectHandle: Long)
    external fun nativeChainClear(engineHandle: Long, chainHandle: Long)
    external fun nativeChainPrepare(engineHandle: Long, chainHandle: Long, sampleRate: Int, channelCount: Int)
    external fun nativeChainGetEffectCount(engineHandle: Long, chainHandle: Long): Int
    external fun nativeChainEnqueueParam(engineHandle: Long, chainHandle: Long, effectHandle: Long, paramId: Int, value: Float)
    external fun nativeSetStreamEffectChain(engineHandle: Long, streamHandle: Long, chainHandle: Long)
    external fun nativeClearStreamEffectChain(engineHandle: Long, streamHandle: Long)
    external fun nativeDestroyEffectChain(engineHandle: Long, chainHandle: Long)

    // Device enumeration
    external fun nativeGetDeviceCount(): Int
    external fun nativeGetDeviceInfo(index: Int): IntArray
    external fun nativeGetDeviceName(index: Int): String
    external fun nativeGetDeviceSampleRates(index: Int): IntArray
    external fun nativeGetDeviceChannelCounts(index: Int): IntArray
}
