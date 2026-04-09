#include <cassert>
#include <cmath>
#include <cstdio>
#include <vector>

#include "effects/Tremolo.h"
#include "effects/Chorus.h"
#include "effects/Flanger.h"
#include "effects/Phaser.h"

static bool approxEqual(float a, float b, float tolerance = 1e-4f) {
    return std::fabs(a - b) < tolerance;
}

// ============================================================================
// Tremolo tests
// ============================================================================

static void test_tremolo_zero_depth() {
    printf("  Tremolo: zero depth = no modulation... ");
    klarinet::Tremolo trem;
    trem.prepare(48000, 1);
    trem.setParameter(klarinet::TremoloParams::kDepth, 0.0f);
    trem.setParameter(klarinet::TremoloParams::kRateHz, 5.0f);

    float buffer[] = {1.0f, 1.0f, 1.0f, 1.0f};
    trem.process(buffer, 4, 1);

    for (int i = 0; i < 4; ++i) {
        assert(approxEqual(buffer[i], 1.0f, 0.001f));
    }
    printf("PASS\n");
}

static void test_tremolo_full_depth_range() {
    printf("  Tremolo: full depth produces values in [0, 1]... ");
    klarinet::Tremolo trem;
    trem.prepare(48000, 1);
    trem.setParameter(klarinet::TremoloParams::kDepth, 1.0f);
    trem.setParameter(klarinet::TremoloParams::kRateHz, 5.0f);

    // Process enough samples to cover one full LFO cycle
    int32_t numFrames = 48000 / 5 + 1; // ~one cycle at 5Hz
    std::vector<float> buffer(numFrames, 1.0f);
    trem.process(buffer.data(), numFrames, 1);

    float minVal = 1.0f;
    float maxVal = 0.0f;
    for (int32_t i = 0; i < numFrames; ++i) {
        if (buffer[i] < minVal) minVal = buffer[i];
        if (buffer[i] > maxVal) maxVal = buffer[i];
    }

    // With depth=1: mod ranges from 0 to 1
    assert(minVal < 0.05f);  // Should reach near 0
    assert(maxVal > 0.95f);  // Should reach near 1
    printf("PASS (min=%.4f, max=%.4f)\n", minVal, maxVal);
}

static void test_tremolo_stereo() {
    printf("  Tremolo: stereo channels get same modulation... ");
    klarinet::Tremolo trem;
    trem.prepare(48000, 2);
    trem.setParameter(klarinet::TremoloParams::kDepth, 0.5f);
    trem.setParameter(klarinet::TremoloParams::kRateHz, 2.0f);

    // Interleaved stereo: [L0, R0, L1, R1, ...]
    float buffer[] = {1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f};
    trem.process(buffer, 4, 2);

    // Each frame's L and R should be equal (same mod applied)
    for (int i = 0; i < 4; ++i) {
        assert(approxEqual(buffer[i * 2], buffer[i * 2 + 1], 1e-6f));
    }
    printf("PASS\n");
}

// ============================================================================
// Chorus tests
// ============================================================================

static void test_chorus_produces_output() {
    printf("  Chorus: sine input produces non-silent output... ");
    klarinet::Chorus chorus;
    chorus.prepare(48000, 1);
    chorus.setParameter(klarinet::ChorusParams::kRateHz, 1.0f);
    chorus.setParameter(klarinet::ChorusParams::kDepth, 0.5f);
    chorus.setParameter(klarinet::ChorusParams::kWetDryMix, 0.5f);

    // Generate a 440Hz sine wave
    int32_t numFrames = 4800;
    std::vector<float> buffer(numFrames);
    for (int32_t i = 0; i < numFrames; ++i) {
        buffer[i] = std::sin(2.0f * 3.14159265f * 440.0f * i / 48000.0f);
    }

    chorus.process(buffer.data(), numFrames, 1);

    // Check output is not silent
    float energy = 0.0f;
    for (int32_t i = 0; i < numFrames; ++i) {
        energy += buffer[i] * buffer[i];
    }
    assert(energy > 0.1f);
    printf("PASS (energy=%.4f)\n", energy);
}

