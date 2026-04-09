#include "EnvelopeFollower.h"
#include <cmath>

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
    float coeff = (inputAbs > envelope_) ? attackCoeff_ : releaseCoeff_;
    envelope_ += coeff * (inputAbs - envelope_);
    return envelope_;
}

void EnvelopeFollower::reset() {
    envelope_ = 0.0f;
}

} // namespace klarinet
