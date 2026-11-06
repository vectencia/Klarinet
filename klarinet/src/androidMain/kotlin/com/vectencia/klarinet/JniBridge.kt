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
        sharingMode: Int, direction: Int, deviceId: Int,
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

    external fun nativeFftCreate(size: Int): Long
    external fun nativeFftDestroy(handle: Long)
    external fun nativeFftForward(handle: Long, real: FloatArray, imag: FloatArray, length: Int)
    external fun nativeFftMagnitude(
        handle: Long,
        real: FloatArray,
        imag: FloatArray,
        out: FloatArray,
        outLen: Int,
    )

    external fun nativeMathRms(data: FloatArray, offset: Int, length: Int): Float
    external fun nativeMathPeak(data: FloatArray, offset: Int, length: Int): Float
    external fun nativeMathLinearToDb(linear: Float): Float
    external fun nativeMathDbToLinear(db: Float): Float
    external fun nativeMathHann(out: FloatArray, size: Int)

    external fun nativeAnalyzerCreate(fftSize: Int, sampleRate: Int): Long
    external fun nativeAnalyzerDestroy(handle: Long)
    external fun nativeAnalyzerAnalyze(
        handle: Long,
        buffer: FloatArray,
        bufferSamples: Int,
        numFrames: Int,
        channelCount: Int,
        spectrum: FloatArray,
        spectrumLen: Int,
        levels: FloatArray,
    )
    external fun nativeAnalyzerBandEnergy(
        spectrum: FloatArray,
        spectrumLen: Int,
        resolution: Float,
        lowHz: Float,
        highHz: Float,
    ): Float

    external fun nativeResamplerCreate(sourceRate: Int, channels: Int): Long
    external fun nativeResamplerDestroy(handle: Long)
    external fun nativeResamplerHasBuffered(handle: Long): Int
    external fun nativeResampleLinear(
        handle: Long,
        destRate: Int,
        dest: FloatArray,
        destFrames: Int,
        channels: Int,
        read: ResamplerRead,
    ): Int
}
