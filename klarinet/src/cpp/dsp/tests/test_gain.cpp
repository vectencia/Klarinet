#include <cassert>
#include <cmath>
#include <cstdio>

#include "effects/Gain.h"

static bool approxEqual(float a, float b, float tolerance = 1e-4f) {
    return std::fabs(a - b) < tolerance;
}

// ============================================================================
// Gain tests
// ============================================================================

static void test_gain_unity() {
    printf("  Gain: 0dB = unity (no change)... ");
    klarinet::Gain gain;
    gain.prepare(48000, 1);
    gain.setParameter(klarinet::GainParams::kGainDb, 0.0f);

    float buffer[] = {0.5f, -0.5f, 1.0f, -1.0f};
    float expected[] = {0.5f, -0.5f, 1.0f, -1.0f};
    gain.process(buffer, 4, 1);

    for (int i = 0; i < 4; ++i) {
        assert(approxEqual(buffer[i], expected[i]));
    }
    printf("PASS\n");
}

static void test_gain_plus_6db() {
    printf("  Gain: +6dB ~ 2x amplification... ");
    klarinet::Gain gain;
    gain.prepare(48000, 1);
    gain.setParameter(klarinet::GainParams::kGainDb, 6.0f);

    // 10^(6/20) = 1.99526...
    float linear = std::pow(10.0f, 6.0f / 20.0f);

    float buffer[] = {1.0f, -1.0f, 0.25f};
    gain.process(buffer, 3, 1);

    assert(approxEqual(buffer[0], linear, 0.01f));
    assert(approxEqual(buffer[1], -linear, 0.01f));
    assert(approxEqual(buffer[2], 0.25f * linear, 0.01f));
    printf("PASS\n");
}

static void test_gain_minus_20db() {
    printf("  Gain: -20dB = 0.1x... ");
    klarinet::Gain gain;
    gain.prepare(48000, 1);
    gain.setParameter(klarinet::GainParams::kGainDb, -20.0f);

    // 10^(-20/20) = 0.1
    float buffer[] = {1.0f, -1.0f};
    gain.process(buffer, 2, 1);

    assert(approxEqual(buffer[0], 0.1f, 0.001f));
    assert(approxEqual(buffer[1], -0.1f, 0.001f));
    printf("PASS\n");
}

static void test_gain_stereo_both_channels() {
    printf("  Gain: stereo both channels affected equally... ");
    klarinet::Gain gain;
    gain.prepare(48000, 2);
    gain.setParameter(klarinet::GainParams::kGainDb, 6.0f);

    float linear = std::pow(10.0f, 6.0f / 20.0f);

    // Interleaved stereo: [L0, R0, L1, R1]
    float buffer[] = {0.5f, 0.8f, -0.3f, -0.6f};
    gain.process(buffer, 2, 2);

    assert(approxEqual(buffer[0], 0.5f * linear, 0.01f));
    assert(approxEqual(buffer[1], 0.8f * linear, 0.01f));
    assert(approxEqual(buffer[2], -0.3f * linear, 0.01f));
    assert(approxEqual(buffer[3], -0.6f * linear, 0.01f));
    printf("PASS\n");
}

// ============================================================================
// Main
// ============================================================================

int main() {
    printf("=== Gain ===\n");
    test_gain_unity();
    test_gain_plus_6db();
    test_gain_minus_20db();
    test_gain_stereo_both_channels();

    printf("\nAll Gain tests passed!\n");
    return 0;
}
