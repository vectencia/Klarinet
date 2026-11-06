package com.vectencia.klarinet

expect class AudioAnalyzer(
    fftSize: Int = 1024,
    sampleRate: Int = 44100,
) {
    val fftSize: Int
    val sampleRate: Int
    val frequencyResolution: Float

    fun analyze(
        buffer: FloatArray,
        numFrames: Int,
        channelCount: Int = 1,
    ): AudioAnalysisResult

    fun bandEnergy(result: AudioAnalysisResult, lowHz: Float, highHz: Float): Float
}
