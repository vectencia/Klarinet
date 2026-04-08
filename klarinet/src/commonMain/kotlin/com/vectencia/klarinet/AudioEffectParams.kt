package com.vectencia.klarinet

/**
 * Describes a single parameter change to be applied to an [AudioEffect].
 *
 * Used with [AudioEffectChain.applyBatch] to apply multiple parameter changes
 * atomically in a single audio processing cycle, avoiding audible glitches.
 *
 * @property effect The target [AudioEffect] whose parameter will be changed.
 * @property paramId The parameter identifier (e.g., [CompressorParams.THRESHOLD]).
 * @property value The new value to set for the parameter.
 * @see AudioEffectChain.applyBatch
 */
data class ParameterChange(
    val effect: AudioEffect,
    val paramId: Int,
    val value: Float,
)

/**
 * Parameter IDs for the [AudioEffectType.GAIN] effect.
 *
 * @see AudioEffect.setParameter
 */
object GainParams {
    /** Signal level adjustment in decibels. Range: -80.0 to 24.0. Default: 0.0. Unit: dB. */
    const val GAIN_DB = 0
}

/**
 * Parameter IDs for the [AudioEffectType.PAN] effect.
 *
 * @see AudioEffect.setParameter
 */
object PanParams {
    /** Stereo pan position. Range: -1.0 (full left) to 1.0 (full right). Default: 0.0 (center). */
    const val PAN = 0
}

/**
 * Parameter IDs for the [AudioEffectType.MUTE_SOLO] effect.
 *
 * @see AudioEffect.setParameter
 */
object MuteSoloParams {
    /** Mute state. Set to 1.0 to mute the signal, 0.0 to unmute. Default: 0.0 (unmuted). */
    const val MUTED = 0
    /** Solo state. Set to 1.0 to solo the signal, 0.0 to unsolo. Default: 0.0 (unsoloed). */
    const val SOLOED = 1
}

/**
 * Parameter IDs for the [AudioEffectType.COMPRESSOR] effect.
 *
 * @see AudioEffect.setParameter
 */
object CompressorParams {
    /** Threshold in dB above which compression begins. Range: -60.0 to 0.0. Default: -20.0. Unit: dB. */
    const val THRESHOLD = 0
    /** Compression ratio (e.g., 4.0 means 4:1 compression). Range: 1.0 to 20.0. Default: 4.0. Unit: ratio. */
    const val RATIO = 1
    /** Time for the compressor to reach full gain reduction after signal exceeds threshold. Range: 0.1 to 100.0. Default: 10.0. Unit: ms. */
    const val ATTACK_MS = 2
    /** Time for the compressor to return to unity gain after signal drops below threshold. Range: 10.0 to 1000.0. Default: 100.0. Unit: ms. */
    const val RELEASE_MS = 3
    /** Additional gain applied after compression to restore perceived loudness. Range: 0.0 to 40.0. Default: 0.0. Unit: dB. */
    const val MAKEUP_GAIN = 4
}

/**
 * Parameter IDs for the [AudioEffectType.LIMITER] effect.
 *
 * @see AudioEffect.setParameter
 */
object LimiterParams {
    /** Ceiling level above which the signal is hard-limited. Range: -60.0 to 0.0. Default: -1.0. Unit: dB. */
    const val THRESHOLD = 0
    /** Time for the limiter to stop attenuating after signal drops below threshold. Range: 1.0 to 500.0. Default: 50.0. Unit: ms. */
    const val RELEASE_MS = 1
}

/**
 * Parameter IDs for the [AudioEffectType.NOISE_GATE] effect.
 *
 * @see AudioEffect.setParameter
 */
