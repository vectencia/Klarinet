package com.vectencia.klarinet

/** Supported audio sample formats. */
enum class AudioFormat {
    /** 32-bit floating point samples, range [-1.0, 1.0]. */
    PCM_FLOAT,
    /** 16-bit signed integer samples. */
    PCM_I16,
    /** 24-bit signed integer samples (packed). */
    PCM_I24,
    /** 32-bit signed integer samples. */
    PCM_I32
}
