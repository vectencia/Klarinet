/**
 * @file Biquad.cpp
 * @brief Biquad coefficient calculation and Direct Form I processing.
 *
 * Coefficient formulas are from the Audio EQ Cookbook by Robert Bristow-Johnson.
 *
 * ## Intermediate variables (common to all types)
 *
 * - `w0`    = 2 * PI * frequency / sampleRate  (normalised angular frequency)
 * - `cosW0` = cos(w0)
 * - `sinW0` = sin(w0)
 * - `alpha` = sinW0 / (2 * Q)                 (bandwidth parameter)
 * - `A`     = 10^(gainDb / 40)                (linear amplitude for shelving / peaking)
 *
 * After computing the raw {b0, b1, b2, a0, a1, a2} for the chosen type, all
 * coefficients are normalised by dividing by a0 so the denominator leading
 * term is unity.
 */
#include "Biquad.h"
#include <cmath>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

namespace klarinet {

void Biquad::configure(BiquadType type, float frequency, float q, float gainDb, float sampleRate) {
    float w0 = 2.0f * static_cast<float>(M_PI) * frequency / sampleRate;
    float cosW0 = std::cos(w0);
    float sinW0 = std::sin(w0);
    float alpha = sinW0 / (2.0f * q);
    float A = std::pow(10.0f, gainDb / 40.0f);

    float b0, b1, b2, a0, a1, a2;

    switch (type) {
        // -----------------------------------------------------------------
        // LowPass:  H(s) = 1 / (s^2 + s/Q + 1)
        // -----------------------------------------------------------------
        case BiquadType::LowPass:
            b0 = (1.0f - cosW0) / 2.0f;
            b1 = 1.0f - cosW0;
            b2 = (1.0f - cosW0) / 2.0f;
            a0 = 1.0f + alpha;
            a1 = -2.0f * cosW0;
            a2 = 1.0f - alpha;
            break;

        // -----------------------------------------------------------------
        // HighPass:  H(s) = s^2 / (s^2 + s/Q + 1)
        // -----------------------------------------------------------------
        case BiquadType::HighPass:
            b0 = (1.0f + cosW0) / 2.0f;
            b1 = -(1.0f + cosW0);
            b2 = (1.0f + cosW0) / 2.0f;
            a0 = 1.0f + alpha;
            a1 = -2.0f * cosW0;
            a2 = 1.0f - alpha;
            break;

        // -----------------------------------------------------------------
        // BandPass (constant-skirt-gain):  H(s) = s / (s^2 + s/Q + 1)
        // Peak gain = Q.
        // -----------------------------------------------------------------
        case BiquadType::BandPass:
            b0 = alpha;
            b1 = 0.0f;
            b2 = -alpha;
            a0 = 1.0f + alpha;
            a1 = -2.0f * cosW0;
            a2 = 1.0f - alpha;
            break;

        // -----------------------------------------------------------------
        // Notch (band-reject):  H(s) = (s^2 + 1) / (s^2 + s/Q + 1)
        // -----------------------------------------------------------------
        case BiquadType::Notch:
            b0 = 1.0f;
            b1 = -2.0f * cosW0;
            b2 = 1.0f;
            a0 = 1.0f + alpha;
            a1 = -2.0f * cosW0;
            a2 = 1.0f - alpha;
            break;

        // -----------------------------------------------------------------
        // Peak (parametric EQ):
        //   H(s) = (s^2 + s*(A/Q) + 1) / (s^2 + s/(A*Q) + 1)
        // -----------------------------------------------------------------
        case BiquadType::Peak:
            b0 = 1.0f + alpha * A;
            b1 = -2.0f * cosW0;
            b2 = 1.0f - alpha * A;
            a0 = 1.0f + alpha / A;
            a1 = -2.0f * cosW0;
            a2 = 1.0f - alpha / A;
            break;

        // -----------------------------------------------------------------
        // LowShelf:
        //   H(s) = A * [ (A+1) - (A-1)*cos(w0) + 2*sqrt(A)*alpha ]
        //          Uses the 2*sqrt(A)*alpha shelving slope parameter.
        // -----------------------------------------------------------------
        case BiquadType::LowShelf: {
            float twoSqrtAAlpha = 2.0f * std::sqrt(A) * alpha;
            b0 = A * ((A + 1.0f) - (A - 1.0f) * cosW0 + twoSqrtAAlpha);
            b1 = 2.0f * A * ((A - 1.0f) - (A + 1.0f) * cosW0);
            b2 = A * ((A + 1.0f) - (A - 1.0f) * cosW0 - twoSqrtAAlpha);
            a0 = (A + 1.0f) + (A - 1.0f) * cosW0 + twoSqrtAAlpha;
            a1 = -2.0f * ((A - 1.0f) + (A + 1.0f) * cosW0);
            a2 = (A + 1.0f) + (A - 1.0f) * cosW0 - twoSqrtAAlpha;
            break;
        }

        // -----------------------------------------------------------------
        // HighShelf:
        //   H(s) = A * [ (A+1) + (A-1)*cos(w0) + 2*sqrt(A)*alpha ]
        // -----------------------------------------------------------------
        case BiquadType::HighShelf: {
            float twoSqrtAAlpha = 2.0f * std::sqrt(A) * alpha;
            b0 = A * ((A + 1.0f) + (A - 1.0f) * cosW0 + twoSqrtAAlpha);
            b1 = -2.0f * A * ((A - 1.0f) + (A + 1.0f) * cosW0);
            b2 = A * ((A + 1.0f) + (A - 1.0f) * cosW0 - twoSqrtAAlpha);
            a0 = (A + 1.0f) - (A - 1.0f) * cosW0 + twoSqrtAAlpha;
            a1 = 2.0f * ((A - 1.0f) - (A + 1.0f) * cosW0);
            a2 = (A + 1.0f) - (A - 1.0f) * cosW0 - twoSqrtAAlpha;
            break;
        }

        // -----------------------------------------------------------------
        // AllPass:  H(s) = (s^2 - s/Q + 1) / (s^2 + s/Q + 1)
        // Unity gain at all frequencies; only the phase response changes.
        // -----------------------------------------------------------------
        case BiquadType::AllPass:
            b0 = 1.0f - alpha;
            b1 = -2.0f * cosW0;
            b2 = 1.0f + alpha;
            a0 = 1.0f + alpha;
            a1 = -2.0f * cosW0;
            a2 = 1.0f - alpha;
            break;
    }

    // Normalize by a0 so the denominator leading coefficient is 1.
    b0_ = b0 / a0;
    b1_ = b1 / a0;
    b2_ = b2 / a0;
    a1_ = a1 / a0;
    a2_ = a2 / a0;
}

float Biquad::process(float input) {
    // Direct Form I:  y[n] = b0*x[n] + b1*x[n-1] + b2*x[n-2]
    //                       - a1*y[n-1] - a2*y[n-2]
    float output = b0_ * input + b1_ * x1_ + b2_ * x2_ - a1_ * y1_ - a2_ * y2_;
    x2_ = x1_;
    x1_ = input;
    y2_ = y1_;
    y1_ = output;
    return output;
}

void Biquad::reset() {
    x1_ = 0.0f;
    x2_ = 0.0f;
    y1_ = 0.0f;
    y2_ = 0.0f;
}

} // namespace klarinet
