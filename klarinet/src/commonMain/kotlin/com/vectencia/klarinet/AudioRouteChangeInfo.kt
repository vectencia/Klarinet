package com.vectencia.klarinet

/**
 * Describes an audio route change event, such as headphones being plugged in
 * or disconnected, or a Bluetooth device connecting.
 *
 * Route changes can affect audio playback and recording. For example, when
 * headphones are unplugged, audio output typically switches to the built-in
 * speaker, which may require the application to pause playback or adjust
 * volume.
 *
 * ## Platform Behavior
 *
 * - **Android**: On API 37 and later, Oboe reports
 *   `AudioStreamRoutingCallback` while a stream is open and its routed
 *   devices change without disconnecting the stream. [reason] is
 *   `"RoutingChanged"`. [previousRoute] is the previous device id
 *   list (`"unspecified"` when there was none). Earlier Android versions
 *   disconnect the stream instead, and that path does not emit here.
 * - **Apple**: Route changes are reported via
 *   `AVAudioSession.routeChangeNotification`.
 *
 * @property reason A human-readable string describing why the route changed.
 *   Examples: `"NewDeviceAvailable"`, `"OldDeviceUnavailable"`,
 *   `"CategoryChange"`. The exact values are platform-dependent.
 * @property previousRoute A human-readable description of the audio route
 *   that was active before the change occurred. Useful for logging or for
 *   deciding whether to take action (e.g., pausing playback when headphones
 *   are removed).
 */
data class AudioRouteChangeInfo(
    val reason: String,
    val previousRoute: String,
)

/** [AudioRouteChangeInfo.reason] for an Oboe routing-changed callback. */
internal const val ROUTING_CHANGED_REASON = "RoutingChanged"

internal fun formatRoutedDeviceIds(deviceIds: IntArray): String =
    if (deviceIds.isEmpty()) "unspecified" else deviceIds.joinToString(",")

internal fun routingChangedInfo(previousDeviceIds: IntArray): AudioRouteChangeInfo =
    AudioRouteChangeInfo(
        reason = ROUTING_CHANGED_REASON,
        previousRoute = formatRoutedDeviceIds(previousDeviceIds),
    )
