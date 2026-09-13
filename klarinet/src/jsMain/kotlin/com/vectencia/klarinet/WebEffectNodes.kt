package com.vectencia.klarinet

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sinh

internal fun dbToGain(db: Float): Float = 10f.pow(db / 20f)

/** RBJ: Q = 1 / (2 * sinh(ln(2)/2 * bandwidthOctaves)). */
internal fun bandwidthOctavesToQ(octaves: Float): Float {
    val bw = max(0.001f, octaves)
    return 1f / (2f * max(sinh(ln(2f) / 2f * bw), 0.001f))
}

internal fun defaultEffectParams(type: AudioEffectType): MutableMap<Int, Float> {
    val params = mutableMapOf<Int, Float>()
    when (type) {
        AudioEffectType.GAIN -> {
            params[GainParams.GAIN_DB] = 0f
            params[GainParams.FADE_MS] = 0f
        }
        AudioEffectType.PAN -> params[PanParams.PAN] = 0f
        AudioEffectType.MUTE_SOLO -> {
            params[MuteSoloParams.MUTED] = 0f
            params[MuteSoloParams.SOLOED] = 0f
        }
        AudioEffectType.COMPRESSOR -> {
            params[CompressorParams.THRESHOLD] = -20f
            params[CompressorParams.RATIO] = 4f
            params[CompressorParams.ATTACK_MS] = 10f
            params[CompressorParams.RELEASE_MS] = 100f
            params[CompressorParams.MAKEUP_GAIN] = 0f
        }
        AudioEffectType.LIMITER -> {
            params[LimiterParams.THRESHOLD] = -1f
            params[LimiterParams.RELEASE_MS] = 50f
        }
        AudioEffectType.NOISE_GATE -> {
            params[NoiseGateParams.THRESHOLD] = -40f
            params[NoiseGateParams.ATTACK_MS] = 1f
            params[NoiseGateParams.RELEASE_MS] = 100f
            params[NoiseGateParams.HOLD_MS] = 50f
        }
        AudioEffectType.PARAMETRIC_EQ -> {
            for (band in 0 until 8) {
                val base = band * EQParams.PARAMS_PER_BAND
                params[base + EQParams.BAND_FREQUENCY] = 1000f
                params[base + EQParams.BAND_GAIN] = 0f
                params[base + EQParams.BAND_Q] = 1f
                params[base + EQParams.BAND_TYPE] = 0f
            }
        }
        AudioEffectType.LOW_PASS_FILTER -> {
            params[LPFParams.CUTOFF_HZ] = 20000f
            params[LPFParams.RESONANCE] = 0.707f
        }
        AudioEffectType.HIGH_PASS_FILTER -> {
            params[HPFParams.CUTOFF_HZ] = 20f
            params[HPFParams.RESONANCE] = 0.707f
        }
        AudioEffectType.BAND_PASS_FILTER -> {
            params[BPFParams.CENTER_HZ] = 1000f
            params[BPFParams.BANDWIDTH] = 1f
        }
        AudioEffectType.DELAY -> {
            params[DelayParams.TIME_MS] = 250f
            params[DelayParams.FEEDBACK] = 0.3f
            params[DelayParams.WET_DRY_MIX] = 0.5f
        }
        AudioEffectType.REVERB -> {
            params[ReverbParams.ROOM_SIZE] = 0.5f
            params[ReverbParams.DAMPING] = 0.5f
            params[ReverbParams.WET_DRY_MIX] = 0.3f
            params[ReverbParams.WIDTH] = 1f
        }
        AudioEffectType.CHORUS -> {
            params[ChorusParams.RATE_HZ] = 1f
            params[ChorusParams.DEPTH] = 0.5f
            params[ChorusParams.WET_DRY_MIX] = 0.5f
        }
        AudioEffectType.FLANGER -> {
            params[FlangerParams.RATE_HZ] = 0.5f
            params[FlangerParams.DEPTH] = 0.5f
            params[FlangerParams.FEEDBACK] = 0f
            params[FlangerParams.WET_DRY_MIX] = 0.5f
        }
        AudioEffectType.PHASER -> {
            params[PhaserParams.RATE_HZ] = 0.5f
            params[PhaserParams.DEPTH] = 0.5f
            params[PhaserParams.STAGES] = 4f
            params[PhaserParams.FEEDBACK] = 0f
        }
        AudioEffectType.TREMOLO -> {
            params[TremoloParams.RATE_HZ] = 5f
            params[TremoloParams.DEPTH] = 0.5f
        }
    }
    return params
}

