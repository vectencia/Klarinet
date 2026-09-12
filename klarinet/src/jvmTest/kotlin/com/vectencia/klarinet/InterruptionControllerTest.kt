package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class InterruptionControllerTest {

    @Test
    fun beganPausesAttachedStreamAndEndedResumes() {
        AudioEngine.create().use { engine ->
            val stream = engine.openStream(AudioStreamConfig())
            stream.start()
            val session = AudioSessionManager()
            val seen = mutableListOf<AudioInterruptionInfo>()
            session.observeInterruptions { seen += it }
            session.attach(stream)
            try {
                session.interruptions.dispatch(
                    AudioInterruptionInfo(AudioInterruptionType.BEGAN, shouldResume = false),
                )
                assertEquals(StreamState.PAUSED, stream.state)
                assertEquals(AudioInterruptionType.BEGAN, seen.last().type)

                session.interruptions.dispatch(
                    AudioInterruptionInfo(AudioInterruptionType.ENDED, shouldResume = true),
                )
                assertEquals(StreamState.STARTED, stream.state)
                assertEquals(AudioInterruptionType.ENDED, seen.last().type)
            } finally {
                if (stream.state == StreamState.STARTED || stream.state == StreamState.PAUSED) {
                    stream.stop()
                }
                stream.close()
            }
        }
    }

    @Test
    fun endedWithoutShouldResumeLeavesPaused() {
        AudioEngine.create().use { engine ->
            val stream = engine.openStream(AudioStreamConfig())
            stream.start()
            val session = AudioSessionManager()
            session.attach(stream)
            try {
                session.interruptions.dispatch(
                    AudioInterruptionInfo(AudioInterruptionType.BEGAN, shouldResume = false),
                )
                session.interruptions.dispatch(
                    AudioInterruptionInfo(AudioInterruptionType.ENDED, shouldResume = false),
                )
                assertEquals(StreamState.PAUSED, stream.state)
            } finally {
                if (stream.state == StreamState.STARTED || stream.state == StreamState.PAUSED) {
                    stream.stop()
                }
                stream.close()
            }
        }
    }

    @Test
    fun detachStopsAutoHandling() {
        AudioEngine.create().use { engine ->
            val stream = engine.openStream(AudioStreamConfig())
            stream.start()
            val session = AudioSessionManager()
            session.attach(stream)
            session.detach(stream)
            try {
                session.interruptions.dispatch(
                    AudioInterruptionInfo(AudioInterruptionType.BEGAN, shouldResume = false),
                )
                assertEquals(StreamState.STARTED, stream.state)
            } finally {
                if (stream.state == StreamState.STARTED || stream.state == StreamState.PAUSED) {
                    stream.stop()
                }
                stream.close()
            }
        }
    }

    @Test
    fun listenerIsSameInstanceAcrossDispatch() {
        val session = AudioSessionManager()
        var last: AudioInterruptionInfo? = null
        val info = AudioInterruptionInfo(AudioInterruptionType.BEGAN, shouldResume = false)
        session.observeInterruptions { last = it }
        session.interruptions.dispatch(info)
        assertSame(info, last)
    }
}
