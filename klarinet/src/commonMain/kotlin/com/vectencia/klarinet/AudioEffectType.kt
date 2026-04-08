package com.vectencia.klarinet

/**
 * Enumerates the types of audio effects available in the Klarinet SDK.
 *
 * Each value corresponds to a native DSP processor that can be instantiated via
 * [AudioEngine.createEffect]. The associated parameter constants object (e.g.,
 * [GainParams], [CompressorParams]) defines the parameter IDs for configuring
 * each effect type.
 *
 * @see AudioEffect
 * @see AudioEngine.createEffect
 */
enum class AudioEffectType {

    /** Adjusts the signal level. Use [GainParams] for parameter IDs. */
    GAIN,

    /** Controls stereo positioning by distributing signal between left and right channels. Use [PanParams] for parameter IDs. */
    PAN,

    /** Mutes or solos the signal, useful in multi-track mixing scenarios. Use [MuteSoloParams] for parameter IDs. */
    MUTE_SOLO,

    /** Reduces dynamic range by attenuating signals above a threshold, making loud and quiet parts more even. Use [CompressorParams] for parameter IDs. */
    COMPRESSOR,

    /** Prevents signal from exceeding a ceiling level, acting as a brick-wall compressor with a very high ratio. Use [LimiterParams] for parameter IDs. */
    LIMITER,

    /** Silences signals that fall below a threshold, reducing background noise during quiet passages. Use [NoiseGateParams] for parameter IDs. */
    NOISE_GATE,

    /** A multi-band equalizer that boosts or cuts specific frequency ranges with adjustable center frequency, gain, Q factor, and filter type per band. Use [EQParams] for parameter IDs. */
    PARAMETRIC_EQ,

    /** Attenuates frequencies above a cutoff point, allowing only lower frequencies to pass through. Use [LPFParams] for parameter IDs. */
    LOW_PASS_FILTER,

    /** Attenuates frequencies below a cutoff point, allowing only higher frequencies to pass through. Use [HPFParams] for parameter IDs. */
    HIGH_PASS_FILTER,

    /** Passes frequencies within a range around a center frequency and attenuates those outside it. Use [BPFParams] for parameter IDs. */
    BAND_PASS_FILTER,

    /** Creates echoes by repeating the signal after a configurable time interval with optional feedback. Use [DelayParams] for parameter IDs. */
    DELAY,

    /** Simulates acoustic spaces by blending the original signal with reflections, producing a sense of room or hall ambience. Use [ReverbParams] for parameter IDs. */
    REVERB,

    /** Creates a shimmering, thickened sound by mixing the signal with slightly detuned, modulated copies of itself. Use [ChorusParams] for parameter IDs. */
    CHORUS,

    /** Produces a sweeping, jet-like effect by mixing the signal with a short, modulated delayed copy and feeding it back. Use [FlangerParams] for parameter IDs. */
    FLANGER,

    /** Creates a sweeping, notch-filter effect by splitting the signal through a series of all-pass filters with modulated phase shifts. Use [PhaserParams] for parameter IDs. */
    PHASER,

    /** Produces a rhythmic volume modulation by periodically varying the signal amplitude. Use [TremoloParams] for parameter IDs. */
    TREMOLO,
}
