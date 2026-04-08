/**
 * @file LFO.h
 * @brief Low-Frequency Oscillator (LFO) based on a phase-accumulator sine wave.
 *
 * Generates a sine wave in the range [-1.0, 1.0] at sub-audio frequencies.
 * Used as a modulation source for effects such as Chorus, Flanger, Phaser,
 * and Tremolo.
 *
 * ## Phase accumulator design
 *
 * The oscillator maintains a phase value in radians [0, 2*PI). Each call to
 * nextSample() computes sin(phase) and then advances phase by a pre-computed
 * increment:
 *
 *   phaseIncrement = 2 * PI * frequency / sampleRate
 *
 * When phase exceeds 2*PI it wraps back, ensuring long-term numerical stability
 * without drift.
 *
 * @note This class is **not** thread-safe. It is designed to be called
 *       sample-by-sample from the audio thread.
 */
#pragma once
namespace klarinet {

/**
 * @class LFO
 * @brief Phase-accumulator sine oscillator for low-frequency modulation.
 */
class LFO {
public:
    /**
     * @brief Set the sample rate used for frequency-to-increment conversion.
     *
     * Must be called before setFrequency() so the increment is computed
     * correctly.
     *
     * @param sampleRate Audio sample rate in Hz.
     */
    void prepare(float sampleRate);

    /**
     * @brief Set the oscillation frequency.
     *
     * Recomputes the per-sample phase increment:
     *   phaseIncrement = 2 * PI * hz / sampleRate
     *
     * @param hz Frequency in Hz (typically < 20 Hz for LFO use).
     */
    void setFrequency(float hz);

    /**
     * @brief Generate the next LFO sample and advance the phase.
     *
     * Called once per audio sample on the **audio thread** — real-time safe.
     *
     * @return Sine value in the range [-1.0, 1.0].
     */
    float nextSample();

    /**
     * @brief Reset the oscillator phase to zero.
     */
    void reset();

private:
    float phase_ = 0.0f;           ///< Current phase in radians [0, 2*PI).
    float phaseIncrement_ = 0.0f;  ///< Per-sample phase step (2*PI*freq/sampleRate).
    float sampleRate_ = 48000.0f;  ///< Cached sample rate in Hz.
};

} // namespace klarinet
