#include <cassert>
#include <cmath>
#include <cstdio>

#include "primitives/CircularBuffer.h"
#include "primitives/Biquad.h"
#include "primitives/LFO.h"
#include "primitives/EnvelopeFollower.h"

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

static bool approxEqual(float a, float b, float tolerance = 1e-4f) {
    return std::fabs(a - b) < tolerance;
}

// ============================================================================
// CircularBuffer tests
// ============================================================================

static void test_circular_buffer_write_read() {
    printf("  CircularBuffer: write/read... ");
    klarinet::CircularBuffer buf(8);
    buf.write(1.0f);
    buf.write(2.0f);
    buf.write(3.0f);

    // read(0) = most recent sample = 3.0
    assert(approxEqual(buf.read(0), 3.0f));
    // read(1) = one sample back = 2.0
    assert(approxEqual(buf.read(1), 2.0f));
    // read(2) = two samples back = 1.0
    assert(approxEqual(buf.read(2), 1.0f));
    printf("PASS\n");
}

static void test_circular_buffer_clear() {
    printf("  CircularBuffer: clear... ");
    klarinet::CircularBuffer buf(8);
    buf.write(1.0f);
    buf.write(2.0f);
    buf.clear();

    // After clear, all should read 0
    assert(approxEqual(buf.read(0), 0.0f));
    assert(approxEqual(buf.read(1), 0.0f));
    printf("PASS\n");
}

static void test_circular_buffer_wrap_around() {
    printf("  CircularBuffer: wrap around... ");
    klarinet::CircularBuffer buf(4);
    // Write more than buffer size
    for (int i = 0; i < 6; i++) {
        buf.write(static_cast<float>(i + 1));
    }
    // Buffer should contain [5, 6, 3, 4] with writePos_ at 2
    // Most recent = 6.0, one back = 5.0
    assert(approxEqual(buf.read(0), 6.0f));
    assert(approxEqual(buf.read(1), 5.0f));
    assert(approxEqual(buf.read(2), 4.0f));
    assert(approxEqual(buf.read(3), 3.0f));
    printf("PASS\n");
}

static void test_circular_buffer_linear_interpolation() {
    printf("  CircularBuffer: linear interpolation... ");
    klarinet::CircularBuffer buf(8);
    buf.write(0.0f);
    buf.write(10.0f);

    // Exact integer delay
    assert(approxEqual(buf.readLinear(0.0f), 10.0f));
    assert(approxEqual(buf.readLinear(1.0f), 0.0f));

    // Fractional delay: 0.5 should interpolate between 10.0 and 0.0
    float val = buf.readLinear(0.5f);
    assert(approxEqual(val, 5.0f));

    // 0.25 -> 75% of 10.0 + 25% of 0.0 = 7.5
    val = buf.readLinear(0.25f);
    assert(approxEqual(val, 7.5f));

    printf("PASS\n");
}

static void test_circular_buffer_set_size() {
    printf("  CircularBuffer: setSize... ");
    klarinet::CircularBuffer buf;
    assert(buf.getSize() == 0);
    buf.setSize(16);
    assert(buf.getSize() == 16);
    buf.write(42.0f);
    assert(approxEqual(buf.read(0), 42.0f));
    printf("PASS\n");
}

// ============================================================================
// Biquad tests
// ============================================================================

static void test_biquad_lpf_passes_dc() {
    printf("  Biquad: LPF passes DC... ");
    klarinet::Biquad bq;
    bq.configure(klarinet::BiquadType::LowPass, 1000.0f, 0.707f, 0.0f, 48000.0f);

    // Feed DC signal (1.0) through filter, it should converge to 1.0
    float output = 0.0f;
    for (int i = 0; i < 1000; i++) {
        output = bq.process(1.0f);
    }
    assert(approxEqual(output, 1.0f, 0.01f));
    printf("PASS\n");
}

static void test_biquad_lpf_attenuates_high_freq() {
    printf("  Biquad: LPF attenuates 20kHz... ");
    klarinet::Biquad bq;
    bq.configure(klarinet::BiquadType::LowPass, 1000.0f, 0.707f, 0.0f, 48000.0f);

    // Generate 20kHz sine and measure output amplitude
    float maxOutput = 0.0f;
    float sampleRate = 48000.0f;
    float freq = 20000.0f;
    for (int i = 0; i < 2000; i++) {
        float input = std::sin(2.0f * static_cast<float>(M_PI) * freq * static_cast<float>(i) / sampleRate);
        float output = bq.process(input);
        if (i > 500) { // Let transient settle
            maxOutput = std::max(maxOutput, std::fabs(output));
        }
    }
    // Should be heavily attenuated (< 0.1 of input amplitude)
    assert(maxOutput < 0.1f);
    printf("PASS\n");
}

static void test_biquad_hpf_blocks_dc() {
    printf("  Biquad: HPF blocks DC... ");
    klarinet::Biquad bq;
    bq.configure(klarinet::BiquadType::HighPass, 1000.0f, 0.707f, 0.0f, 48000.0f);

    float output = 0.0f;
    for (int i = 0; i < 1000; i++) {
        output = bq.process(1.0f);
    }
    assert(approxEqual(output, 0.0f, 0.01f));
    printf("PASS\n");
}

static void test_biquad_reset() {
    printf("  Biquad: reset clears state... ");
    klarinet::Biquad bq;
    bq.configure(klarinet::BiquadType::LowPass, 1000.0f, 0.707f, 0.0f, 48000.0f);

    // Process some signal
    for (int i = 0; i < 100; i++) {
        bq.process(1.0f);
    }
    bq.reset();
    // After reset, processing 0 should immediately give 0
    float output = bq.process(0.0f);
    assert(approxEqual(output, 0.0f));
    printf("PASS\n");
}

