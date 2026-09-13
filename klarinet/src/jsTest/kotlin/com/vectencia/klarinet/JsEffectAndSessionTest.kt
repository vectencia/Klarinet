package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsEffectAndSessionTest {

    @Test
    fun reverbDefaultRoomSizeMatchesKotlinContract() {
        val effect = AudioEffect(AudioEffectType.REVERB)
        assertEquals(0.5f, effect.getParameter(ReverbParams.ROOM_SIZE))
        assertEquals(0.5f, effect.getParameter(ReverbParams.DAMPING))
        assertEquals(0.3f, effect.getParameter(ReverbParams.WET_DRY_MIX))
        assertEquals(1f, effect.getParameter(ReverbParams.WIDTH))
        effect.release()
    }

    @Test
    fun bandwidthOctavesToQMatchesRbJ() {
        assertEquals(1.4142135f, bandwidthOctavesToQ(1f), 0.01f)
    }

    @Test
    fun gainEffectParameters() {
        val effect = AudioEffect(AudioEffectType.GAIN)
        assertEquals(AudioEffectType.GAIN, effect.type)
        assertTrue(effect.isEnabled)
        assertEquals(0f, effect.getParameter(GainParams.GAIN_DB))
        assertEquals(0f, effect.getParameter(GainParams.FADE_MS))
        effect.setParameter(GainParams.GAIN_DB, -6f)
        assertEquals(-6f, effect.getParameter(GainParams.GAIN_DB))
        effect.setParameter(GainParams.FADE_MS, 2000f)
        assertEquals(2000f, effect.getParameter(GainParams.FADE_MS))
        assertEquals(-6f, effect.getParameter(GainParams.GAIN_DB))
        effect.setParameter(99, 12f)
        assertEquals(-6f, effect.getParameter(GainParams.GAIN_DB))
        assertEquals(2000f, effect.getParameter(GainParams.FADE_MS))
        effect.isEnabled = false
        assertFalse(effect.isEnabled)
        effect.release()
        assertFailsWith<ResourceReleasedException> { effect.getParameter(GainParams.GAIN_DB) }
    }

    @Test
    fun effectChainAddRemoveAndBatch() {
        val chain = AudioEffectChain()
        val gain = AudioEffect(AudioEffectType.GAIN)
        val pan = AudioEffect(AudioEffectType.PAN)
        assertEquals(0, chain.effectCount)
        chain.add(gain)
        chain.add(pan)
        assertEquals(2, chain.effectCount)
        chain.applyBatch(listOf(ParameterChange(gain, GainParams.GAIN_DB, 3f)))
        assertEquals(3f, gain.getParameter(GainParams.GAIN_DB))
        chain.remove(pan)
        assertEquals(1, chain.effectCount)
        chain.clear()
        assertEquals(0, chain.effectCount)
        chain.release()
        assertFailsWith<ResourceReleasedException> { chain.add(gain) }
        gain.release()
        pan.release()
    }

    @Test
    fun sessionManagerIsNoOp() {
        val session = AudioSessionManager()
        session.configure(AudioSessionCategory.PLAY_AND_RECORD, AudioSessionMode.VOICE_CHAT)
        session.setActive(true)
        session.observeRouteChanges { }
        session.observeInterruptions { }
        session.setActive(false)
        AudioEngine.create().use { engine ->
            val stream = engine.openStream(AudioStreamConfig())
            session.attach(stream)
            session.detach(stream)
            stream.close()
        }
    }

    @Test
    fun sampleRingWraps() {
        val ring = SampleRing(4)
        assertEquals(3, ring.write(floatArrayOf(1f, 2f, 3f), 0, 3))
        val first = FloatArray(2)
        assertEquals(2, ring.read(first, 0, 2))
        assertEquals(1f, first[0])
        assertEquals(2f, first[1])
        assertEquals(2, ring.write(floatArrayOf(4f, 5f), 0, 2))
        val rest = FloatArray(4)
        assertEquals(3, ring.read(rest, 0, 4))
        assertEquals(3f, rest[0])
        assertEquals(4f, rest[1])
        assertEquals(5f, rest[2])
    }
}
