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

static void test_gain_reset_snaps_on_next_process() {
    printf("  Gain: reset snaps to unity on next process()... ");
    klarinet::Gain gain;
    gain.prepare(48000, 1);
    gain.setParameter(klarinet::GainParams::kGainDb, -20.0f);
    float primed[] = {1.0f};
    gain.process(primed, 1, 1);
    assert(approxEqual(primed[0], 0.1f, 0.001f));

    gain.reset();
    float buffer[] = {1.0f};
    gain.process(buffer, 1, 1);
    assert(approxEqual(buffer[0], 1.0f, 0.0001f));
    assert(approxEqual(gain.getParameter(klarinet::GainParams::kGainDb), 0.0f));
    printf("PASS\n");
}

static void test_gain_unknown_param_is_ignored() {
    printf("  Gain: unknown param id does not change gain... ");
    klarinet::Gain gain;
    gain.prepare(48000, 1);
    gain.setParameter(99, 12.0f);
    assert(approxEqual(gain.getParameter(klarinet::GainParams::kGainDb), 0.0f));
    assert(approxEqual(gain.getParameter(99), 0.0f));

    float buffer[] = {1.0f};
    gain.process(buffer, 1, 1);
    assert(approxEqual(buffer[0], 1.0f, 0.0001f));
    printf("PASS\n");
}

static void test_gain_fade_ms_alone_does_not_ramp() {
    printf("  Gain: FADE_MS alone does not start a ramp... ");
    klarinet::Gain gain;
    gain.prepare(48000, 1);
    gain.setParameter(klarinet::GainParams::kGainDb, -20.0f);
    float primed[] = {1.0f};
    gain.process(primed, 1, 1);
    assert(approxEqual(primed[0], 0.1f, 0.001f));

    gain.setParameter(klarinet::GainParams::kFadeMs, 2000.0f);
    assert(approxEqual(gain.getParameter(klarinet::GainParams::kGainDb), -20.0f));

    float buffer[] = {1.0f, 1.0f, 1.0f};
    gain.process(buffer, 3, 1);
    assert(approxEqual(buffer[0], 0.1f, 0.001f));
    assert(approxEqual(buffer[2], 0.1f, 0.001f));
    printf("PASS\n");
}

static void test_gain_fade_zero_ms_stays_instant() {
    printf("  Gain: fade 0ms still applies instantly... ");
    klarinet::Gain gain;
    gain.prepare(48000, 1);
    gain.setParameter(klarinet::GainParams::kFadeMs, 0.0f);
    gain.setParameter(klarinet::GainParams::kGainDb, -20.0f);

    float buffer[] = {1.0f};
    gain.process(buffer, 1, 1);
    assert(approxEqual(buffer[0], 0.1f, 0.001f));
    printf("PASS\n");
}

static void test_gain_fade_to_silence_no_click() {
    printf("  Gain: fade to silence is continuous, ends quiet... ");
    klarinet::Gain gain;
    gain.prepare(48000, 1);
    gain.setParameter(klarinet::GainParams::kFadeMs, 10.0f);
    gain.setParameter(klarinet::GainParams::kGainDb, -80.0f);

    const int n = 480; // 10ms at 48kHz
    float buffer[n];
    for (int i = 0; i < n; ++i) {
        buffer[i] = 1.0f;
    }
    gain.process(buffer, n, 1);

    assert(buffer[0] > 0.9f);
    assert(buffer[n - 1] < 0.001f);
    for (int i = 1; i < n; ++i) {
        assert(buffer[i] <= buffer[i - 1] + 1e-6f);
        assert(buffer[i - 1] - buffer[i] < 0.01f);
    }
    printf("PASS\n");
}

static void test_gain_fade_from_silence_no_click() {
    printf("  Gain: fade from silence is continuous, ends full... ");
    klarinet::Gain gain;
    gain.prepare(48000, 1);
    gain.setParameter(klarinet::GainParams::kGainDb, -80.0f);
    float primed[] = {1.0f};
    gain.process(primed, 1, 1);

    gain.setParameter(klarinet::GainParams::kFadeMs, 10.0f);
    gain.setParameter(klarinet::GainParams::kGainDb, 0.0f);

    const int n = 480;
    float buffer[n];
    for (int i = 0; i < n; ++i) {
        buffer[i] = 1.0f;
    }
    gain.process(buffer, n, 1);

    assert(buffer[0] < 0.01f);
    assert(buffer[n - 1] > 0.99f);
    for (int i = 1; i < n; ++i) {
        assert(buffer[i] >= buffer[i - 1] - 1e-6f);
        assert(buffer[i] - buffer[i - 1] < 0.01f);
    }
    printf("PASS\n");
}

static void test_gain_fade_retarget_from_current() {
    printf("  Gain: retarget mid-fade starts from current amplitude... ");
    klarinet::Gain gain;
    gain.prepare(48000, 1);
    gain.setParameter(klarinet::GainParams::kFadeMs, 10.0f);
    gain.setParameter(klarinet::GainParams::kGainDb, -80.0f);

    const int half = 240;
    float first[half];
    for (int i = 0; i < half; ++i) {
        first[i] = 1.0f;
    }
    gain.process(first, half, 1);

    float mid = first[half - 1];
    assert(mid > 0.4f && mid < 0.6f);

    gain.setParameter(klarinet::GainParams::kGainDb, 0.0f);

    float second[half];
    for (int i = 0; i < half; ++i) {
        second[i] = 1.0f;
    }
    gain.process(second, half, 1);

    assert(std::fabs(second[0] - mid) < 0.02f);
    assert(second[half - 1] > second[0]);
    for (int i = 1; i < half; ++i) {
        assert(second[i] - second[i - 1] < 0.01f);
    }
    printf("PASS\n");
}

