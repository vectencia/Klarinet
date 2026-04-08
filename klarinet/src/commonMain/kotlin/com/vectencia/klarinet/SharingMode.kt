package com.vectencia.klarinet

/** Audio stream sharing mode with other apps. */
enum class SharingMode {
    /** Share the audio device with other apps (default). */
    SHARED,
    /** Request exclusive access to the audio device. */
    EXCLUSIVE
}