internal class WebEffectGraph(
    val input: GainNode,
    val output: GainNode,
    private val applyParams: (Map<Int, Float>, Boolean) -> Unit,
    private val disposers: List<() -> Unit>,
) {
    fun apply(params: Map<Int, Float>, enabled: Boolean) {
        applyParams(params, enabled)
    }

    fun dispose() {
        disposers.forEach { disposer ->
            try {
                disposer()
            } catch (_: Throwable) {
            }
        }
        try {
            input.disconnect()
        } catch (_: Throwable) {
        }
        try {
            output.disconnect()
        } catch (_: Throwable) {
        }
    }

    companion object {
        fun create(ctx: AudioContext, type: AudioEffectType): WebEffectGraph {
            val input = ctx.createGain()
            val output = ctx.createGain()
            val dry = ctx.createGain()
            val wet = ctx.createGain()
            val disposers = mutableListOf<() -> Unit>()
            input.connect(dry)
            dry.connect(output)
            wet.connect(output)

            val apply = buildGraph(ctx, type, input, dry, wet, disposers)
            apply(defaultEffectParams(type), true)
            return WebEffectGraph(input, output, apply, disposers)
        }
    }
}

private fun applyLinearGain(ctx: AudioContext, param: AudioParam, target: Float, fadeMs: Float) {
    val now = ctx.currentTime
    if (fadeMs <= 0f) {
        try {
            param.cancelScheduledValues(now)
        } catch (_: Throwable) {
        }
        param.value = target
        return
    }
    val current = param.value
    try {
        param.cancelScheduledValues(now)
        param.setValueAtTime(current, now)
        param.linearRampToValueAtTime(target, now + fadeMs / 1000.0)
    } catch (_: Throwable) {
        param.value = target
    }
}

private fun wireMix(dry: GainNode, wet: GainNode, enabled: Boolean, wetAmount: Float? = null) {
    if (!enabled) {
        dry.gain.value = 1f
        wet.gain.value = 0f
        return
    }
    if (wetAmount == null) {
        dry.gain.value = 0f
        wet.gain.value = 1f
    } else {
        dry.gain.value = 1f - wetAmount
        wet.gain.value = wetAmount
    }
}

private fun impulse(ctx: AudioContext, seconds: Float, decay: Float): AudioBuffer {
    val length = max(1, (ctx.sampleRate * seconds).toInt())
    val buffer = ctx.createBuffer(2, length, ctx.sampleRate)
    for (channel in 0 until 2) {
        val data = buffer.getChannelData(channel)
        for (i in 0 until length) {
            val envelope = (1f - i.toFloat() / length).pow(1f + decay * 4f)
            float32Set(data, i, ((js("Math.random()") as Double) * 2.0 - 1.0).toFloat() * envelope)
        }
    }
    return buffer
}

