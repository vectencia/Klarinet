package com.vectencia.klarinet

internal object JniBridge {
    init {
        NativeLibLoader.load("klarinet_jvm")
    }

    @JvmStatic external fun nativeContextInit(): Long
    @JvmStatic external fun nativeContextUninit(contextPtr: Long)

    @JvmStatic external fun nativeDeviceInit(
        contextPtr: Long, sampleRate: Int, channelCount: Int,
        bufferCapacityInFrames: Int, direction: Int,
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
}
