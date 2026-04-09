package com.vectencia.klarinet

/**
 * Information about an audio route change event.
 */
data class AudioRouteChangeInfo(
    val reason: String,
    val previousRoute: String,
)
