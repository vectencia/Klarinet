/**
 * @file Biquad.h
 * @brief Second-order IIR (biquad) filter based on the Audio EQ Cookbook.
 *
 * Implements a Direct Form I biquad filter with coefficients computed from the
 * formulas in Robert Bristow-Johnson's "Audio EQ Cookbook"
 * (https://www.w3.org/2011/audio/audio-eq-cookbook.html).
 *
 * The transfer function is:
 * @code
 *         b0 + b1*z^-1 + b2*z^-2
 * H(z) = -------------------------
 *          1 + a1*z^-1 + a2*z^-2
 * @endcode
 *
 * All coefficients are normalised by a0 during configure(), so the denominator
 * leading coefficient is always 1.
 *
 * ## Direct Form I difference equation
 *
 * @code
 * y[n] = b0*x[n] + b1*x[n-1] + b2*x[n-2] - a1*y[n-1] - a2*y[n-2]
 * @endcode
 *
 * This form keeps the feedforward and feedback paths separate, which makes
 * coefficient changes less likely to produce transient clicks compared to
 * Direct Form II (Transposed).
 *
 * @note This class is **not** thread-safe. Each audio channel should use its
 *       own Biquad instance, or process() should be called from a single thread.
 */
#pragma once
namespace klarinet {

/**
 * @enum BiquadType
 * @brief Selects the filter shape for Biquad::configure().
 *
 * Each type corresponds to a distinct set of coefficient formulas from the
 * Audio EQ Cookbook.
 */
enum class BiquadType {
    LowPass,   ///< Low-pass filter — passes frequencies below the cutoff, attenuates above.
    HighPass,  ///< High-pass filter — passes frequencies above the cutoff, attenuates below.
    BandPass,  ///< Band-pass filter — passes a band of frequencies around the center, attenuates others.
    Notch,     ///< Notch (band-reject) filter — attenuates a narrow band around the center frequency.
    Peak,      ///< Peaking EQ filter — boosts or cuts a band around the center frequency by gainDb.
    LowShelf,  ///< Low-shelf filter — boosts or cuts all frequencies below the shelf frequency.
    HighShelf, ///< High-shelf filter — boosts or cuts all frequencies above the shelf frequency.
    AllPass,   ///< All-pass filter — passes all frequencies with unity gain but shifts phase.
};

/**
 * @class Biquad
 * @brief A single second-order IIR filter section (biquad).
 *
 * Typical usage:
 * @code
 * Biquad lpf;
 * lpf.configure(BiquadType::LowPass, 1000.0f, 0.707f, 0.0f, 48000.0f);
 * for (int i = 0; i < numSamples; ++i) {
 *     output[i] = lpf.process(input[i]);
 * }
 * @endcode
 */
class Biquad {
public:
    Biquad() = default;

    /**
     * @brief Compute filter coefficients for the given filter type and parameters.
     *
     * Must be called before process(). Can be called again at any time to
     * change the filter shape without resetting the internal state (allows
     * smooth parameter sweeps, though there may be minor transients).
     *
     * @param type       Filter type (low-pass, high-pass, peak, etc.).
     * @param frequency  Center / cutoff frequency in Hz.
     * @param q          Quality factor (Q). Higher values = narrower bandwidth.
     *                   For LowPass / HighPass, Q = 0.707 gives Butterworth response.
     * @param gainDb     Gain in dB — only used by Peak, LowShelf, and HighShelf types.
     *                   Ignored by other types.
     * @param sampleRate Audio sample rate in Hz.
     */
    void configure(BiquadType type, float frequency, float q, float gainDb, float sampleRate);

    /**
     * @brief Process a single input sample through the filter.
     *
     * Implements the Direct Form I difference equation:
     *   y[n] = b0*x[n] + b1*x[n-1] + b2*x[n-2] - a1*y[n-1] - a2*y[n-2]
     *
     * Called on the **audio thread** — real-time safe.
     *
     * @param input The input sample x[n].
     * @return The filtered output sample y[n].
     */
    float process(float input);

    /**
     * @brief Reset the filter state (delay elements) to zero.
     *
     * Clears x[n-1], x[n-2], y[n-1], y[n-2]. Coefficients are preserved.
     */
    void reset();

private:
    // Normalised filter coefficients (a0 is factored out during configure()).
    float b0_ = 1.0f;  ///< Feedforward coefficient b0 / a0.
    float b1_ = 0.0f;  ///< Feedforward coefficient b1 / a0.
    float b2_ = 0.0f;  ///< Feedforward coefficient b2 / a0.
    float a1_ = 0.0f;  ///< Feedback coefficient a1 / a0.
    float a2_ = 0.0f;  ///< Feedback coefficient a2 / a0.

    // Delay elements (Direct Form I state).
    float x1_ = 0.0f;  ///< Previous input sample x[n-1].
    float x2_ = 0.0f;  ///< Two-samples-ago input x[n-2].
    float y1_ = 0.0f;  ///< Previous output sample y[n-1].
    float y2_ = 0.0f;  ///< Two-samples-ago output y[n-2].
};

} // namespace klarinet