private fun buildGraph(
    ctx: AudioContext,
    type: AudioEffectType,
    input: GainNode,
    dry: GainNode,
    wet: GainNode,
    disposers: MutableList<() -> Unit>,
): (Map<Int, Float>, Boolean) -> Unit {
    fun stopOsc(osc: OscillatorNode) {
        disposers += {
            try {
                osc.stop()
            } catch (_: Throwable) {
            }
            try {
                osc.disconnect()
            } catch (_: Throwable) {
            }
        }
    }

    return when (type) {
        AudioEffectType.GAIN -> {
            val gain = ctx.createGain()
            input.connect(gain)
            gain.connect(wet)
            var lastGainDb: Float? = null
            val apply: (Map<Int, Float>, Boolean) -> Unit = { params, enabled ->
                wireMix(dry, wet, enabled)
                val db = params[GainParams.GAIN_DB] ?: 0f
                val fadeMs = params[GainParams.FADE_MS] ?: 0f
                val target = dbToGain(db)
                val gainChanged = lastGainDb == null || lastGainDb != db
                lastGainDb = db
                if (gainChanged) {
                    applyLinearGain(ctx, gain.gain, target, fadeMs)
                }
            }
            apply
        }
        AudioEffectType.PAN -> {
            val pan = ctx.createStereoPanner()
            input.connect(pan)
            pan.connect(wet)
            val apply: (Map<Int, Float>, Boolean) -> Unit = { params, enabled ->
                wireMix(dry, wet, enabled)
                pan.pan.value = params[PanParams.PAN] ?: 0f
            }
            apply
        }
        AudioEffectType.MUTE_SOLO -> {
            val gain = ctx.createGain()
            input.connect(gain)
            gain.connect(wet)
            val apply: (Map<Int, Float>, Boolean) -> Unit = { params, enabled ->
                wireMix(dry, wet, enabled)
                gain.gain.value = if ((params[MuteSoloParams.MUTED] ?: 0f) >= 0.5f) 0f else 1f
            }
            apply
        }
        AudioEffectType.COMPRESSOR -> {
            val comp = ctx.createDynamicsCompressor()
            val makeup = ctx.createGain()
            input.connect(comp)
            comp.connect(makeup)
            makeup.connect(wet)
            val apply: (Map<Int, Float>, Boolean) -> Unit = { params, enabled ->
                wireMix(dry, wet, enabled)
                comp.threshold.value = params[CompressorParams.THRESHOLD] ?: -20f
                comp.ratio.value = params[CompressorParams.RATIO] ?: 4f
                comp.attack.value = (params[CompressorParams.ATTACK_MS] ?: 10f) / 1000f
                comp.release.value = (params[CompressorParams.RELEASE_MS] ?: 100f) / 1000f
                makeup.gain.value = dbToGain(params[CompressorParams.MAKEUP_GAIN] ?: 0f)
            }
            apply
        }
        AudioEffectType.LIMITER -> {
            val comp = ctx.createDynamicsCompressor()
            input.connect(comp)
            comp.connect(wet)
            val apply: (Map<Int, Float>, Boolean) -> Unit = { params, enabled ->
                wireMix(dry, wet, enabled)
                comp.threshold.value = params[LimiterParams.THRESHOLD] ?: -1f
                comp.knee.value = 0f
                comp.ratio.value = 20f
                comp.attack.value = 0.003f
                comp.release.value = (params[LimiterParams.RELEASE_MS] ?: 50f) / 1000f
            }
            apply
        }
        AudioEffectType.NOISE_GATE -> {
            // Approximation: DynamicsCompressor, not a true gate. HOLD_MS is unused.
            val comp = ctx.createDynamicsCompressor()
            input.connect(comp)
            comp.connect(wet)
            val apply: (Map<Int, Float>, Boolean) -> Unit = { params, enabled ->
                wireMix(dry, wet, enabled)
                comp.threshold.value = params[NoiseGateParams.THRESHOLD] ?: -40f
                comp.knee.value = 0f
                comp.ratio.value = 20f
                comp.attack.value = (params[NoiseGateParams.ATTACK_MS] ?: 1f) / 1000f
                comp.release.value = (params[NoiseGateParams.RELEASE_MS] ?: 100f) / 1000f
            }
            apply
        }
        AudioEffectType.PARAMETRIC_EQ -> {
            val filters = Array(8) { ctx.createBiquadFilter() }
            var prev: AudioNode = input
            for (filter in filters) {
                prev.connect(filter)
                prev = filter
            }
            prev.connect(wet)
            val apply: (Map<Int, Float>, Boolean) -> Unit = { params, enabled ->
                wireMix(dry, wet, enabled)
                filters.forEachIndexed { index, filter ->
                    val base = index * EQParams.PARAMS_PER_BAND
                    val typeId = (params[base + EQParams.BAND_TYPE] ?: 0f).roundToInt()
                    filter.type = when (typeId) {
                        1 -> "lowshelf"
                        2 -> "highshelf"
                        else -> "peaking"
                    }
                    filter.frequency.value = params[base + EQParams.BAND_FREQUENCY] ?: 1000f
                    filter.gain.value = params[base + EQParams.BAND_GAIN] ?: 0f
                    filter.Q.value = params[base + EQParams.BAND_Q] ?: 1f
                }
            }
            apply
        }
        AudioEffectType.LOW_PASS_FILTER,
        AudioEffectType.HIGH_PASS_FILTER,
        AudioEffectType.BAND_PASS_FILTER -> {
            val filter = ctx.createBiquadFilter()
            filter.type = when (type) {
                AudioEffectType.LOW_PASS_FILTER -> "lowpass"
                AudioEffectType.HIGH_PASS_FILTER -> "highpass"
                else -> "bandpass"
            }
            input.connect(filter)
            filter.connect(wet)
            val apply: (Map<Int, Float>, Boolean) -> Unit = { params, enabled ->
                wireMix(dry, wet, enabled)
                val cutoff = params[0] ?: if (type == AudioEffectType.LOW_PASS_FILTER) 20000f else if (type == AudioEffectType.HIGH_PASS_FILTER) 20f else 1000f
                filter.frequency.value = cutoff
                filter.Q.value = if (type == AudioEffectType.BAND_PASS_FILTER) {
                    bandwidthOctavesToQ(params[BPFParams.BANDWIDTH] ?: 1f)
                } else {
                    params[1] ?: 0.707f
                }
            }
            apply
        }
        AudioEffectType.DELAY -> {
            val delay = ctx.createDelay(2.0)
            val feedback = ctx.createGain()
            val mix = ctx.createGain()
            input.connect(delay)
            delay.connect(feedback)
            feedback.connect(delay)
            delay.connect(mix)
            mix.connect(wet)
            val apply: (Map<Int, Float>, Boolean) -> Unit = { params, enabled ->
                val wetAmount = params[DelayParams.WET_DRY_MIX] ?: 0.5f
                wireMix(dry, wet, enabled, wetAmount)
                delay.delayTime.value = (params[DelayParams.TIME_MS] ?: 250f) / 1000f
                feedback.gain.value = params[DelayParams.FEEDBACK] ?: 0.3f
                mix.gain.value = 1f
            }
            apply
        }
        AudioEffectType.REVERB -> {
            // Approximation: Convolver impulse from ROOM_SIZE and DAMPING. WIDTH is unused.
            val convolver = ctx.createConvolver()
            val mix = ctx.createGain()
            input.connect(convolver)
            convolver.connect(mix)
            mix.connect(wet)
            val apply: (Map<Int, Float>, Boolean) -> Unit = { params, enabled ->
                val wetAmount = params[ReverbParams.WET_DRY_MIX] ?: 0.3f
                wireMix(dry, wet, enabled, wetAmount)
                convolver.buffer = impulse(
                    ctx,
                    0.3f + (params[ReverbParams.ROOM_SIZE] ?: 0.5f) * 2.2f,
                    params[ReverbParams.DAMPING] ?: 0.5f,
                )
                mix.gain.value = 1f
            }
            apply
        }
        AudioEffectType.CHORUS,
        AudioEffectType.FLANGER -> {
            val delay = ctx.createDelay(0.05)
            val mix = ctx.createGain()
            val feedback = ctx.createGain()
            val lfo = ctx.createOscillator()
            val depth = ctx.createGain()
            lfo.connect(depth)
            depth.connect(delay.delayTime)
            input.connect(delay)
            delay.connect(feedback)
            feedback.connect(delay)
            delay.connect(mix)
            mix.connect(wet)
            lfo.start()
            stopOsc(lfo)
            val apply: (Map<Int, Float>, Boolean) -> Unit = { params, enabled ->
                val wetParam = if (type == AudioEffectType.CHORUS) ChorusParams.WET_DRY_MIX else FlangerParams.WET_DRY_MIX
                val wetAmount = params[wetParam] ?: 0.5f
                wireMix(dry, wet, enabled, wetAmount)
                val rate = params[0] ?: if (type == AudioEffectType.CHORUS) 1f else 0.5f
                lfo.frequency.value = rate
                val base = if (type == AudioEffectType.FLANGER) 0.003f else 0.015f
                delay.delayTime.value = base
                depth.gain.value = base * (params[1] ?: 0.5f)
                feedback.gain.value = params[FlangerParams.FEEDBACK] ?: 0f
                mix.gain.value = 1f
            }
            apply
        }
        AudioEffectType.PHASER -> {
            val filters = Array(12) {
                ctx.createBiquadFilter().also { filter -> filter.type = "allpass" }
            }
            var prev: AudioNode = input
            for (filter in filters) {
                prev.connect(filter)
                prev = filter
            }
            val mix = ctx.createGain()
            val feedback = ctx.createGain()
            mix.connect(wet)
            val lfo = ctx.createOscillator()
            val depth = ctx.createGain()
            lfo.connect(depth)
            for (filter in filters) {
                depth.connect(filter.frequency)
            }
            lfo.start()
            stopOsc(lfo)
            var mixFrom: AudioNode? = null
            val apply: (Map<Int, Float>, Boolean) -> Unit = { params, enabled ->
                wireMix(dry, wet, enabled)
                val count = min(12, max(2, (params[PhaserParams.STAGES] ?: 4f).roundToInt()))
                val from = filters[count - 1]
                if (mixFrom != from) {
                    try {
                        mixFrom?.disconnect(mix)
                    } catch (_: Throwable) {
                    }
                    try {
                        mixFrom?.disconnect(feedback)
                    } catch (_: Throwable) {
                    }
                    from.connect(mix)
                    from.connect(feedback)
                    try {
                        feedback.disconnect()
                    } catch (_: Throwable) {
                    }
                    feedback.connect(filters[0])
                    mixFrom = from
                }
                feedback.gain.value = params[PhaserParams.FEEDBACK] ?: 0f
                lfo.frequency.value = params[PhaserParams.RATE_HZ] ?: 0.5f
                depth.gain.value = 400f + (params[PhaserParams.DEPTH] ?: 0.5f) * 1600f
                mix.gain.value = 0.7f
            }
            apply
        }
        AudioEffectType.TREMOLO -> {
            val gain = ctx.createGain()
            val lfo = ctx.createOscillator()
            val depth = ctx.createGain()
            val offset = ctx.createConstantSource()
            offset.offset.value = 1f
            offset.start()
            input.connect(gain)
            gain.connect(wet)
            offset.connect(gain.gain)
            lfo.connect(depth)
            depth.connect(gain.gain)
            lfo.start()
            stopOsc(lfo)
            disposers += {
                try {
                    offset.stop()
                } catch (_: Throwable) {
                }
                try {
                    offset.disconnect()
                } catch (_: Throwable) {
                }
            }
            val apply: (Map<Int, Float>, Boolean) -> Unit = { params, enabled ->
                wireMix(dry, wet, enabled)
                lfo.frequency.value = params[TremoloParams.RATE_HZ] ?: 5f
                depth.gain.value = (params[TremoloParams.DEPTH] ?: 0.5f) * 0.5f
            }
            apply
        }
    }
}
