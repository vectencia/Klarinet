/**
 * @file CircularBuffer.cpp
 * @brief Implementation of the CircularBuffer delay line.
 *
 * ## Read position calculation
 *
 * The read position for a given delay is:
 *   readPos = (writePos_ - 1 - delay) mod size_
 *
 * The "- 1" accounts for the fact that writePos_ points to the *next* slot to
 * be written, so the most recently written sample is at writePos_ - 1.
 *
 * Negative values are handled by repeatedly adding size_ until positive (safe
 * because delay is always less than size_ in normal usage).
 */
#include "CircularBuffer.h"
#include <algorithm>
#include <cmath>

namespace klarinet {

CircularBuffer::CircularBuffer(int32_t maxSizeInSamples) {
    if (maxSizeInSamples > 0) {
        setSize(maxSizeInSamples);
    }
}

void CircularBuffer::setSize(int32_t sizeInSamples) {
    size_ = sizeInSamples;
    buffer_.resize(static_cast<size_t>(size_), 0.0f);
    if (size_ > 0) {
        writePos_ = writePos_ % size_;
    } else {
        writePos_ = 0;
    }
}

void CircularBuffer::clear() {
    std::fill(buffer_.begin(), buffer_.end(), 0.0f);
    writePos_ = 0;
}

void CircularBuffer::write(float sample) {
    if (size_ == 0) return;
    buffer_[static_cast<size_t>(writePos_)] = sample;
    writePos_ = (writePos_ + 1) % size_;
}

float CircularBuffer::read(float delaySamples) const {
    if (size_ == 0) return 0.0f;
    int32_t delay = static_cast<int32_t>(delaySamples);
    int32_t readPos = writePos_ - 1 - delay;
    while (readPos < 0) readPos += size_;
    readPos = readPos % size_;
    return buffer_[static_cast<size_t>(readPos)];
}

float CircularBuffer::readLinear(float delaySamples) const {
    if (size_ == 0) return 0.0f;

    // Decompose into integer and fractional parts for linear interpolation.
    float frac = delaySamples - std::floor(delaySamples);
    int32_t delay0 = static_cast<int32_t>(std::floor(delaySamples));
    int32_t delay1 = delay0 + 1;

    // Compute read positions for the two bracketing integer delays.
    int32_t readPos0 = writePos_ - 1 - delay0;
    while (readPos0 < 0) readPos0 += size_;
    readPos0 = readPos0 % size_;

    int32_t readPos1 = writePos_ - 1 - delay1;
    while (readPos1 < 0) readPos1 += size_;
    readPos1 = readPos1 % size_;

    // Linear interpolation: s0 + frac * (s1 - s0)
    float s0 = buffer_[static_cast<size_t>(readPos0)];
    float s1 = buffer_[static_cast<size_t>(readPos1)];
    return s0 + frac * (s1 - s0);
}

} // namespace klarinet
