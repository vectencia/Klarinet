package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import platform.posix.usleep

class AudioScenePlayerNativeTest {

    @Test
    fun nativeTransitionReleasesOutgoingAfterFade() {
        AudioEngine.create().use { engine ->
            val opened = mutableListOf<AudioStream>()
            AudioScenePlayer(engine).use { player ->
                player.transitionTo(
                    AudioScene("a", listOf(SceneLayer("a"))),
                    fadeMs = 0f,
                ) { engine.openStream(AudioStreamConfig()).also { opened += it } }
                player.transitionTo(
                    AudioScene("b", listOf(SceneLayer("b"))),
                    fadeMs = 80f,
                ) { engine.openStream(AudioStreamConfig()).also { opened += it } }
                assertEquals(StreamState.STARTED, opened[0].state)
                assertEquals(StreamState.STARTED, opened[1].state)
                val deadline = sleepTimerNowMs() + 2_000
                while (
                    sleepTimerNowMs() < deadline &&
                    opened[0].state != StreamState.STOPPED &&
                    opened[0].state != StreamState.CLOSED
                ) {
                    usleep(10_000u)
                }
                assertTrue(opened[0].state == StreamState.STOPPED || opened[0].state == StreamState.CLOSED)
                assertEquals(StreamState.STARTED, opened[1].state)
            }
        }
    }
}
