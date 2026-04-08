package com.vectencia.koboe

/**
 * Latency measurements for an audio stream.
 */
data class LatencyInfo(
    val inputLatencyMs: Double,
    val outputLatencyMs: Double,
)
