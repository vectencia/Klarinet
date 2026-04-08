/**
 * @file CircularBuffer.h
 * @brief Fixed-size circular buffer implementing a delay line for audio DSP.
 *
 * A CircularBuffer stores a fixed number of audio samples and supports reading
 * at arbitrary delay offsets (both integer and fractional). It is the building
 * block for delay-based effects such as Delay, Chorus, Flanger, and Reverb.
 *
 * ## Write position
 *
 * Samples are written sequentially. The write cursor wraps around modulo the
 * buffer size, overwriting the oldest sample each time.
 *
 * ## Reading with delay
 *
 * - read(delaySamples) truncates the delay to an integer and returns the
 *   sample that many positions behind the write head.
 * - readLinear(delaySamples) performs linear interpolation between the two
 *   nearest integer delay positions, enabling fractional (sub-sample) delay.
 *   This is essential for modulation effects (chorus, flanger) where the delay
 *   time is swept continuously by an LFO.
 *
 * @note This class is **not** thread-safe. It is intended to be used from a
 *       single thread (typically the audio thread).
 */
#pragma once
#include <vector>
#include <cstdint>

namespace klarinet {

/**
 * @class CircularBuffer
 * @brief Delay line backed by a flat std::vector with modular indexing.
 */
class CircularBuffer {
public:
    /**
     * @brief Construct a CircularBuffer with an optional initial size.
     *
     * If @p maxSizeInSamples is positive the buffer is allocated and zeroed
     * immediately. Otherwise the buffer is empty and setSize() must be called
     * before use.
     *
     * @param maxSizeInSamples Initial buffer size in samples (0 = no allocation).
     */
    explicit CircularBuffer(int32_t maxSizeInSamples = 0);

    /**
     * @brief Resize the buffer.
     *
     * The buffer is resized (and zero-filled on growth). The write position is
     * wrapped to remain within the new bounds.
     *
     * @param sizeInSamples New buffer size in samples.
     */
    void setSize(int32_t sizeInSamples);

    /**
     * @brief Zero-fill the entire buffer and reset the write position to 0.
     */
    void clear();

    /**
     * @brief Write a single sample at the current write position and advance.
     *
     * The write cursor advances by one and wraps around at the buffer boundary.
     *
     * @param sample The audio sample to write.
     */
    void write(float sample);

    /**
     * @brief Read a sample at an integer delay offset behind the write head.
     *
     * The delay is truncated to an integer. A delay of 0 returns the most
     * recently written sample.
     *
     * @param delaySamples Delay in samples (truncated to int).
     * @return The delayed sample, or 0.0f if the buffer is empty.
     */
    float read(float delaySamples) const;

    /**
     * @brief Read a sample at a fractional delay offset using linear interpolation.
     *
     * Interpolates between the two nearest integer delay positions:
     *   result = sample[floor(delay)] + frac * (sample[floor(delay)+1] - sample[floor(delay)])
     *
     * This provides smooth sub-sample delay for modulation effects.
     *
     * @param delaySamples Delay in samples (fractional values are interpolated).
     * @return The interpolated delayed sample, or 0.0f if the buffer is empty.
     */
    float readLinear(float delaySamples) const;

    /**
     * @brief Get the current buffer size.
     * @return Size in samples.
     */
    int32_t getSize() const { return size_; }

private:
    std::vector<float> buffer_;  ///< Backing storage for the delay line.
    int32_t writePos_ = 0;      ///< Current write cursor (wraps modulo size_).
    int32_t size_ = 0;          ///< Current buffer size in samples.
};

} // namespace klarinet
