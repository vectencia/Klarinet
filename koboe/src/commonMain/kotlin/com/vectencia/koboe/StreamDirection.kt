package com.vectencia.koboe

/** Direction of an audio stream. */
enum class StreamDirection {
    /** Playback stream — audio flows from the app to the device. */
    OUTPUT,
    /** Recording stream — audio flows from the device to the app. */
    INPUT
}