static void test_chorus_dry_only() {
    printf("  Chorus: mix=0 passes dry signal through... ");
    klarinet::Chorus chorus;
    chorus.prepare(48000, 1);
    chorus.setParameter(klarinet::ChorusParams::kWetDryMix, 0.0f);

    float buffer[] = {0.5f, -0.3f, 0.8f, -0.1f};
    float expected[] = {0.5f, -0.3f, 0.8f, -0.1f};
    chorus.process(buffer, 4, 1);

    for (int i = 0; i < 4; ++i) {
        assert(approxEqual(buffer[i], expected[i], 0.001f));
    }
    printf("PASS\n");
}

// ============================================================================
// Flanger tests
// ============================================================================

static void test_flanger_produces_output() {
    printf("  Flanger: sine input produces non-silent output... ");
    klarinet::Flanger flanger;
    flanger.prepare(48000, 1);
    flanger.setParameter(klarinet::FlangerParams::kRateHz, 0.5f);
    flanger.setParameter(klarinet::FlangerParams::kDepth, 0.7f);
    flanger.setParameter(klarinet::FlangerParams::kFeedback, 0.5f);
    flanger.setParameter(klarinet::FlangerParams::kWetDryMix, 0.5f);

    int32_t numFrames = 4800;
    std::vector<float> buffer(numFrames);
    for (int32_t i = 0; i < numFrames; ++i) {
        buffer[i] = std::sin(2.0f * 3.14159265f * 440.0f * i / 48000.0f);
    }

    flanger.process(buffer.data(), numFrames, 1);

    float energy = 0.0f;
    for (int32_t i = 0; i < numFrames; ++i) {
        energy += buffer[i] * buffer[i];
    }
    assert(energy > 0.1f);
    printf("PASS (energy=%.4f)\n", energy);
}

static void test_flanger_dry_only() {
    printf("  Flanger: mix=0 passes dry signal through... ");
    klarinet::Flanger flanger;
    flanger.prepare(48000, 1);
    flanger.setParameter(klarinet::FlangerParams::kWetDryMix, 0.0f);
    flanger.setParameter(klarinet::FlangerParams::kFeedback, 0.0f);

    float buffer[] = {0.5f, -0.3f, 0.8f, -0.1f};
    float expected[] = {0.5f, -0.3f, 0.8f, -0.1f};
    flanger.process(buffer, 4, 1);

    for (int i = 0; i < 4; ++i) {
        assert(approxEqual(buffer[i], expected[i], 0.001f));
    }
    printf("PASS\n");
}

// ============================================================================
// Phaser tests
// ============================================================================

static void test_phaser_produces_output() {
    printf("  Phaser: sine input produces non-silent output... ");
    klarinet::Phaser phaser;
    phaser.prepare(48000, 1);
    phaser.setParameter(klarinet::PhaserParams::kRateHz, 0.5f);
    phaser.setParameter(klarinet::PhaserParams::kDepth, 0.5f);
    phaser.setParameter(klarinet::PhaserParams::kStages, 4.0f);
    phaser.setParameter(klarinet::PhaserParams::kFeedback, 0.3f);

    int32_t numFrames = 4800;
    std::vector<float> buffer(numFrames);
    for (int32_t i = 0; i < numFrames; ++i) {
        buffer[i] = std::sin(2.0f * 3.14159265f * 440.0f * i / 48000.0f);
    }

    phaser.process(buffer.data(), numFrames, 1);

    float energy = 0.0f;
    for (int32_t i = 0; i < numFrames; ++i) {
        energy += buffer[i] * buffer[i];
    }
    assert(energy > 0.1f);
    printf("PASS (energy=%.4f)\n", energy);
}

