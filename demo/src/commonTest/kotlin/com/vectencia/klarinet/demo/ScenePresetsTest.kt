package com.vectencia.klarinet.demo

import com.vectencia.klarinet.AudioSceneJson
import com.vectencia.klarinet.AudioEffectType
import com.vectencia.klarinet.ReverbParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScenePresetsTest {
    @Test
    fun fourPresetsWithExpectedIdsAndLayers() {
        assertEquals(listOf("low", "high", "stack", "low-hall"), ScenePresets.all.map { it.id })
        assertEquals(listOf("low"), ScenePresets.low.layers.map { it.id })
        assertEquals(listOf("high"), ScenePresets.high.layers.map { it.id })
        assertEquals(listOf("low", "high"), ScenePresets.stack.layers.map { it.id })
        assertEquals(-6f, ScenePresets.stack.layers[1].gainDb, 0.001f)
        val hall = ScenePresets.lowHall.layers.single()
        assertEquals("low", hall.id)
        val effect = hall.effects.single()
        assertEquals(AudioEffectType.REVERB, effect.type)
        assertEquals(0.7f, effect.params.getValue(ReverbParams.ROOM_SIZE), 0.001f)
        assertEquals(0.4f, effect.params.getValue(ReverbParams.WET_DRY_MIX), 0.001f)
    }

    @Test
    fun jsonRoundTrip() {
        for (scene in ScenePresets.all) {
            assertEquals(scene, AudioSceneJson.decode(AudioSceneJson.encode(scene)))
        }
    }

    @Test
    fun hzForKnownAndUnknown() {
        assertEquals(220.0, ScenePresets.hzFor("low"))
        assertEquals(660.0, ScenePresets.hzFor("high"))
        assertFailsWith<IllegalArgumentException> { ScenePresets.hzFor("mid") }
    }
}
