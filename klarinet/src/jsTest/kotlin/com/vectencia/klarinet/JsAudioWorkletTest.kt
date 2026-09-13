@file:OptIn(ExperimentalWasmJsInterop::class)

package com.vectencia.klarinet

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertTrue

class JsAudioWorkletTest {

    @Test
    fun workletModuleLoads(): Promise<JsAny?> {
        val engine = AudioEngine.create()
        return ensureKlarinetWorklet(engine.context).then(
            {
                engine.close()
                null
            },
            { error ->
                engine.close()
                throw error
            },
        )
    }

    @Test
    fun startCreatesAudioWorkletNode(): Promise<JsAny?> {
        val engine = AudioEngine.create()
        val stream = engine.openStream(
            AudioStreamConfig(channelCount = 1, performanceMode = PerformanceMode.LOW_LATENCY),
        )
        stream.start()
        return ensureKlarinetWorklet(engine.context)
            .then { waitMs(50) }
            .then {
                assertTrue(
                    stream.usesAudioWorklet(),
                    "AudioStream should attach an AudioWorkletNode after start (state=${stream.state})",
                )
                stream.close()
                engine.close()
                null
            }
    }
}
