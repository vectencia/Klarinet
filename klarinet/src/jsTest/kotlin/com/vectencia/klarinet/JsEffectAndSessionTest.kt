@file:OptIn(ExperimentalWasmJsInterop::class)

package com.vectencia.klarinet

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.Promise
import kotlin.math.pow
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
    fun wasmGainAttenuatesFullScaleBy20Db(): Promise<JsAny?> {
        val started = startUnityOutput()
        val gain = AudioEffect(AudioEffectType.GAIN)
        gain.setParameter(GainParams.GAIN_DB, -20f)
        val chain = AudioEffectChain()
        chain.add(gain)
        started.stream.effectChain = chain
        started.stream.start()
        return waitMs(400).then {
            val node = started.stream.workletNode ?: error("worklet was not created")
            assertTrue(started.stream.effectInserts.isEmpty())
            analyserMean(node, started.engine.context).then { level ->
                assertEquals(0.1, level as Double, 0.04)
                chain.clear()
                waitMs(500).then {
                    val again = started.stream.workletNode ?: error("worklet was not created")
                    analyserMean(again, started.engine.context).then { cleared ->
                        assertEquals(1.0, cleared as Double, 0.25)
                        started.stream.close()
                        started.engine.close()
                        gain.release()
                        chain.release()
                        null
                    }
                }
            }
        }
    }

    @Test
    fun fadeMsAfterStartDoesNotRetargetTheRamp(): Promise<JsAny?> {
        val started = startUnityOutput()
        val gain = AudioEffect(AudioEffectType.GAIN)
        val chain = AudioEffectChain()
        chain.add(gain)
        started.stream.effectChain = chain
        started.stream.start()
        return waitMs(300).then {
            gain.setParameter(GainParams.FADE_MS, 600f)
            gain.setParameter(GainParams.GAIN_DB, -40f)
            waitMs(150).then {
                gain.setParameter(GainParams.FADE_MS, 10_000f)
                waitMs(200).then {
                    val node = started.stream.workletNode ?: error("worklet was not created")
                    analyserMean(node, started.engine.context).then { level ->
                        assertTrue(
                            (level as Double) < 0.2,
                            "fade update retargeted the ramp, level=$level",
                        )
                        started.stream.close()
                        started.engine.close()
                        gain.release()
                        chain.release()
                        null
                    }
                }
            }
        }
    }

    @Test
    fun wasmGainFollowsChainOrderAfterAWebEffect(): Promise<JsAny?> {
        val started = startUnityOutput()
        val mute = AudioEffect(AudioEffectType.MUTE_SOLO)
        val gain = AudioEffect(AudioEffectType.GAIN)
        gain.setParameter(GainParams.GAIN_DB, -20f)
        val chain = AudioEffectChain()
        chain.add(mute)
        chain.add(gain)
        started.stream.effectChain = chain
        started.stream.start()
        return waitMs(500).then {
            assertEquals(StreamState.STARTED, started.stream.state)
            val insert = started.stream.effectInserts.singleOrNull()
                ?: error("gain did not keep its place after mute")
            val io = started.stream.workletNode ?: error("worklet was not created")
            analyserMean(io, started.engine.context).then { dry ->
                analyserMean(insert, started.engine.context).then { wet ->
                    assertEquals(1.0, dry as Double, 0.25)
                    assertEquals(0.1, wet as Double, 0.04)
                    started.stream.close()
                    started.engine.close()
                    mute.release()
                    gain.release()
                    chain.release()
                    null
                }
            }
        }
    }

    @Test
    fun twoWasmGainsDoNotShareOneParameter(): Promise<JsAny?> {
        val started = startUnityOutput()
        val first = AudioEffect(AudioEffectType.GAIN)
        val second = AudioEffect(AudioEffectType.GAIN)
        first.setParameter(GainParams.GAIN_DB, -20f)
        second.setParameter(GainParams.GAIN_DB, -6f)
        val chain = AudioEffectChain()
        chain.add(first)
        chain.add(second)
        started.stream.effectChain = chain
        started.stream.start()
        val expected = 0.1 * 10.0.pow(-6.0 / 20.0)
        return waitMs(500).then {
            assertTrue(started.stream.effectInserts.isEmpty())
            val node = started.stream.workletNode ?: error("worklet was not created")
            analyserMean(node, started.engine.context).then { level ->
                assertEquals(expected, level as Double, 0.02)
                started.stream.close()
                started.engine.close()
                first.release()
                second.release()
                chain.release()
                null
            }
        }
    }

    @Test
    fun wasmInputCaptureReachesTheRingAttenuated(): Promise<JsAny?> {
        val engine = AudioEngine.create()
        val stream = engine.openStream(
            AudioStreamConfig(
                direction = StreamDirection.INPUT,
                channelCount = 1,
                performanceMode = PerformanceMode.LOW_LATENCY,
            ),
        )
        val gain = AudioEffect(AudioEffectType.GAIN)
        gain.setParameter(GainParams.GAIN_DB, -20f)
        val chain = AudioEffectChain()
        chain.add(gain)
        stream.effectChain = chain
        stream.start()
        val buffer = FloatArray(4096)
        var framesRead = 0
        var peak = 0f
        val pump = startWorkletPump {
            val frames = stream.read(buffer, buffer.size, 0)
            framesRead += frames
            val samples = frames * stream.config.channelCount
            for (index in 0 until samples) {
                val value = kotlin.math.abs(buffer[index])
                if (value > peak) peak = value
            }
        }
        return waitMs(1200).then {
            stopWorkletPump(pump)
            assertEquals(StreamState.STARTED, stream.state)
            assertTrue(framesRead > 1000, "input ring received $framesRead frames")
            assertTrue(peak > 0.04f && peak < 0.25f, "attenuated input peak was $peak")
            stream.close()
            engine.close()
            gain.release()
            chain.release()
            null
        }
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

private class UnityOutput(
    val engine: AudioEngine,
    val stream: AudioStream,
)

private fun startUnityOutput(): UnityOutput {
    val engine = AudioEngine.create()
    val stream = engine.openStream(
        AudioStreamConfig(channelCount = 1, performanceMode = PerformanceMode.LOW_LATENCY),
        object : AudioStreamCallback {
            override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
                for (i in 0 until numFrames) buffer[i] = 1f
                return numFrames
            }
        },
    )
    return UnityOutput(engine, stream)
}

private fun analyserMean(node: AudioNode, context: AudioContext): Promise<Double> {
    val analyser = context.createAnalyser()
    analyser.fftSize = 1024
    node.connect(analyser)
    return waitMs(300).then {
        val mean: Double = js(
            """
            var data = new Float32Array(analyser.fftSize);
            analyser.getFloatTimeDomainData(data);
            var sum = 0;
            for (var i = 0; i < data.length; i++) sum += Math.abs(data[i]);
            sum / data.length
            """,
        )
        mean
    }
}
