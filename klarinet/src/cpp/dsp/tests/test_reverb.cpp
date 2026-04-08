#include <cassert>
#include <cmath>
#include <cstdio>
#include <vector>

#include "effects/Reverb.h"

static bool approxEqual(float a, float b, float tolerance = 1e-4f) {
    return std::fabs(a - b) < tolerance;
}

// ============================================================================
// Reverb tests
// ============================================================================

static void test_reverb_tail_has_energy() {
    printf("  Reverb: impulse produces tail with energy at 500ms... ");

    constexpr int32_t sampleRate = 48000;
    constexpr int32_t channelCount = 2;
    constexpr int32_t numFrames = sampleRate; // 1 second

    klarinet::Reverb reverb;
    reverb.prepare(sampleRate, channelCount);
    reverb.setParameter(klarinet::ReverbParams::kRoomSize, 0.9f);
    reverb.setParameter(klarinet::ReverbParams::kDamping, 0.3f);
    reverb.setParameter(klarinet::ReverbParams::kWetDryMix, 1.0f);
    reverb.setParameter(klarinet::ReverbParams::kWidth, 1.0f);

    // Interleaved stereo buffer
    std::vector<float> buffer(numFrames * channelCount, 0.0f);
    // Impulse in both channels
    buffer[0] = 1.0f;
    buffer[1] = 1.0f;

    reverb.process(buffer.data(), numFrames, channelCount);

    // Check that there is still energy around the 500ms mark (sample 24000)
    constexpr int32_t checkStart = sampleRate / 2 - 500;
    constexpr int32_t checkEnd = sampleRate / 2 + 500;
    float energy = 0.0f;
    for (int32_t frame = checkStart; frame < checkEnd; ++frame) {
        float l = buffer[frame * channelCount + 0];
        float r = buffer[frame * channelCount + 1];
        energy += l * l + r * r;
    }

    // The reverb tail should have measurable energy
    assert(energy > 1e-6f);
    printf("PASS\n");
}

static void test_reverb_dry_passthrough() {
    printf("  Reverb: fully dry (mix=0) is passthrough... ");

    constexpr int32_t sampleRate = 48000;
    constexpr int32_t channelCount = 2;
    constexpr int32_t numFrames = 256;

    klarinet::Reverb reverb;
    reverb.prepare(sampleRate, channelCount);
    reverb.setParameter(klarinet::ReverbParams::kRoomSize, 0.7f);
    reverb.setParameter(klarinet::ReverbParams::kDamping, 0.5f);
    reverb.setParameter(klarinet::ReverbParams::kWetDryMix, 0.0f);
    reverb.setParameter(klarinet::ReverbParams::kWidth, 1.0f);

    std::vector<float> buffer(numFrames * channelCount);
    std::vector<float> original(numFrames * channelCount);
    for (int32_t i = 0; i < numFrames; ++i) {
        float val = std::sin(2.0f * 3.14159265f * 440.0f * static_cast<float>(i)
                             / static_cast<float>(sampleRate));
        buffer[i * channelCount + 0] = val;
        buffer[i * channelCount + 1] = val;
        original[i * channelCount + 0] = val;
        original[i * channelCount + 1] = val;
    }

    reverb.process(buffer.data(), numFrames, channelCount);

    for (int32_t i = 0; i < numFrames * channelCount; ++i) {
        assert(approxEqual(buffer[i], original[i], 1e-5f));
    }
    printf("PASS\n");
}

// ============================================================================
// Main
// ============================================================================

int main() {
    printf("=== Reverb ===\n");
    test_reverb_tail_has_energy();
    test_reverb_dry_passthrough();

    printf("\nAll reverb tests passed!\n");
    return 0;
}
