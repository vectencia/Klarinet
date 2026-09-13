package com.vectencia.klarinet.demo.web

import com.vectencia.klarinet.AudioEffectType
import com.vectencia.klarinet.AudioScene
import com.vectencia.klarinet.ReverbParams
import com.vectencia.klarinet.SceneEffect
import com.vectencia.klarinet.SceneLayer

object ScenePresets {
    const val LAYER_LOW = "low"
    const val LAYER_HIGH = "high"

    val low = AudioScene("low", listOf(SceneLayer(LAYER_LOW, gainDb = 0f)))
    val high = AudioScene("high", listOf(SceneLayer(LAYER_HIGH, gainDb = 0f)))
    val stack = AudioScene(
        "stack",
        listOf(
            SceneLayer(LAYER_LOW, gainDb = 0f),
            SceneLayer(LAYER_HIGH, gainDb = -6f),
        ),
    )
    val lowHall = AudioScene(
        "low-hall",
        listOf(
            SceneLayer(
                LAYER_LOW,
                gainDb = 0f,
                effects = listOf(
                    SceneEffect(
                        AudioEffectType.REVERB,
                        mapOf(
                            ReverbParams.ROOM_SIZE to 0.7f,
                            ReverbParams.WET_DRY_MIX to 0.4f,
                        ),
                    ),
                ),
            ),
        ),
    )

    val all: List<AudioScene> = listOf(low, high, stack, lowHall)

    fun hzFor(layerId: String): Double = when (layerId) {
        LAYER_LOW -> 220.0
        LAYER_HIGH -> 660.0
        else -> throw IllegalArgumentException("Unknown layer id: $layerId")
    }
}
