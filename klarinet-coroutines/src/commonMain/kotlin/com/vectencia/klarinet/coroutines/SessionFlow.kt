package com.vectencia.klarinet.coroutines

import com.vectencia.klarinet.AudioInterruptionInfo
import com.vectencia.klarinet.AudioRouteChangeInfo
import com.vectencia.klarinet.AudioSessionManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Audio interruptions as a cold [Flow].
 *
 * Collecting registers [AudioSessionManager.observeInterruptions]. The
 * core API keeps a single listener, so this replaces any previous
 * listener (including another collector). Cancelling the collector
 * installs an empty listener.
 *
 * JVM, JS, and desktop never emit. Android needs `bind(context)` then
 * [AudioSessionManager.setActive]; Apple uses session interruptions.
 *
 * ```kotlin
 * session.attach(stream)
 * session.interruptionFlow().collect { info ->
 *     // UI / logging; attach() already paused or resumed the stream
 * }
 * ```
 *
 * @see AudioSessionManager.observeInterruptions
 */
fun AudioSessionManager.interruptionFlow(): Flow<AudioInterruptionInfo> = callbackFlow {
    observeInterruptions { info -> trySend(info) }
    awaitClose { observeInterruptions { } }
}

/**
 * Audio route changes as a cold [Flow].
 *
 * Collecting registers [AudioSessionManager.observeRouteChanges]. There
 * is no unregister in the core API; cancelling the collector does not
 * drop a platform observer on Apple. Android, JVM, JS, and desktop
 * never emit from this path.
 *
 * @see AudioSessionManager.observeRouteChanges
 */
fun AudioSessionManager.routeChangeFlow(): Flow<AudioRouteChangeInfo> = callbackFlow {
    observeRouteChanges { info -> trySend(info) }
    awaitClose { }
}
