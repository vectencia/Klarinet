package com.vectencia.klarinet

/**
 * Latency measurements for an audio stream.
 */
data class LatencyInfo(
    val inputLatencyMs: Double,
    val outputLatencyMs: Double,
)
