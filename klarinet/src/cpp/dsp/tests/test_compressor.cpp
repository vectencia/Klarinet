#include <cassert>
#include <cmath>
#include <cstdio>
#include <vector>

#include "effects/Compressor.h"
#include "effects/Limiter.h"
#include "effects/NoiseGate.h"

static bool approxEqual(float a, float b, float tolerance = 1e-4f) {
    return std::fabs(a - b) < tolerance;
}

// ============================================================================
// Compressor tests
// ============================================================================

static void test_compressor_below_threshold() {
    printf("  Compressor: signal below threshold is not compressed... ");
    klarinet::Compressor comp;
    comp.prepare(48000, 1);
    comp.setParameter(klarinet::CompressorParams::kThreshold, -10.0f);
    comp.setParameter(klarinet::CompressorParams::kRatio, 4.0f);
    comp.setParameter(klarinet::CompressorParams::kAttackMs, 1.0f);
    comp.setParameter(klarinet::CompressorParams::kReleaseMs, 50.0f);
    comp.setParameter(klarinet::CompressorParams::kMakeupGain, 0.0f);

    // Signal at -20 dB (amplitude ~0.1), threshold at -10 dB
    float amplitude = std::pow(10.0f, -20.0f / 20.0f); // 0.1
    int32_t numFrames = 48000; // 1 second
    std::vector<float> buffer(numFrames, amplitude);

    comp.process(buffer.data(), numFrames, 1);

    // Signal is below threshold, so it should pass through uncompressed.
    // After the envelope settles, output should be close to input amplitude.
    float lastSample = buffer[numFrames - 1];
    assert(approxEqual(lastSample, amplitude, 0.01f));
    printf("PASS\n");
}

static void test_compressor_above_threshold() {
    printf("  Compressor: signal above threshold is compressed... ");
    klarinet::Compressor comp;
    comp.prepare(48000, 1);
    comp.setParameter(klarinet::CompressorParams::kThreshold, -20.0f);
    comp.setParameter(klarinet::CompressorParams::kRatio, 4.0f);
    comp.setParameter(klarinet::CompressorParams::kAttackMs, 1.0f);
    comp.setParameter(klarinet::CompressorParams::kReleaseMs, 50.0f);
    comp.setParameter(klarinet::CompressorParams::kMakeupGain, 0.0f);

    // Signal at 0 dB (amplitude 1.0), threshold at -20 dB
    float amplitude = 1.0f;
    int32_t numFrames = 48000;
    std::vector<float> buffer(numFrames, amplitude);

    comp.process(buffer.data(), numFrames, 1);

    // After settling, the signal should be reduced.
    // 0 dB input, -20 dB threshold, ratio 4:1
    // overDb = 0 - (-20) = 20, compressed = -20 + 20/4 = -15
    // gain reduction = -15 - 0 = -15 dB -> linear ~0.178
    float lastSample = buffer[numFrames - 1];
    assert(lastSample < amplitude);
    assert(lastSample < 0.5f); // Should be significantly reduced
    printf("PASS\n");
}

// ============================================================================
// Limiter tests
// ============================================================================

static void test_limiter_clamps_loud_signal() {
    printf("  Limiter: clamps loud signal below threshold... ");
    klarinet::Limiter limiter;
    limiter.prepare(48000, 1);
    limiter.setParameter(klarinet::LimiterParams::kThreshold, -6.0f);
    limiter.setParameter(klarinet::LimiterParams::kReleaseMs, 50.0f);

    // Loud signal at 0 dB (amplitude 1.0), threshold at -6 dB (~0.5)
    float amplitude = 1.0f;
    float threshLinear = std::pow(10.0f, -6.0f / 20.0f); // ~0.501
    int32_t numFrames = 48000;
    std::vector<float> buffer(numFrames, amplitude);

    limiter.process(buffer.data(), numFrames, 1);

    // After settling, output should not exceed threshold
    float lastSample = buffer[numFrames - 1];
    assert(lastSample <= threshLinear + 0.05f);
    printf("PASS\n");
}

// ============================================================================
// NoiseGate tests
// ============================================================================

static void test_noise_gate_gates_quiet_signal() {
    printf("  NoiseGate: gates quiet signal to near-zero... ");
    klarinet::NoiseGate gate;
    gate.prepare(48000, 1);
    gate.setParameter(klarinet::NoiseGateParams::kThreshold, -40.0f);
    gate.setParameter(klarinet::NoiseGateParams::kAttackMs, 1.0f);
    gate.setParameter(klarinet::NoiseGateParams::kReleaseMs, 10.0f);
    gate.setParameter(klarinet::NoiseGateParams::kHoldMs, 1.0f);

    // Very quiet signal at -60 dB (amplitude ~0.001), threshold at -40 dB
    float amplitude = std::pow(10.0f, -60.0f / 20.0f); // 0.001
    int32_t numFrames = 48000;
    std::vector<float> buffer(numFrames, amplitude);

    gate.process(buffer.data(), numFrames, 1);

    // The gate should close and attenuate the signal to near-zero
    float lastSample = buffer[numFrames - 1];
    assert(lastSample < 0.0001f);
    printf("PASS\n");
}

// ============================================================================
// Main
// ============================================================================

int main() {
    printf("=== Compressor ===\n");
    test_compressor_below_threshold();
    test_compressor_above_threshold();

    printf("\n=== Limiter ===\n");
    test_limiter_clamps_loud_signal();

    printf("\n=== NoiseGate ===\n");
    test_noise_gate_gates_quiet_signal();

    printf("\nAll dynamics effect tests passed!\n");
    return 0;
}
