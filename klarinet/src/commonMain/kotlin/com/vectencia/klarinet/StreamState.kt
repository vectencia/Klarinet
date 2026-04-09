package com.vectencia.klarinet

/** Lifecycle state of an audio stream. */
enum class StreamState {
    UNINITIALIZED, OPEN, STARTING, STARTED,
    PAUSING, PAUSED, STOPPING, STOPPED,
    CLOSING, CLOSED
}
