package com.vectencia.klarinet

data class AudioAnalysisResult(
    val rmsLevel: Float,
    val rmsDb: Float,
    val peakLevel: Float,
    val peakDb: Float,
    val magnitudeSpectrum: FloatArray,
    val frequencyResolution: Float,
    val sampleRate: Int,
    val fftSize: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioAnalysisResult) return false
        return rmsLevel == other.rmsLevel &&
            rmsDb == other.rmsDb &&
            peakLevel == other.peakLevel &&
            peakDb == other.peakDb &&
            magnitudeSpectrum.contentEquals(other.magnitudeSpectrum) &&
            frequencyResolution == other.frequencyResolution &&
            sampleRate == other.sampleRate &&
            fftSize == other.fftSize
    }

    override fun hashCode(): Int {
        var result = rmsLevel.hashCode()
        result = 31 * result + rmsDb.hashCode()
        result = 31 * result + peakLevel.hashCode()
        result = 31 * result + peakDb.hashCode()
        result = 31 * result + magnitudeSpectrum.contentHashCode()
        result = 31 * result + frequencyResolution.hashCode()
        result = 31 * result + sampleRate
        result = 31 * result + fftSize
        return result
    }
}
