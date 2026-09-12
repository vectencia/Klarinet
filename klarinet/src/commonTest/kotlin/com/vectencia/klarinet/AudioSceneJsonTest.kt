package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AudioSceneJsonTest {

    @Test
    fun roundTripEmptyLayers() {
        val scene = AudioScene(id = "silence")
        assertEquals(scene, AudioSceneJson.decode(AudioSceneJson.encode(scene)))
    }

    @Test
    fun roundTripLayersAndEffects() {
        val scene = AudioScene(
            id = "rain-night",
            layers = listOf(
                SceneLayer(
                    id = "rain",
                    gainDb = -6f,
                    effects = listOf(
                        SceneEffect(
                            type = AudioEffectType.REVERB,
                            params = mapOf(0 to 0.5f, 1 to 0.25f),
                        ),
                    ),
                ),
                SceneLayer(id = "wind", gainDb = -12f),
            ),
        )
        val json = AudioSceneJson.encode(scene)
        assertTrue(json.contains("\"rain-night\""))
        assertTrue(json.contains("REVERB"))
        assertEquals(scene, AudioSceneJson.decode(json))
    }

    @Test
    fun decodeAcceptsWhitespaceAndIntegerParams() {
        val json = """
            {
              "id": "pad",
              "layers": [
                {
                  "id": "drone",
                  "gainDb": 0,
                  "effects": [
                    { "type": "GAIN", "params": { "0": -3 } }
                  ]
                }
              ]
            }
        """.trimIndent()
        val scene = AudioSceneJson.decode(json)
        assertEquals("pad", scene.id)
        assertEquals("drone", scene.layers.single().id)
        assertEquals(0f, scene.layers.single().gainDb)
        assertEquals(AudioEffectType.GAIN, scene.layers.single().effects.single().type)
        assertEquals(-3f, scene.layers.single().effects.single().params[0])
    }

    @Test
    fun decodeEscapedId() {
        val scene = AudioScene(id = "quote \" and \\ slash")
        assertEquals(scene, AudioSceneJson.decode(AudioSceneJson.encode(scene)))
    }

    @Test
    fun decodeRejectsUnknownEffectType() {
        val error = assertFailsWith<SceneFormatException> {
            AudioSceneJson.decode("""{"id":"x","layers":[{"id":"a","effects":[{"type":"NOPE"}]}]}""")
        }
        assertTrue(error.message!!.contains("NOPE"))
    }

    @Test
    fun decodeRejectsMissingId() {
        assertFailsWith<SceneFormatException> {
            AudioSceneJson.decode("""{"layers":[]}""")
        }
    }

    @Test
    fun decodeRejectsGarbage() {
        assertFailsWith<SceneFormatException> {
            AudioSceneJson.decode("not json")
        }
    }
}
