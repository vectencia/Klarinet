package com.vectencia.klarinet

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioEffectProcessTest {

    @Test
    fun gainMinus20dbScalesSamples() {
        AudioEngine.create().use { engine ->
            val effect = engine.createEffect(AudioEffectType.GAIN)
            effect.setParameter(GainParams.GAIN_DB, -20f)
            val chain = engine.createEffectChain()
            chain.add(effect)
            JniBridge.nativeChainPrepare(chain.chainHandle, 48_000, 1)

            val buffer = floatArrayOf(1f, -1f, 0.5f)
            JniBridge.nativeChainProcess(chain.chainHandle, buffer, 3, 1)

            assertEquals(0.1f, buffer[0], 0.001f)
            assertEquals(-0.1f, buffer[1], 0.001f)
            assertEquals(0.05f, buffer[2], 0.001f)

            chain.close()
            effect.close()
        }
    }

    @Test
    fun disabledGainIsPassthrough() {
        AudioEngine.create().use { engine ->
            val effect = engine.createEffect(AudioEffectType.GAIN)
            effect.setParameter(GainParams.GAIN_DB, -20f)
            effect.isEnabled = false
            val chain = engine.createEffectChain()
            chain.add(effect)
            JniBridge.nativeChainPrepare(chain.chainHandle, 48_000, 1)

            val buffer = floatArrayOf(0.8f, -0.4f)
            JniBridge.nativeChainProcess(chain.chainHandle, buffer, 2, 1)

            assertEquals(0.8f, buffer[0], 0.0001f)
            assertEquals(-0.4f, buffer[1], 0.0001f)

            chain.close()
            effect.close()
        }
    }

    @Test
    fun attachingChainToStreamDoesNotThrow() {
        AudioEngine.create().use { engine ->
            val stream = engine.openStream(AudioStreamConfig())
            val effect = engine.createEffect(AudioEffectType.GAIN)
            effect.setParameter(GainParams.GAIN_DB, -6f)
            val chain = engine.createEffectChain()
            chain.add(effect)
            stream.effectChain = chain
            assertEquals(1, stream.effectChain?.effectCount)
            stream.effectChain = null
            stream.close()
            chain.close()
            effect.close()
        }
    }

    @Test
    fun getParameterReadsNativeGain() {
        AudioEngine.create().use { engine ->
            val effect = engine.createEffect(AudioEffectType.GAIN)
            assertTrue(abs(effect.getParameter(GainParams.GAIN_DB)) < 0.001f)
            effect.setParameter(GainParams.GAIN_DB, 6f)
            assertEquals(6f, effect.getParameter(GainParams.GAIN_DB), 0.001f)
            effect.setParameter(GainParams.FADE_MS, 10f)
            assertEquals(10f, effect.getParameter(GainParams.FADE_MS), 0.001f)
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
            JniBridge.nativeChainPrepare(chain.chainHandle, 48_000, 1)

            val buffer = FloatArray(480) { 1f }
            JniBridge.nativeChainProcess(chain.chainHandle, buffer, 480, 1)

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
