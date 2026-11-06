package com.vectencia.klarinet

internal object JniBridge {
    init {
        NativeLibLoader.load("klarinet_jvm")
    }

    @JvmStatic external fun nativeContextInit(): Long
    @JvmStatic external fun nativeContextUninit(contextPtr: Long)

    @JvmStatic external fun nativeDeviceInit(
        contextPtr: Long, sampleRate: Int, channelCount: Int,
        bufferCapacityInFrames: Int, direction: Int, deviceId: Int,
        callbackObj: AudioStreamCallback?,
    ): Long
    @JvmStatic external fun nativeDeviceStart(devicePtr: Long)
    @JvmStatic external fun nativeDeviceStop(devicePtr: Long)
    @JvmStatic external fun nativeDeviceUninit(devicePtr: Long)
    @JvmStatic external fun nativeDeviceWriteFloat(devicePtr: Long, data: FloatArray, numFrames: Int, timeoutNanos: Long): Int
    @JvmStatic external fun nativeDeviceReadFloat(devicePtr: Long, data: FloatArray, numFrames: Int, timeoutNanos: Long): Int
    @JvmStatic external fun nativeDeviceGetState(devicePtr: Long): Int
    @JvmStatic external fun nativeDeviceGetLatencyMs(devicePtr: Long): Double

    @JvmStatic external fun nativeGetPlaybackDeviceCount(contextPtr: Long): Int
    @JvmStatic external fun nativeGetCaptureDeviceCount(contextPtr: Long): Int
    @JvmStatic external fun nativeGetPlaybackDeviceName(contextPtr: Long, index: Int): String
    @JvmStatic external fun nativeGetCaptureDeviceName(contextPtr: Long, index: Int): String

    @JvmStatic external fun nativeDecoderInitFile(path: String): Long
    @JvmStatic external fun nativeDecoderGetSampleRate(decoderPtr: Long): Int
    @JvmStatic external fun nativeDecoderGetChannels(decoderPtr: Long): Int
    @JvmStatic external fun nativeDecoderGetTotalFrames(decoderPtr: Long): Long
    @JvmStatic external fun nativeDecoderReadFrames(decoderPtr: Long, output: FloatArray, frameCount: Int): Long
    @JvmStatic external fun nativeDecoderSeek(decoderPtr: Long, frameIndex: Long): Boolean
    @JvmStatic external fun nativeDecoderUninit(decoderPtr: Long)

    @JvmStatic external fun nativeEncoderInitFile(path: String, format: Int, channels: Int, sampleRate: Int): Long
    @JvmStatic external fun nativeEncoderWriteFrames(encoderPtr: Long, data: FloatArray, frameCount: Int): Boolean
    @JvmStatic external fun nativeEncoderUninit(encoderPtr: Long)

    @JvmStatic external fun nativeCreateEffect(effectType: Int): Long
    @JvmStatic external fun nativeDestroyEffect(effectHandle: Long)
    @JvmStatic external fun nativeSetEffectParameter(effectHandle: Long, paramId: Int, value: Float)
    @JvmStatic external fun nativeGetEffectParameter(effectHandle: Long, paramId: Int): Float
    @JvmStatic external fun nativeSetEffectEnabled(effectHandle: Long, enabled: Boolean)
    @JvmStatic external fun nativeIsEffectEnabled(effectHandle: Long): Boolean
    @JvmStatic external fun nativeEffectPrepare(effectHandle: Long, sampleRate: Int, channelCount: Int)

    @JvmStatic external fun nativeCreateEffectChain(): Long
    @JvmStatic external fun nativeDestroyEffectChain(chainHandle: Long)
    @JvmStatic external fun nativeChainAddEffect(chainHandle: Long, effectHandle: Long)
    @JvmStatic external fun nativeChainRemoveEffect(chainHandle: Long, effectHandle: Long)
    @JvmStatic external fun nativeChainClear(chainHandle: Long)
    @JvmStatic external fun nativeChainPrepare(chainHandle: Long, sampleRate: Int, channelCount: Int)
    @JvmStatic external fun nativeChainGetEffectCount(chainHandle: Long): Int
    @JvmStatic external fun nativeChainEnqueueParam(chainHandle: Long, effectHandle: Long, paramId: Int, value: Float)
    @JvmStatic external fun nativeChainProcess(chainHandle: Long, data: FloatArray, numFrames: Int, channelCount: Int)
    @JvmStatic external fun nativeSetDeviceEffectChain(devicePtr: Long, chainHandle: Long)
    @JvmStatic external fun nativeClearDeviceEffectChain(devicePtr: Long)

    @JvmStatic external fun nativeFftCreate(size: Int): Long
    @JvmStatic external fun nativeFftDestroy(handle: Long)
    @JvmStatic external fun nativeFftForward(handle: Long, real: FloatArray, imag: FloatArray, length: Int)
    @JvmStatic external fun nativeFftMagnitude(
        handle: Long,
        real: FloatArray,
        imag: FloatArray,
        out: FloatArray,
        outLen: Int,
    )

    @JvmStatic external fun nativeMathRms(data: FloatArray, offset: Int, length: Int): Float
    @JvmStatic external fun nativeMathPeak(data: FloatArray, offset: Int, length: Int): Float
    @JvmStatic external fun nativeMathLinearToDb(linear: Float): Float
    @JvmStatic external fun nativeMathDbToLinear(db: Float): Float
    @JvmStatic external fun nativeMathHann(out: FloatArray, size: Int)

    @JvmStatic external fun nativeAnalyzerCreate(fftSize: Int, sampleRate: Int): Long
    @JvmStatic external fun nativeAnalyzerDestroy(handle: Long)
    @JvmStatic external fun nativeAnalyzerAnalyze(
        handle: Long,
        buffer: FloatArray,
        bufferSamples: Int,
        numFrames: Int,
        channelCount: Int,
        spectrum: FloatArray,
        spectrumLen: Int,
        levels: FloatArray,
    )
    @JvmStatic external fun nativeAnalyzerBandEnergy(
        spectrum: FloatArray,
        spectrumLen: Int,
        resolution: Float,
        lowHz: Float,
        highHz: Float,
    ): Float

    @JvmStatic external fun nativeResamplerCreate(sourceRate: Int, channels: Int): Long
    @JvmStatic external fun nativeResamplerDestroy(handle: Long)
    @JvmStatic external fun nativeResamplerHasBuffered(handle: Long): Int
    @JvmStatic external fun nativeResampleLinear(
        handle: Long,
        destRate: Int,
        dest: FloatArray,
        destFrames: Int,
        channels: Int,
        read: ResamplerRead,
    ): Int
}
