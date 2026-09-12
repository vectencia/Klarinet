package com.vectencia.klarinet

/**
 * Whether an audio interruption is starting or finishing.
 *
 * @see AudioInterruptionInfo
 * @see AudioSessionManager.observeInterruptions
 */
enum class AudioInterruptionType {
    /** A call, alarm, or other app took the audio session / focus. */
    BEGAN,

    /** The interruption ended. Check [AudioInterruptionInfo.shouldResume]. */
    ENDED,
}

/**
 * A call, Siri, another app, or audio-focus loss interrupted playback.
 *
 * The SDK does not pause or resume [AudioStream]s. The host should:
 *
 * ```kotlin
 * session.observeInterruptions { info ->
 *     when (info.type) {
 *         AudioInterruptionType.BEGAN -> stream.pause()
 *         AudioInterruptionType.ENDED -> if (info.shouldResume) stream.start()
 *     }
 * }
 * ```
 *
 * @property type [AudioInterruptionType.BEGAN] or [AudioInterruptionType.ENDED].
 * @property shouldResume true when the interruption ended and the system
 *   suggests continuing playback. Always false for [AudioInterruptionType.BEGAN].
 */
data class AudioInterruptionInfo(
    val type: AudioInterruptionType,
    val shouldResume: Boolean,
)
