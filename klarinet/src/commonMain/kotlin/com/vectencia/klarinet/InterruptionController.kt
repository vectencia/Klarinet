package com.vectencia.klarinet

/**
 * Shared interruption fan-out: optional host listener plus pause/resume of
 * [attach]ed streams. Platform session managers [dispatch] into this.
 */
internal class InterruptionController {
    private val listeners = mutableListOf<(AudioInterruptionInfo) -> Unit>()
    private val attached = mutableListOf<AudioStream>()
    private val pausedByUs = mutableListOf<AudioStream>()

    fun observe(listener: (AudioInterruptionInfo) -> Unit) {
        if (listeners.none { it === listener }) {
            listeners += listener
        }
    }

    fun remove(listener: (AudioInterruptionInfo) -> Unit) {
        listeners.removeAll { it === listener }
    }

    fun attach(stream: AudioStream) {
        if (attached.none { it === stream }) {
            attached += stream
        }
    }

    fun detach(stream: AudioStream) {
        attached.removeAll { it === stream }
        pausedByUs.removeAll { it === stream }
    }

    fun isIdle(): Boolean = listeners.isEmpty() && attached.isEmpty()

    fun dispatch(info: AudioInterruptionInfo) {
        when (info.type) {
            AudioInterruptionType.BEGAN -> pauseAttached()
            AudioInterruptionType.ENDED -> if (info.shouldResume) resumeAttached()
        }
        for (listener in listeners.toList()) {
            listener(info)
        }
    }

    private fun pauseAttached() {
        pausedByUs.clear()
        for (stream in attached.toList()) {
            try {
                val state = stream.state
                if (state == StreamState.STARTED || state == StreamState.STARTING) {
                    stream.pause()
                    pausedByUs += stream
                }
            } catch (_: StreamOperationException) {
            } catch (_: ResourceReleasedException) {
            }
        }
    }

    private fun resumeAttached() {
        for (stream in pausedByUs.toList()) {
            try {
                if (stream.state == StreamState.PAUSED) {
                    stream.start()
                }
            } catch (_: StreamOperationException) {
            } catch (_: ResourceReleasedException) {
            }
        }
        pausedByUs.clear()
    }
}

/**
 * Android [android.media.AudioManager] focus-change values (stable API constants).
 */
internal object AudioFocusInterruptions {
    const val AUDIOFOCUS_GAIN = 1
    const val AUDIOFOCUS_LOSS = -1
    const val AUDIOFOCUS_LOSS_TRANSIENT = -2
    const val AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK = -3

    fun fromFocusChange(change: Int): AudioInterruptionInfo? = when (change) {
        AUDIOFOCUS_GAIN -> AudioInterruptionInfo(AudioInterruptionType.ENDED, shouldResume = true)
        AUDIOFOCUS_LOSS,
        AUDIOFOCUS_LOSS_TRANSIENT,
        AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
        -> AudioInterruptionInfo(AudioInterruptionType.BEGAN, shouldResume = false)
        else -> null
    }
}

internal fun audioInterruptionFromSession(
    typeBegan: Boolean,
    optionShouldResume: Boolean,
): AudioInterruptionInfo = AudioInterruptionInfo(
    type = if (typeBegan) AudioInterruptionType.BEGAN else AudioInterruptionType.ENDED,
    shouldResume = !typeBegan && optionShouldResume,
)
