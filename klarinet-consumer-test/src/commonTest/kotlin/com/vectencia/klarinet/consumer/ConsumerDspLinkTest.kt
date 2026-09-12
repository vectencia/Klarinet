package com.vectencia.klarinet.consumer

import com.vectencia.klarinet.AudioEffectType
import com.vectencia.klarinet.AudioEngine
import com.vectencia.klarinet.GainParams
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Links against Klarinet with no extra `-lklarinet-dsp` flags.
 * Passing this means the C++ DSP static lib is packed into the klib.
 */
class ConsumerDspLinkTest {

    @Test
    fun consumerCanCreateGainChainWithoutExtraLinkerFlags() {
        AudioEngine.create().use { engine ->
            val effect = engine.createEffect(AudioEffectType.GAIN)
            effect.setParameter(GainParams.GAIN_DB, -6f)
            effect.setParameter(GainParams.FADE_MS, 10f)
            val chain = engine.createEffectChain()
            chain.add(effect)
            assertEquals(1, chain.effectCount)
            chain.close()
            effect.close()
        }
    }
}
