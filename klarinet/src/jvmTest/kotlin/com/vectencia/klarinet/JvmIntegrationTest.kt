package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class JvmIntegrationTest {

    @Test
    fun engineCreatesAndReleases() {
        val engine = AudioEngine.create()
        assertNotNull(engine)
        engine.release()
    }

    @Test
    fun engineListsDevices() {
        val engine = AudioEngine.create()
        val devices = engine.getAvailableDevices()
        assertTrue(devices.isNotEmpty(), "No audio devices found")
        engine.release()
    }

    @Test
    fun engineCreatesEffectAndChain() {
        val engine = AudioEngine.create()
        val effect = engine.createEffect(AudioEffectType.GAIN)
        assertEquals(AudioEffectType.GAIN, effect.type)
        effect.setParameter(0, 0.5f)
        assertEquals(0.5f, effect.getParameter(0))

        val chain = engine.createEffectChain()
        chain.add(effect)
        assertEquals(1, chain.effectCount)

        chain.release()
        effect.release()
        engine.release()
    }

    @Test
    fun sessionManagerNoOp() {
        val manager = AudioSessionManager()
        manager.configure(AudioSessionCategory.PLAYBACK, AudioSessionMode.DEFAULT)
        manager.setActive(true)
        manager.setActive(false)
    }
}
