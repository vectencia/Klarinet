#include <cassert>
#include <cmath>
#include <cstdio>
#include <vector>

#include "effects/Delay.h"

static bool approxEqual(float a, float b, float tolerance = 1e-4f) {
    return std::fabs(a - b) < tolerance;
}

// ============================================================================
// Delay tests
// ============================================================================

static void test_delay_echo_position() {
    printf("  Delay: impulse echo appears near expected sample... ");

    constexpr int32_t sampleRate = 48000;
    constexpr int32_t channelCount = 1;
    constexpr float delayMs = 100.0f;
    constexpr int32_t expectedDelaySamples = static_cast<int32_t>(
        delayMs * 0.001f * static_cast<float>(sampleRate)); // 4800

    klarinet::Delay delay;
    delay.prepare(sampleRate, channelCount);
    delay.setParameter(klarinet::DelayParams::kTimeMs, delayMs);
    delay.setParameter(klarinet::DelayParams::kFeedback, 0.0f);
    delay.setParameter(klarinet::DelayParams::kWetDryMix, 1.0f);

    // Create buffer: impulse at sample 0, then silence
    constexpr int32_t numFrames = 6000;
    std::vector<float> buffer(numFrames, 0.0f);
    buffer[0] = 1.0f;

    delay.process(buffer.data(), numFrames, channelCount);

    // The echo should appear near sample 4800
    // Check that there is energy around the expected delay position
    float maxVal = 0.0f;
    int32_t maxPos = 0;
    for (int32_t i = expectedDelaySamples - 10; i < expectedDelaySamples + 10 && i < numFrames; ++i) {
        if (std::fabs(buffer[i]) > maxVal) {
            maxVal = std::fabs(buffer[i]);
            maxPos = i;
        }
    }

    assert(maxVal > 0.5f);
    assert(maxPos >= expectedDelaySamples - 5 && maxPos <= expectedDelaySamples + 5);
    printf("PASS\n");
}

static void test_delay_dry_passthrough() {
    printf("  Delay: fully dry (mix=0) is passthrough... ");

    constexpr int32_t sampleRate = 48000;
    constexpr int32_t channelCount = 1;

    klarinet::Delay delay;
    delay.prepare(sampleRate, channelCount);
    delay.setParameter(klarinet::DelayParams::kTimeMs, 250.0f);
    delay.setParameter(klarinet::DelayParams::kFeedback, 0.5f);
    delay.setParameter(klarinet::DelayParams::kWetDryMix, 0.0f);

    constexpr int32_t numFrames = 256;
    std::vector<float> buffer(numFrames);
    std::vector<float> original(numFrames);
    for (int32_t i = 0; i < numFrames; ++i) {
        float val = std::sin(2.0f * 3.14159265f * 440.0f * static_cast<float>(i)
                             / static_cast<float>(sampleRate));
        buffer[i] = val;
        original[i] = val;
    }

    delay.process(buffer.data(), numFrames, channelCount);

    for (int32_t i = 0; i < numFrames; ++i) {
        assert(approxEqual(buffer[i], original[i], 1e-6f));
    }
    printf("PASS\n");
}

// ============================================================================
// Main
// ============================================================================

int main() {
    printf("=== Delay ===\n");
    test_delay_echo_position();
    test_delay_dry_passthrough();

    printf("\nAll delay tests passed!\n");
    return 0;
}
