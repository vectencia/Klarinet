package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AudioScenePlayerTest {

    @Test
    fun crossfadeKeepsBothStreamsUntilFadeEnds() {
        AudioEngine.create().use { engine ->
            val clock = ManualClock()
            val scheduler = ManualScheduler(clock)
            val streams = linkedMapOf<String, AudioStream>()
            AudioScenePlayer(engine, scheduler).use { player ->
                val rain = AudioScene("rain", listOf(SceneLayer("rain", gainDb = -6f)))
                val wind = AudioScene("wind", listOf(SceneLayer("wind", gainDb = -3f)))
                player.transitionTo(rain, fadeMs = 0f) { open(engine, streams, it) }
                val rainStream = streams.getValue("rain")
                assertEquals(StreamState.STARTED, rainStream.state)
                assertEquals(-6f, player.targetGainDb("rain")!!, 0.001f)

                player.transitionTo(wind, fadeMs = 200f) { open(engine, streams, it) }
                val windStream = streams.getValue("wind")
                assertEquals(StreamState.STARTED, rainStream.state)
                assertEquals(StreamState.STARTED, windStream.state)
                assertEquals(-3f, player.targetGainDb("wind")!!, 0.001f)
                assertEquals(setOf("wind"), player.playingIds())

                scheduler.advance(200)
                assertTrue(
                    rainStream.state == StreamState.STOPPED || rainStream.state == StreamState.CLOSED,
                )
                assertEquals(StreamState.STARTED, windStream.state)
            }
        }
    }

    @Test
    fun sameLayerIdRetargetsGainOnOneStream() {
        AudioEngine.create().use { engine ->
            val clock = ManualClock()
            val scheduler = ManualScheduler(clock)
            val streams = linkedMapOf<String, AudioStream>()
            AudioScenePlayer(engine, scheduler).use { player ->
                player.transitionTo(
                    AudioScene("a", listOf(SceneLayer("pad", gainDb = -6f))),
                    fadeMs = 0f,
                ) { open(engine, streams, it) }
                val first = streams.getValue("pad")
                player.transitionTo(
                    AudioScene("b", listOf(SceneLayer("pad", gainDb = -12f))),
                    fadeMs = 150f,
                ) { open(engine, streams, it) }
                assertSame(first, streams["pad"])
                assertEquals(StreamState.STARTED, first.state)
                assertEquals(1, streams.size)
                assertEquals(-12f, player.targetGainDb("pad")!!, 0.001f)
            }
        }
    }

    @Test
    fun chainedScenesReleaseOutgoingStreams() {
        AudioEngine.create().use { engine ->
            val clock = ManualClock()
            val scheduler = ManualScheduler(clock)
            val streams = mutableListOf<AudioStream>()
            AudioScenePlayer(engine, scheduler).use { player ->
                repeat(3) { index ->
                    val id = "s$index"
                    player.transitionTo(
                        AudioScene(id, listOf(SceneLayer(id, gainDb = 0f))),
                        fadeMs = 50f,
                    ) { layer ->
                        engine.openStream(AudioStreamConfig()).also { streams += it }
                    }
                    scheduler.advance(50)
                }
                val live = streams.count {
                    it.state == StreamState.STARTED || it.state == StreamState.PAUSED
                }
                assertEquals(1, live)
            }
            assertTrue(streams.all { it.state == StreamState.CLOSED || it.state == StreamState.STOPPED })
        }
    }

    @Test
    fun closeIsIdempotentAndStopsPlayback() {
        AudioEngine.create().use { engine ->
            val streams = mutableListOf<AudioStream>()
            val player = AudioScenePlayer(engine)
            player.transitionTo(
                AudioScene("x", listOf(SceneLayer("x"))),
                fadeMs = 0f,
            ) { engine.openStream(AudioStreamConfig()).also { streams += it } }
            player.close()
            player.close()
            assertTrue(streams.single().state == StreamState.CLOSED || streams.single().state == StreamState.STOPPED)
        }
    }

    @Test
    fun realSchedulerTransitionReleasesOutgoing() {
        AudioEngine.create().use { engine ->
            val opened = mutableListOf<AudioStream>()
            AudioScenePlayer(engine).use { player ->
                player.transitionTo(
                    AudioScene("a", listOf(SceneLayer("a", gainDb = 0f))),
                    fadeMs = 0f,
                ) { engine.openStream(AudioStreamConfig()).also { opened += it } }
                player.transitionTo(
                    AudioScene("b", listOf(SceneLayer("b", gainDb = 0f))),
                    fadeMs = 80f,
                ) { engine.openStream(AudioStreamConfig()).also { opened += it } }
                assertEquals(StreamState.STARTED, opened[0].state)
                assertEquals(StreamState.STARTED, opened[1].state)
                val deadline = System.nanoTime() + 2_000_000_000L
                while (
                    System.nanoTime() < deadline &&
                    opened[0].state != StreamState.STOPPED &&
                    opened[0].state != StreamState.CLOSED
                ) {
                    Thread.sleep(10)
                }
                assertTrue(opened[0].state == StreamState.STOPPED || opened[0].state == StreamState.CLOSED)
                assertEquals(StreamState.STARTED, opened[1].state)
            }
        }
    }

    @Test
    fun graphChangeCrossfadesNewStream() {
        AudioEngine.create().use { engine ->
            val clock = ManualClock()
            val scheduler = ManualScheduler(clock)
            val opened = mutableListOf<AudioStream>()
            AudioScenePlayer(engine, scheduler).use { player ->
                player.transitionTo(
                    AudioScene("a", listOf(SceneLayer("voice", gainDb = 0f))),
                    fadeMs = 0f,
                ) { engine.openStream(AudioStreamConfig()).also { opened += it } }
                player.transitionTo(
                    AudioScene(
                        "b",
                        listOf(
                            SceneLayer(
                                "voice",
                                gainDb = 0f,
                                effects = listOf(SceneEffect(AudioEffectType.REVERB)),
                            ),
                        ),
                    ),
                    fadeMs = 100f,
                ) { engine.openStream(AudioStreamConfig()).also { opened += it } }
                assertEquals(2, opened.size)
                assertNotSame(opened[0], opened[1])
                assertEquals(StreamState.STARTED, opened[0].state)
                assertEquals(StreamState.STARTED, opened[1].state)
                scheduler.advance(100)
                assertTrue(opened[0].state == StreamState.STOPPED || opened[0].state == StreamState.CLOSED)
                assertEquals(StreamState.STARTED, opened[1].state)
            }
        }
    }
}

private fun open(
    engine: AudioEngine,
    streams: MutableMap<String, AudioStream>,
    layer: SceneLayer,
): AudioStream = engine.openStream(AudioStreamConfig()).also { streams[layer.id] = it }