static void test_phaser_stereo() {
    printf("  Phaser: stereo processing does not crash... ");
    klarinet::Phaser phaser;
    phaser.prepare(48000, 2);
    phaser.setParameter(klarinet::PhaserParams::kStages, 6.0f);

    int32_t numFrames = 480;
    std::vector<float> buffer(numFrames * 2);
    for (int32_t i = 0; i < numFrames; ++i) {
        float sample = std::sin(2.0f * 3.14159265f * 440.0f * i / 48000.0f);
        buffer[i * 2]     = sample;
        buffer[i * 2 + 1] = sample;
    }

    phaser.process(buffer.data(), numFrames, 2);

    // Just confirm no crash and output is not all zeros
    float energy = 0.0f;
    for (size_t i = 0; i < buffer.size(); ++i) {
        energy += buffer[i] * buffer[i];
    }
    assert(energy > 0.01f);
    printf("PASS\n");
}

// ============================================================================
// Parameter get/set round-trip tests
// ============================================================================

static void test_parameter_roundtrip() {
    printf("  Parameters: get/set round-trip for all effects... ");

    klarinet::Tremolo trem;
    trem.setParameter(klarinet::TremoloParams::kRateHz, 3.0f);
    trem.setParameter(klarinet::TremoloParams::kDepth, 0.8f);
    assert(approxEqual(trem.getParameter(klarinet::TremoloParams::kRateHz), 3.0f));
    assert(approxEqual(trem.getParameter(klarinet::TremoloParams::kDepth), 0.8f));

    klarinet::Chorus chorus;
    chorus.setParameter(klarinet::ChorusParams::kRateHz, 2.0f);
    chorus.setParameter(klarinet::ChorusParams::kDepth, 0.3f);
    chorus.setParameter(klarinet::ChorusParams::kWetDryMix, 0.7f);
    assert(approxEqual(chorus.getParameter(klarinet::ChorusParams::kRateHz), 2.0f));
    assert(approxEqual(chorus.getParameter(klarinet::ChorusParams::kDepth), 0.3f));
    assert(approxEqual(chorus.getParameter(klarinet::ChorusParams::kWetDryMix), 0.7f));

    klarinet::Flanger flanger;
    flanger.setParameter(klarinet::FlangerParams::kRateHz, 1.5f);
    flanger.setParameter(klarinet::FlangerParams::kDepth, 0.4f);
    flanger.setParameter(klarinet::FlangerParams::kFeedback, 0.6f);
    flanger.setParameter(klarinet::FlangerParams::kWetDryMix, 0.2f);
    assert(approxEqual(flanger.getParameter(klarinet::FlangerParams::kRateHz), 1.5f));
    assert(approxEqual(flanger.getParameter(klarinet::FlangerParams::kDepth), 0.4f));
    assert(approxEqual(flanger.getParameter(klarinet::FlangerParams::kFeedback), 0.6f));
    assert(approxEqual(flanger.getParameter(klarinet::FlangerParams::kWetDryMix), 0.2f));

    klarinet::Phaser phaser;
    phaser.setParameter(klarinet::PhaserParams::kRateHz, 0.8f);
    phaser.setParameter(klarinet::PhaserParams::kDepth, 0.9f);
    phaser.setParameter(klarinet::PhaserParams::kStages, 6.0f);
    phaser.setParameter(klarinet::PhaserParams::kFeedback, 0.5f);
    assert(approxEqual(phaser.getParameter(klarinet::PhaserParams::kRateHz), 0.8f));
    assert(approxEqual(phaser.getParameter(klarinet::PhaserParams::kDepth), 0.9f));
    assert(approxEqual(phaser.getParameter(klarinet::PhaserParams::kStages), 6.0f));
    assert(approxEqual(phaser.getParameter(klarinet::PhaserParams::kFeedback), 0.5f));

    printf("PASS\n");
}

// ============================================================================
// Main
// ============================================================================

int main() {
    printf("=== Tremolo ===\n");
    test_tremolo_zero_depth();
    test_tremolo_full_depth_range();
    test_tremolo_stereo();

    printf("\n=== Chorus ===\n");
    test_chorus_produces_output();
    test_chorus_dry_only();

    printf("\n=== Flanger ===\n");
    test_flanger_produces_output();
    test_flanger_dry_only();

    printf("\n=== Phaser ===\n");
    test_phaser_produces_output();
    test_phaser_stereo();

    printf("\n=== Parameters ===\n");
    test_parameter_roundtrip();

    printf("\nAll modulation tests passed!\n");
    return 0;
}
