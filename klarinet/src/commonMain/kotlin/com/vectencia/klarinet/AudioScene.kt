package com.vectencia.klarinet

/**
 * A portable mix description: named layers, levels, and effect parameters.
 *
 * Content (files, generators, UI) stays in the host. [SceneLayer.id] is an
 * opaque key the host maps to audio. Ship scenes as JSON via [AudioSceneJson].
 *
 * @see AudioSceneJson
 * @see AudioScenePlayer
 */
data class AudioScene(
    val id: String,
    val layers: List<SceneLayer> = emptyList(),
)

/**
 * One layer in an [AudioScene].
 *
 * @property id Host-defined key (file id, generator name, etc.).
 * @property gainDb Mix level in dB. Applied with [GainParams] on a Gain effect.
 * @property effects Extra effects after the mix Gain, in chain order.
 */
data class SceneLayer(
    val id: String,
    val gainDb: Float = 0f,
    val effects: List<SceneEffect> = emptyList(),
)

/**
 * One effect on a [SceneLayer], excluding the mix Gain which is [SceneLayer.gainDb].
 *
 * @property type DSP type created with [AudioEngine.createEffect].
 * @property params Parameter id → value, same ids as [GainParams], [ReverbParams], …
 */
data class SceneEffect(
    val type: AudioEffectType,
    val params: Map<Int, Float> = emptyMap(),
)

/**
 * Thrown when [AudioSceneJson.decode] cannot read a scene document.
 */
class SceneFormatException(message: String, cause: Throwable? = null) :
    KlarinetException(message, cause)
