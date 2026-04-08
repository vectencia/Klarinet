package com.vectencia.koboe

/** Audio stream performance mode. */
enum class PerformanceMode {
    /** No specific performance mode requested. */
    NONE,
    /** Minimize latency at the expense of power consumption. */
    LOW_LATENCY,
    /** Minimize power consumption at the expense of latency. */
    POWER_SAVING
}
