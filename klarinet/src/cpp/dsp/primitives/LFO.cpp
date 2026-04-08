/**
 * @file LFO.cpp
 * @brief Implementation of the LFO phase-accumulator sine oscillator.
 */
#include "LFO.h"
#include <cmath>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

namespace klarinet {

/// Two-pi constant used for phase wrapping and increment calculation.
static constexpr float kTwoPi = 2.0f * static_cast<float>(M_PI);

void LFO::prepare(float sampleRate) {
    sampleRate_ = sampleRate;
    // User should call setFrequency() after prepare() to update the increment.
}

void LFO::setFrequency(float hz) {
    // Convert frequency to per-sample phase increment:
    //   increment = 2*PI * hz / sampleRate
    phaseIncrement_ = kTwoPi * hz / sampleRate_;
}

float LFO::nextSample() {
    float value = std::sin(phase_);
    phase_ += phaseIncrement_;
    // Wrap phase to [0, 2*PI) to prevent unbounded growth.
    if (phase_ >= kTwoPi) {
        phase_ -= kTwoPi;
    }
    return value;
}

void LFO::reset() {
    phase_ = 0.0f;
}

} // namespace klarinet
