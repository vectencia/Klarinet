package com.vectencia.koboe

internal object JniBridge {
    init {
        System.loadLibrary("koboe-jni")
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

    // Device enumeration
    external fun nativeGetDeviceCount(): Int
    external fun nativeGetDeviceInfo(index: Int): IntArray
    external fun nativeGetDeviceName(index: Int): String
    external fun nativeGetDeviceSampleRates(index: Int): IntArray
    external fun nativeGetDeviceChannelCounts(index: Int): IntArray
}