static void test_gain_fade_two_seconds_to_silence() {
    printf("  Gain: 2s fade to silence, continuous, no spike... ");
    klarinet::Gain gain;
    gain.prepare(48000, 1);
    gain.setParameter(klarinet::GainParams::kFadeMs, 2000.0f);
    gain.setParameter(klarinet::GainParams::kGainDb, -80.0f);

    const int total = 96000; // 2s at 48kHz
    const int block = 256;
    float first = 0.0f;
    float last = 1.0f;
    float prev = 1.0f;
    int processed = 0;
    while (processed < total) {
        int n = total - processed;
        if (n > block) n = block;
        float buffer[256];
        for (int i = 0; i < n; ++i) {
            buffer[i] = 1.0f;
        }
        gain.process(buffer, n, 1);
        if (processed == 0) {
            first = buffer[0];
        }
        for (int i = 0; i < n; ++i) {
            assert(buffer[i] <= prev + 1e-6f);
            assert(prev - buffer[i] < 0.001f);
            prev = buffer[i];
        }
        last = buffer[n - 1];
        processed += n;
    }
    assert(first > 0.999f);
    assert(last < 0.001f);
    printf("PASS\n");
}

static void test_gain_fade_two_seconds_from_silence() {
    printf("  Gain: 2s fade from silence to full, continuous... ");
    klarinet::Gain gain;
    gain.prepare(48000, 1);
    gain.setParameter(klarinet::GainParams::kGainDb, -80.0f);
    float primed[] = {1.0f};
    gain.process(primed, 1, 1);

    gain.setParameter(klarinet::GainParams::kFadeMs, 2000.0f);
    gain.setParameter(klarinet::GainParams::kGainDb, 0.0f);

    const int total = 96000;
    const int block = 256;
    float first = 1.0f;
    float last = 0.0f;
    float prev = 0.0f;
    int processed = 0;
    while (processed < total) {
        int n = total - processed;
        if (n > block) n = block;
        float buffer[256];
        for (int i = 0; i < n; ++i) {
            buffer[i] = 1.0f;
        }
        gain.process(buffer, n, 1);
        if (processed == 0) {
            first = buffer[0];
            prev = buffer[0];
        }
        for (int i = 0; i < n; ++i) {
            assert(buffer[i] >= prev - 1e-6f);
            assert(buffer[i] - prev < 0.001f);
            prev = buffer[i];
        }
        last = buffer[n - 1];
        processed += n;
    }
    assert(first < 0.01f);
    assert(last > 0.99f);
    printf("PASS\n");
}

static void test_gain_fade_across_callback_buffers() {
    printf("  Gain: fade completes across multiple process() calls... ");
    klarinet::Gain gain;
    gain.prepare(48000, 1);
    gain.setParameter(klarinet::GainParams::kFadeMs, 10.0f);
    gain.setParameter(klarinet::GainParams::kGainDb, -80.0f);

    const int total = 480;
    const int block = 64;
    float last = 1.0f;
    int processed = 0;
    while (processed < total) {
        int n = total - processed;
        if (n > block) n = block;
        float buffer[64];
        for (int i = 0; i < n; ++i) {
            buffer[i] = 1.0f;
        }
        gain.process(buffer, n, 1);
        assert(buffer[0] <= last + 1e-6f);
        last = buffer[n - 1];
        processed += n;
    }
    assert(last < 0.001f);
    printf("PASS\n");
}

static void test_gain_crossfade_sum_has_no_hole_or_spike() {
    printf("  Gain: complementary 10ms fades sum to ~unity... ");
    klarinet::Gain down;
    down.prepare(48000, 1);
    down.setParameter(klarinet::GainParams::kFadeMs, 10.0f);
    down.setParameter(klarinet::GainParams::kGainDb, -80.0f);

    klarinet::Gain up;
    up.prepare(48000, 1);
    up.setParameter(klarinet::GainParams::kGainDb, -80.0f);
    float primed[] = {1.0f};
    up.process(primed, 1, 1);
    up.setParameter(klarinet::GainParams::kFadeMs, 10.0f);
    up.setParameter(klarinet::GainParams::kGainDb, 0.0f);

    const int n = 480;
    float falling[n];
    float rising[n];
    for (int i = 0; i < n; ++i) {
        falling[i] = 1.0f;
        rising[i] = 1.0f;
    }
    down.process(falling, n, 1);
    up.process(rising, n, 1);

    float minSum = 10.0f;
    float maxSum = -10.0f;
    for (int i = 0; i < n; ++i) {
        float sum = falling[i] + rising[i];
        if (sum < minSum) minSum = sum;
        if (sum > maxSum) maxSum = sum;
    }
    assert(falling[0] > 0.9f);
    assert(rising[0] < 0.01f);
    assert(falling[n - 1] < 0.001f);
    assert(rising[n - 1] > 0.99f);
    assert(minSum > 0.95f);
    assert(maxSum < 1.05f);
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
    test_gain_reset_snaps_on_next_process();
    test_gain_unknown_param_is_ignored();
    test_gain_fade_ms_alone_does_not_ramp();
    test_gain_fade_zero_ms_stays_instant();
    test_gain_fade_to_silence_no_click();
    test_gain_fade_from_silence_no_click();
    test_gain_fade_retarget_from_current();
    test_gain_fade_across_callback_buffers();
    test_gain_fade_two_seconds_to_silence();
    test_gain_fade_two_seconds_from_silence();
    test_gain_crossfade_sum_has_no_hole_or_spike();

    printf("\nAll Gain tests passed!\n");
    return 0;
}
