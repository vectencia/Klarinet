package com.vectencia.klarinet

import kotlin.math.ceil

/**
 * Loads [AudioScene]s onto live streams and crossfades between them.
 *
 * The host opens each layer's [AudioStream] (file, generator, etc.). This
 * player attaches a mix [AudioEffectType.GAIN] plus the layer's effects,
 * then uses [GainParams.FADE_MS] so A → B has no hole or level spike.
 *
 * ```kotlin
 * AudioScenePlayer(engine).use { player ->
 *     player.transitionTo(sceneA, fadeMs = 0f) { layer ->
 *         engine.openStream(config, callbackFor(layer.id))
 *     }
 *     player.transitionTo(sceneB, fadeMs = 3000f) { layer ->
 *         engine.openStream(config, callbackFor(layer.id))
 *     }
 * }
 * ```
 *
 * Close the player to stop and release every stream and chain it created.
 */
class AudioScenePlayer internal constructor(
    private val engine: AudioEngine,
    private val scheduler: SleepTimerScheduler,
) : AutoCloseable {

    constructor(engine: AudioEngine) : this(engine, platformSleepTimerScheduler())

    fun interface LayerSource {
        fun openStream(layer: SceneLayer): AudioStream
    }

    private val playing = linkedMapOf<String, PlayingLayer>()
    private val departing = mutableListOf<PlayingLayer>()
    private var active = true
    var current: AudioScene? = null
        private set

    internal fun playingIds(): Set<String> = playing.keys.toSet()

    internal fun targetGainDb(layerId: String): Float? =
        playing[layerId]?.gain?.getParameter(GainParams.GAIN_DB)

    /**
     * Crossfade to [scene]. Layers with the same [SceneLayer.id] keep their
     * stream and retarget gain/params. Others fade out and in over [fadeMs].
     */
    fun transitionTo(scene: AudioScene, fadeMs: Float, source: LayerSource) {
        require(fadeMs >= 0f) { "fadeMs must be >= 0" }
        requireActive(active, "AudioScenePlayer")
        val fade = fadeMs
        val incoming = scene.layers.associateBy { it.id }
        val incomingIds = incoming.keys
        val existingIds = playing.keys.toSet()

        for (id in existingIds - incomingIds) {
            fadeOutAndRelease(id, fade)
        }
        for (layer in scene.layers) {
            val existing = playing[layer.id]
            if (existing != null && existing.matches(layer)) {
                applyParams(existing, layer)
                fadeGain(existing.gain, layer.gainDb, fade)
            } else {
                if (existing != null) {
                    fadeOutAndRelease(layer.id, fade)
                }
                playing[layer.id] = startLayer(layer, fade, source)
            }
        }
        current = scene
    }

    override fun close() {
        if (!active) return
        active = false
        val live = playing.values.toList()
        val outgoing = departing.toList()
        playing.clear()
        departing.clear()
        live.forEach { releaseLayer(it) }
        outgoing.forEach { releaseLayer(it) }
        current = null
    }

    private fun startLayer(layer: SceneLayer, fadeMs: Float, source: LayerSource): PlayingLayer {
        val stream = source.openStream(layer)
        val gain = engine.createEffect(AudioEffectType.GAIN)
        gain.setParameter(GainParams.FADE_MS, 0f)
        gain.setParameter(GainParams.GAIN_DB, SILENCE_DB)
        val extras = layer.effects.map { spec ->
            val effect = engine.createEffect(spec.type)
            for ((paramId, value) in spec.params) {
                effect.setParameter(paramId, value)
            }
            effect
        }
        val chain = engine.createEffectChain()
        chain.add(gain)
        extras.forEach { chain.add(it) }
        stream.effectChain = chain
        if (stream.state == StreamState.OPEN || stream.state == StreamState.PAUSED) {
            stream.start()
        }
        fadeGain(gain, layer.gainDb, fadeMs)
        return PlayingLayer(
            id = layer.id,
            stream = stream,
            gain = gain,
            extras = extras,
            chain = chain,
            effectTypes = layer.effects.map { it.type },
        )
    }

    private fun applyParams(playingLayer: PlayingLayer, layer: SceneLayer) {
        for ((effect, spec) in playingLayer.extras.zip(layer.effects)) {
            for ((paramId, value) in spec.params) {
                effect.setParameter(paramId, value)
            }
        }
    }

    private fun fadeGain(gain: AudioEffect, targetDb: Float, fadeMs: Float) {
        gain.setParameter(GainParams.FADE_MS, fadeMs)
        gain.setParameter(GainParams.GAIN_DB, targetDb)
    }

    private fun fadeOutAndRelease(id: String, fadeMs: Float) {
        val layer = playing.remove(id) ?: return
        layer.cancelPending()
        departing += layer
        fadeGain(layer.gain, SILENCE_DB, fadeMs)
        val delay = ceil(fadeMs.toDouble()).toLong().coerceAtLeast(0L)
        layer.pending = scheduler.schedule(delay) {
            if (departing.removeAll { it === layer }) {
                releaseLayer(layer)
            }
        }
    }

    private fun releaseLayer(layer: PlayingLayer) {
        layer.cancelPending()
        departing.removeAll { it === layer }
        if (playing[layer.id] === layer) {
            playing.remove(layer.id)
        }
        try {
            val state = layer.stream.state
            if (
                state == StreamState.STARTED ||
                state == StreamState.PAUSED ||
                state == StreamState.STARTING ||
                state == StreamState.PAUSING
            ) {
                layer.stream.stop()
            }
        } catch (_: StreamOperationException) {
        } catch (_: ResourceReleasedException) {
        }
        try {
            layer.stream.close()
        } catch (_: ResourceReleasedException) {
        }
        try {
            layer.chain.close()
        } catch (_: ResourceReleasedException) {
        }
        layer.extras.forEach {
            try {
                it.close()
            } catch (_: ResourceReleasedException) {
            }
        }
        try {
            layer.gain.close()
        } catch (_: ResourceReleasedException) {
        }
    }

    private class PlayingLayer(
        val id: String,
        val stream: AudioStream,
        val gain: AudioEffect,
        val extras: List<AudioEffect>,
        val chain: AudioEffectChain,
        val effectTypes: List<AudioEffectType>,
        var pending: SleepTimerCancelable? = null,
    ) {
        fun matches(layer: SceneLayer): Boolean =
            effectTypes == layer.effects.map { it.type }

        fun cancelPending() {
            pending?.cancel()
            pending = null
        }
    }

    private companion object {
        const val SILENCE_DB = -80f
    }
}
