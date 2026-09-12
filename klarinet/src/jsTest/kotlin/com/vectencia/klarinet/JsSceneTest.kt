package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertEquals

class JsSceneTest {

    @Test
    fun jsonRoundTripOnJs() {
        val scene = AudioScene(
            id = "web",
            layers = listOf(SceneLayer("pad", gainDb = -6f)),
        )
        assertEquals(scene, AudioSceneJson.decode(AudioSceneJson.encode(scene)))
    }

    @Test
    fun playerTransitionOnJs() {
        AudioEngine.create().use { engine ->
            AudioScenePlayer(engine).use { player ->
                player.transitionTo(
                    AudioScene("a", listOf(SceneLayer("a"))),
                    fadeMs = 0f,
                ) { engine.openStream(AudioStreamConfig()) }
                assertEquals("a", player.current?.id)
                player.transitionTo(
                    AudioScene("b", listOf(SceneLayer("b"))),
                    fadeMs = 0f,
                ) { engine.openStream(AudioStreamConfig()) }
                assertEquals("b", player.current?.id)
            }
        }
    }
}
