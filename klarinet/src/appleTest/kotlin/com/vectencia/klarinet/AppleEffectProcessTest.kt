package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppleEffectProcessTest {

    @Test
    fun gainMinus20dbScalesSamples() {
        AudioEngine.create().use { engine ->
            val effect = engine.createEffect(AudioEffectType.GAIN)
            effect.setParameter(GainParams.GAIN_DB, -20f)
            val chain = engine.createEffectChain()
            chain.add(effect)
            chain.prepare(48_000, 1)

            val buffer = floatArrayOf(1f, -1f, 0.5f)
            chain.process(buffer, 3, 1)

            assertEquals(0.1f, buffer[0], 0.001f)
            assertEquals(-0.1f, buffer[1], 0.001f)
            assertEquals(0.05f, buffer[2], 0.001f)

            chain.close()
            effect.close()
        }
    }

    @Test
    fun gainFadeToSilenceIsContinuous() {
        AudioEngine.create().use { engine ->
            val effect = engine.createEffect(AudioEffectType.GAIN)
            effect.setParameter(GainParams.FADE_MS, 10f)
            effect.setParameter(GainParams.GAIN_DB, -80f)
            val chain = engine.createEffectChain()
            chain.add(effect)
            chain.prepare(48_000, 1)

            val buffer = FloatArray(480) { 1f }
            chain.process(buffer, 480, 1)

            assertTrue(buffer[0] > 0.9f)
            assertTrue(buffer[479] < 0.001f)
            for (i in 1 until buffer.size) {
                assertTrue(buffer[i] <= buffer[i - 1] + 1e-6f)
                assertTrue(buffer[i - 1] - buffer[i] < 0.01f)
            }

            chain.close()
            effect.close()
        }
    }
}