object NoiseGateParams {
    /** Level below which the signal is silenced. Range: -80.0 to 0.0. Default: -40.0. Unit: dB. */
    const val THRESHOLD = 0
    /** Time for the gate to fully open once the signal exceeds the threshold. Range: 0.1 to 50.0. Default: 1.0. Unit: ms. */
    const val ATTACK_MS = 1
    /** Time for the gate to fully close once the signal drops below the threshold. Range: 10.0 to 1000.0. Default: 100.0. Unit: ms. */
    const val RELEASE_MS = 2
    /** Duration the gate stays open after the signal drops below the threshold, before the release phase begins. Range: 0.0 to 500.0. Default: 50.0. Unit: ms. */
    const val HOLD_MS = 3
}

/**
 * Parameter IDs for the [AudioEffectType.PARAMETRIC_EQ] effect.
 *
 * Parameters are organized per band. To address a specific band, calculate the
 * parameter offset as `bandIndex * PARAMS_PER_BAND + paramId`. For example,
 * the frequency of band 2 is at offset `2 * 4 + BAND_FREQUENCY = 8`.
 *
 * @see AudioEffect.setParameter
 */
object EQParams {
    /** Number of parameters per EQ band. Used to calculate parameter offsets for multi-band addressing. */
    const val PARAMS_PER_BAND = 4
    /** Center frequency of the band. Range: 20.0 to 20000.0. Default: 1000.0. Unit: Hz. */
    const val BAND_FREQUENCY = 0
    /** Gain adjustment for the band. Range: -24.0 to 24.0. Default: 0.0. Unit: dB. */
    const val BAND_GAIN = 1
    /** Quality factor controlling the bandwidth of the band. Higher values produce a narrower band. Range: 0.1 to 10.0. Default: 1.0. */
    const val BAND_Q = 2
    /** Filter type for the band (e.g., peaking, low-shelf, high-shelf). Interpreted as an integer cast to float. Default: 0 (peaking). */
    const val BAND_TYPE = 3
}

/**
 * Parameter IDs for the [AudioEffectType.LOW_PASS_FILTER] effect.
 *
 * @see AudioEffect.setParameter
 */
object LPFParams {
    /** Cutoff frequency above which signals are attenuated. Range: 20.0 to 20000.0. Default: 20000.0. Unit: Hz. */
    const val CUTOFF_HZ = 0
    /** Resonance (Q) at the cutoff frequency. Higher values produce a sharper peak at the cutoff. Range: 0.1 to 10.0. Default: 0.707. */
    const val RESONANCE = 1
}

/**
 * Parameter IDs for the [AudioEffectType.HIGH_PASS_FILTER] effect.
 *
 * @see AudioEffect.setParameter
 */
object HPFParams {
    /** Cutoff frequency below which signals are attenuated. Range: 20.0 to 20000.0. Default: 20.0. Unit: Hz. */
    const val CUTOFF_HZ = 0
    /** Resonance (Q) at the cutoff frequency. Higher values produce a sharper peak at the cutoff. Range: 0.1 to 10.0. Default: 0.707. */
    const val RESONANCE = 1
}

/**
 * Parameter IDs for the [AudioEffectType.BAND_PASS_FILTER] effect.
 *
 * @see AudioEffect.setParameter
 */
object BPFParams {
    /** Center frequency of the pass band. Range: 20.0 to 20000.0. Default: 1000.0. Unit: Hz. */
    const val CENTER_HZ = 0
    /** Width of the pass band in octaves. Higher values allow a wider range of frequencies through. Range: 0.1 to 4.0. Default: 1.0. Unit: octaves. */
    const val BANDWIDTH = 1
}

/**
 * Parameter IDs for the [AudioEffectType.DELAY] effect.
 *
 * @see AudioEffect.setParameter
 */
object DelayParams {
    /** Delay time between the original signal and its echo. Range: 1.0 to 2000.0. Default: 250.0. Unit: ms. */
    const val TIME_MS = 0
    /** Amount of delayed signal fed back into the input, controlling echo repetitions. Range: 0.0 to 0.95. Default: 0.3. */
    const val FEEDBACK = 1
    /** Balance between dry (original) and wet (delayed) signal. 0.0 = fully dry, 1.0 = fully wet. Range: 0.0 to 1.0. Default: 0.5. */
    const val WET_DRY_MIX = 2
}

