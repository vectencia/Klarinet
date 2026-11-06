/**
 * @file EnvelopeFollower.cpp
 * @brief Implementation of the one-pole envelope follower.
 *
 * ## msToCoeff derivation
 *
 * For a one-pole filter  y[n] = y[n-1] + coeff * (x[n] - y[n-1]),
 * the step response after N samples is  1 - (1 - coeff)^N.
 *
 * We want the response to reach ~63.2% (one time constant) after `ms`
 * milliseconds, i.e., after N = ms * 0.001 * sampleRate samples:
 *
 *   1 - (1 - coeff)^N = 1 - 1/e
 *   (1 - coeff)^N     = 1/e
 *   N * ln(1 - coeff) = -1
 *   coeff             = 1 - exp(-1/N)
 *
 * Which simplifies to:  coeff = 1 - exp(-1 / (ms * 0.001 * sampleRate))
 */
#include "EnvelopeFollower.h"
#include <cmath>

#ifdef KLARINET_DSP_RUST_EFFECTS
#include "klarinet_fx.h"

namespace klarinet {

EnvelopeFollower::EnvelopeFollower() : rust_(klarinet_env_create()) {}

EnvelopeFollower::~EnvelopeFollower() {
    if (rust_ != nullptr) {
        klarinet_env_destroy(rust_);
    }
}

void EnvelopeFollower::prepare(float sampleRate) {
    klarinet_env_prepare(rust_, sampleRate);
}

void EnvelopeFollower::setAttackMs(float ms) {
    klarinet_env_set_attack(rust_, ms);
}

void EnvelopeFollower::setReleaseMs(float ms) {
    klarinet_env_set_release(rust_, ms);
}

float EnvelopeFollower::process(float input) {
    return klarinet_env_process(rust_, input);
}

void EnvelopeFollower::reset() {
    klarinet_env_reset(rust_);
}

} // namespace klarinet
#else
namespace klarinet {

float EnvelopeFollower::msToCoeff(float ms, float sampleRate) {
    if (ms <= 0.0f || sampleRate <= 0.0f) return 1.0f;
    return 1.0f - std::exp(-1.0f / (ms * 0.001f * sampleRate));
}

void EnvelopeFollower::prepare(float sampleRate) {
    sampleRate_ = sampleRate;
}

void EnvelopeFollower::setAttackMs(float ms) {
    attackCoeff_ = msToCoeff(ms, sampleRate_);
}

void EnvelopeFollower::setReleaseMs(float ms) {
    releaseCoeff_ = msToCoeff(ms, sampleRate_);
}

float EnvelopeFollower::process(float input) {
    float inputAbs = std::fabs(input);
    // Select attack or release coefficient depending on whether the signal
    // is rising above or falling below the current envelope.
    float coeff = (inputAbs > envelope_) ? attackCoeff_ : releaseCoeff_;
    // One-pole smoothing: envelope approaches |input| exponentially.
    envelope_ += coeff * (inputAbs - envelope_);
    return envelope_;
}

void EnvelopeFollower::reset() {
    envelope_ = 0.0f;
}

} // namespace klarinet

#endif
