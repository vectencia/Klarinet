#include <cassert>
#include <cmath>
#include <cstdio>
#include <vector>

#include "effects/LowPassFilter.h"
#include "effects/HighPassFilter.h"
#include "effects/BandPassFilter.h"
#include "effects/ParametricEQ.h"

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

static constexpr float kSampleRate = 48000.0f;
static constexpr int32_t kChannelCount = 1;

// ---------------------------------------------------------------------------
// Helper: generate a sine tone at `freq`, process through the effect,
// skip the first `skipSamples` for settling, then return peak amplitude.
// ---------------------------------------------------------------------------
static float measurePeakAtFrequency(klarinet::AudioEffect& effect, float freq,
                                    int32_t totalSamples = 4000,
                                    int32_t skipSamples = 1000) {
    std::vector<float> buffer(totalSamples);
    for (int32_t i = 0; i < totalSamples; ++i) {
        buffer[i] = std::sin(2.0f * static_cast<float>(M_PI) * freq *
                             static_cast<float>(i) / kSampleRate);
    }

    effect.process(buffer.data(), totalSamples, kChannelCount);

    float peak = 0.0f;
    for (int32_t i = skipSamples; i < totalSamples; ++i) {
        peak = std::max(peak, std::fabs(buffer[i]));
    }
    return peak;
}

// ============================================================================
// LowPassFilter tests
// ============================================================================

static void test_lpf_passes_low_frequency() {
    printf("  LPF: passes 100 Hz with cutoff at 5 kHz... ");
    klarinet::LowPassFilter lpf;
    lpf.prepare(static_cast<int32_t>(kSampleRate), kChannelCount);
    lpf.setParameter(klarinet::LPFParams::kCutoffHz, 5000.0f);
    lpf.setParameter(klarinet::LPFParams::kResonance, 0.707f);

    float peak = measurePeakAtFrequency(lpf, 100.0f);
    // 100 Hz is well below the 5 kHz cutoff; should pass nearly unattenuated
    assert(peak > 0.9f);
    printf("PASS\n");
}

static void test_lpf_attenuates_high_frequency() {
    printf("  LPF: attenuates 10 kHz with cutoff at 1 kHz... ");
    klarinet::LowPassFilter lpf;
    lpf.prepare(static_cast<int32_t>(kSampleRate), kChannelCount);
    lpf.setParameter(klarinet::LPFParams::kCutoffHz, 1000.0f);
    lpf.setParameter(klarinet::LPFParams::kResonance, 0.707f);

    float peak = measurePeakAtFrequency(lpf, 10000.0f);
    // 10 kHz is well above the 1 kHz cutoff; should be heavily attenuated
    assert(peak < 0.1f);
    printf("PASS\n");
}

// ============================================================================
// HighPassFilter tests
// ============================================================================

static void test_hpf_attenuates_low_frequency() {
    printf("  HPF: attenuates 100 Hz with cutoff at 5 kHz... ");
    klarinet::HighPassFilter hpf;
    hpf.prepare(static_cast<int32_t>(kSampleRate), kChannelCount);
    hpf.setParameter(klarinet::HPFParams::kCutoffHz, 5000.0f);
    hpf.setParameter(klarinet::HPFParams::kResonance, 0.707f);

    float peak = measurePeakAtFrequency(hpf, 100.0f);
    // 100 Hz is well below the 5 kHz cutoff; should be heavily attenuated
    assert(peak < 0.1f);
    printf("PASS\n");
}

// ============================================================================
// ParametricEQ tests
// ============================================================================

static void test_parametric_eq_boost() {
    printf("  ParametricEQ: +12 dB boost at 1 kHz amplifies ~4x... ");
    klarinet::ParametricEQ eq;
    eq.prepare(static_cast<int32_t>(kSampleRate), kChannelCount);

    // Configure band 0: Peak at 1 kHz, +12 dB, Q = 0.707
    int32_t band = 0;
    eq.setParameter(band * klarinet::EQParams::kParamsPerBand + klarinet::EQParams::kBandFrequency,
                    1000.0f);
    eq.setParameter(band * klarinet::EQParams::kParamsPerBand + klarinet::EQParams::kBandGain,
                    12.0f);
    eq.setParameter(band * klarinet::EQParams::kParamsPerBand + klarinet::EQParams::kBandQ,
                    0.707f);
    eq.setParameter(band * klarinet::EQParams::kParamsPerBand + klarinet::EQParams::kBandType,
                    static_cast<float>(klarinet::BiquadType::Peak));

    float peak = measurePeakAtFrequency(eq, 1000.0f);
    // +12 dB ~ 10^(12/20) ~ 3.98x
    assert(peak > 3.0f);
    printf("PASS\n");
}

// ============================================================================
// Main
// ============================================================================

int main() {
    printf("=== LowPassFilter ===\n");
    test_lpf_passes_low_frequency();
    test_lpf_attenuates_high_frequency();

    printf("\n=== HighPassFilter ===\n");
    test_hpf_attenuates_low_frequency();

    printf("\n=== ParametricEQ ===\n");
    test_parametric_eq_boost();

    printf("\nAll filter tests passed!\n");
    return 0;
}
