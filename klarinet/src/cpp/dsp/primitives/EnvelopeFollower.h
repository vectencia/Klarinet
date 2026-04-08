/**
 * @file EnvelopeFollower.h
 * @brief Amplitude envelope follower using a one-pole smoothing filter.
 *
 * Tracks the amplitude envelope of an audio signal by smoothing the absolute
 * value of each input sample with separate attack and release time constants.
 * The output is a slowly varying positive value representing the signal's
 * instantaneous loudness.
 *
 * ## Algorithm
 *
 * The envelope follower is a one-pole IIR filter applied to |input|:
 *
 *   if |input| > envelope:
 *       envelope += attackCoeff * (|input| - envelope)    // rising
 *   else:
 *       envelope += releaseCoeff * (|input| - envelope)   // falling
 *
 * This gives exponential approach to the input level, with separate rates for
 * increasing and decreasing signals.
 *
 * ## Coefficient calculation (ms to coefficient)
 *
 * The smoothing coefficient for a given time constant in milliseconds is:
 *
 *   coeff = 1 - exp(-1 / (ms * 0.001 * sampleRate))
 *
 * This yields a one-pole filter whose step response reaches ~63.2% of the
 * target value after `ms` milliseconds.
 *
 * Typical usage in dynamics effects (Compressor, Limiter, NoiseGate):
 *   - Attack:  1 - 30 ms  (fast response to transients).
 *   - Release: 50 - 500 ms (smooth decay to avoid pumping).
 *
 * @note This class is **not** thread-safe. Each channel should use its own
 *       EnvelopeFollower instance.
 */
#pragma once
namespace klarinet {

/**
 * @class EnvelopeFollower
 * @brief One-pole envelope detector with separate attack and release times.
 */
class EnvelopeFollower {
public:
    /**
     * @brief Set the sample rate for coefficient calculation.
     *
     * Must be called before setAttackMs() / setReleaseMs() so that the
     * time-to-coefficient conversion is correct.
     *
     * @param sampleRate Audio sample rate in Hz.
     */
    void prepare(float sampleRate);

    /**
     * @brief Set the attack time.
     *
     * The attack coefficient determines how quickly the envelope rises when
     * the input level increases.
     *
     * @param ms Attack time in milliseconds. Values <= 0 result in
     *           instantaneous response (coeff = 1.0).
     */
    void setAttackMs(float ms);

    /**
     * @brief Set the release time.
     *
     * The release coefficient determines how quickly the envelope falls when
     * the input level decreases.
     *
     * @param ms Release time in milliseconds. Values <= 0 result in
     *           instantaneous response (coeff = 1.0).
     */
    void setReleaseMs(float ms);

    /**
     * @brief Process a single input sample and return the current envelope level.
     *
     * Called once per sample on the **audio thread** — real-time safe.
     *
     * @param input The input audio sample (any sign).
     * @return The current envelope level (always >= 0).
     */
    float process(float input);

    /**
     * @brief Reset the envelope state to zero.
     */
    void reset();

private:
    float attackCoeff_ = 0.0f;   ///< One-pole coefficient for rising envelope.
    float releaseCoeff_ = 0.0f;  ///< One-pole coefficient for falling envelope.
    float envelope_ = 0.0f;      ///< Current smoothed envelope level.
    float sampleRate_ = 48000.0f;///< Cached sample rate in Hz.

    /**
     * @brief Convert a time constant in milliseconds to a one-pole filter coefficient.
     *
     * @param ms         Time constant in milliseconds.
     * @param sampleRate Sample rate in Hz.
     * @return Smoothing coefficient in [0, 1]. Returns 1.0 if ms or sampleRate <= 0
     *         (instantaneous response).
     */
    static float msToCoeff(float ms, float sampleRate);
};

} // namespace klarinet