/**
 * Parameter IDs for the [AudioEffectType.REVERB] effect.
 *
 * @see AudioEffect.setParameter
 */
object ReverbParams {
    /** Size of the simulated room. Larger values produce longer reverb tails. Range: 0.0 to 1.0. Default: 0.5. */
    const val ROOM_SIZE = 0
    /** High-frequency damping factor. Higher values absorb more high frequencies, producing a warmer sound. Range: 0.0 to 1.0. Default: 0.5. */
    const val DAMPING = 1
    /** Balance between dry (original) and wet (reverb) signal. 0.0 = fully dry, 1.0 = fully wet. Range: 0.0 to 1.0. Default: 0.3. */
    const val WET_DRY_MIX = 2
    /** Stereo width of the reverb. 0.0 = mono, 1.0 = full stereo. Range: 0.0 to 1.0. Default: 1.0. */
    const val WIDTH = 3
}

/**
 * Parameter IDs for the [AudioEffectType.CHORUS] effect.
 *
 * @see AudioEffect.setParameter
 */
object ChorusParams {
    /** Modulation rate of the chorus LFO. Range: 0.1 to 10.0. Default: 1.0. Unit: Hz. */
    const val RATE_HZ = 0
    /** Modulation depth controlling how much the delayed copy is pitch-shifted. Range: 0.0 to 1.0. Default: 0.5. */
    const val DEPTH = 1
    /** Balance between dry (original) and wet (chorus) signal. 0.0 = fully dry, 1.0 = fully wet. Range: 0.0 to 1.0. Default: 0.5. */
    const val WET_DRY_MIX = 2
}

/**
 * Parameter IDs for the [AudioEffectType.FLANGER] effect.
 *
 * @see AudioEffect.setParameter
 */
object FlangerParams {
    /** Modulation rate of the flanger LFO. Range: 0.1 to 10.0. Default: 0.5. Unit: Hz. */
    const val RATE_HZ = 0
    /** Modulation depth controlling the sweep range of the delay. Range: 0.0 to 1.0. Default: 0.5. */
    const val DEPTH = 1
    /** Amount of flanged signal fed back into the input, intensifying the effect. Range: -0.95 to 0.95. Default: 0.0. */
    const val FEEDBACK = 2
    /** Balance between dry (original) and wet (flanged) signal. 0.0 = fully dry, 1.0 = fully wet. Range: 0.0 to 1.0. Default: 0.5. */
    const val WET_DRY_MIX = 3
}

/**
 * Parameter IDs for the [AudioEffectType.PHASER] effect.
 *
 * @see AudioEffect.setParameter
 */
object PhaserParams {
    /** Modulation rate of the phaser LFO. Range: 0.1 to 10.0. Default: 0.5. Unit: Hz. */
    const val RATE_HZ = 0
    /** Modulation depth controlling the sweep range of the all-pass filters. Range: 0.0 to 1.0. Default: 0.5. */
    const val DEPTH = 1
    /** Number of all-pass filter stages. More stages produce more notches. Interpreted as an integer cast to float. Range: 2 to 12. Default: 4. */
    const val STAGES = 2
    /** Amount of phased signal fed back into the input, intensifying the resonant peaks. Range: -0.95 to 0.95. Default: 0.0. */
    const val FEEDBACK = 3
}

/**
 * Parameter IDs for the [AudioEffectType.TREMOLO] effect.
 *
 * @see AudioEffect.setParameter
 */
object TremoloParams {
    /** Modulation rate of the tremolo LFO. Range: 0.1 to 20.0. Default: 5.0. Unit: Hz. */
    const val RATE_HZ = 0
    /** Modulation depth controlling the amplitude variation. 0.0 = no tremolo, 1.0 = full volume swing. Range: 0.0 to 1.0. Default: 0.5. */
    const val DEPTH = 1
}
