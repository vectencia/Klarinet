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
 * Collecting registers [AudioSessionManager.observeInterruptions].
 * Listeners fan out, so this does not replace a host callback or another
 * collector. Cancelling the collector calls
 * [AudioSessionManager.clearInterruptions] for this collector only.
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
 * @see AudioSessionManager.clearInterruptions
 */
fun AudioSessionManager.interruptionFlow(): Flow<AudioInterruptionInfo> = callbackFlow {
    val listener: (AudioInterruptionInfo) -> Unit = { info -> trySend(info) }
    observeInterruptions(listener)
    awaitClose { clearInterruptions(listener) }
}

/**
 * Audio route changes as a cold [Flow].
 *
 * Collecting registers [AudioSessionManager.observeRouteChanges]. The
 * core API keeps a single listener, so this replaces any previous
 * listener (including another collector). Cancelling the collector
 * calls [AudioSessionManager.clearRouteChanges], which drops the Apple
 * `NSNotificationCenter` observer. Android, JVM, JS, desktop, and
 * macOS never emit from this path.
 *
 * @see AudioSessionManager.observeRouteChanges
 * @see AudioSessionManager.clearRouteChanges
 */
fun AudioSessionManager.routeChangeFlow(): Flow<AudioRouteChangeInfo> = callbackFlow {
    observeRouteChanges { info -> trySend(info) }
    awaitClose { clearRouteChanges() }
}