// ============================================================================
// LFO tests
// ============================================================================

static void test_lfo_starts_at_zero() {
    printf("  LFO: starts at 0... ");
    klarinet::LFO lfo;
    lfo.prepare(48000.0f);
    lfo.setFrequency(1.0f);

    float first = lfo.nextSample();
    // sin(0) = 0
    assert(approxEqual(first, 0.0f, 0.001f));
    printf("PASS\n");
}

static void test_lfo_quarter_cycle() {
    printf("  LFO: reaches ~1.0 at quarter cycle... ");
    klarinet::LFO lfo;
    float sampleRate = 48000.0f;
    float freq = 1.0f; // 1 Hz
    lfo.prepare(sampleRate);
    lfo.setFrequency(freq);

    // Quarter cycle = sampleRate / (4 * freq) = 12000 samples
    int quarterCycle = static_cast<int>(sampleRate / (4.0f * freq));
    float sample = 0.0f;
    for (int i = 0; i < quarterCycle; i++) {
        sample = lfo.nextSample();
    }
    // Should be close to sin(pi/2) = 1.0
    assert(approxEqual(sample, 1.0f, 0.01f));
    printf("PASS\n");
}

static void test_lfo_reset() {
    printf("  LFO: reset returns to 0... ");
    klarinet::LFO lfo;
    lfo.prepare(48000.0f);
    lfo.setFrequency(100.0f);

    // Advance some samples
    for (int i = 0; i < 500; i++) {
        lfo.nextSample();
    }
    lfo.reset();
    float first = lfo.nextSample();
    assert(approxEqual(first, 0.0f, 0.001f));
    printf("PASS\n");
}

static void test_lfo_range() {
    printf("  LFO: output stays in [-1, 1]... ");
    klarinet::LFO lfo;
    lfo.prepare(48000.0f);
    lfo.setFrequency(440.0f);

    for (int i = 0; i < 48000; i++) {
        float s = lfo.nextSample();
        assert(s >= -1.0f && s <= 1.0f);
    }
    printf("PASS\n");
}

// ============================================================================
// EnvelopeFollower tests
// ============================================================================

static void test_envelope_silence() {
    printf("  EnvelopeFollower: stays at 0 for silence... ");
    klarinet::EnvelopeFollower env;
    env.prepare(48000.0f);
    env.setAttackMs(10.0f);
    env.setReleaseMs(100.0f);

    float output = 0.0f;
    for (int i = 0; i < 1000; i++) {
        output = env.process(0.0f);
    }
    assert(approxEqual(output, 0.0f, 0.0001f));
    printf("PASS\n");
}

static void test_envelope_rises_for_loud_signal() {
    printf("  EnvelopeFollower: rises for loud signal... ");
    klarinet::EnvelopeFollower env;
    env.prepare(48000.0f);
    env.setAttackMs(1.0f);   // Fast attack
    env.setReleaseMs(100.0f);

    float output = 0.0f;
    // Feed constant 1.0 signal
    for (int i = 0; i < 1000; i++) {
        output = env.process(1.0f);
    }
    // Envelope should have risen close to 1.0
    assert(output > 0.9f);
    printf("PASS\n");
}

static void test_envelope_decays_for_silence() {
    printf("  EnvelopeFollower: decays for silence after signal... ");
    klarinet::EnvelopeFollower env;
    env.prepare(48000.0f);
    env.setAttackMs(1.0f);
    env.setReleaseMs(10.0f); // Fast release

    // First, raise the envelope
    for (int i = 0; i < 1000; i++) {
        env.process(1.0f);
    }
    float peakEnv = env.process(1.0f);
    assert(peakEnv > 0.9f);

    // Now feed silence
    float output = 0.0f;
    for (int i = 0; i < 5000; i++) {
        output = env.process(0.0f);
    }
    // Envelope should have decayed significantly
    assert(output < 0.1f);
    printf("PASS\n");
}

static void test_envelope_reset() {
    printf("  EnvelopeFollower: reset clears envelope... ");
    klarinet::EnvelopeFollower env;
    env.prepare(48000.0f);
    env.setAttackMs(1.0f);
    env.setReleaseMs(100.0f);

    for (int i = 0; i < 1000; i++) {
        env.process(1.0f);
    }
    env.reset();
    float output = env.process(0.0f);
    assert(approxEqual(output, 0.0f, 0.0001f));
    printf("PASS\n");
}

// ============================================================================
// Main
// ============================================================================

int main() {
    printf("=== CircularBuffer ===\n");
    test_circular_buffer_write_read();
    test_circular_buffer_clear();
    test_circular_buffer_wrap_around();
    test_circular_buffer_linear_interpolation();
    test_circular_buffer_set_size();

    printf("\n=== Biquad ===\n");
    test_biquad_lpf_passes_dc();
    test_biquad_lpf_attenuates_high_freq();
    test_biquad_hpf_blocks_dc();
    test_biquad_reset();

    printf("\n=== LFO ===\n");
    test_lfo_starts_at_zero();
    test_lfo_quarter_cycle();
    test_lfo_reset();
    test_lfo_range();

    printf("\n=== EnvelopeFollower ===\n");
    test_envelope_silence();
    test_envelope_rises_for_loud_signal();
    test_envelope_decays_for_silence();
    test_envelope_reset();

    printf("\nAll primitive tests passed!\n");
    return 0;
}
