#include <cassert>
#include <cmath>
#include <cstdio>

#include "effects/Pan.h"

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

static bool approxEqual(float a, float b, float tolerance = 1e-4f) {
    return std::fabs(a - b) < tolerance;
}

// ============================================================================
// Pan tests
// ============================================================================

static void test_pan_center_equal_power() {
    printf("  Pan: center (0.0) equal power to L and R... ");
    klarinet::Pan pan;
    pan.prepare(48000, 2);
    pan.setParameter(klarinet::PanParams::kPan, 0.0f);

    // At center: angle = (0+1)*pi/4 = pi/4
    // gainL = cos(pi/4) = sqrt(2)/2 ~ 0.7071
    // gainR = sin(pi/4) = sqrt(2)/2 ~ 0.7071
    float expected = static_cast<float>(std::cos(M_PI / 4.0));

    // Interleaved stereo: [L0, R0]
    float buffer[] = {1.0f, 1.0f};
    pan.process(buffer, 1, 2);

    assert(approxEqual(buffer[0], expected, 0.001f));
    assert(approxEqual(buffer[1], expected, 0.001f));
    // L and R should be equal
    assert(approxEqual(buffer[0], buffer[1], 0.0001f));
    printf("PASS\n");
}

static void test_pan_full_left() {
    printf("  Pan: full left (-1.0) L=1.0, R~0.0... ");
    klarinet::Pan pan;
    pan.prepare(48000, 2);
    pan.setParameter(klarinet::PanParams::kPan, -1.0f);

    // angle = (-1+1)*pi/4 = 0
    // gainL = cos(0) = 1.0
    // gainR = sin(0) = 0.0
    float buffer[] = {1.0f, 1.0f};
    pan.process(buffer, 1, 2);

    assert(approxEqual(buffer[0], 1.0f, 0.001f));
    assert(approxEqual(buffer[1], 0.0f, 0.001f));
    printf("PASS\n");
}

static void test_pan_full_right() {
    printf("  Pan: full right (1.0) L~0.0, R=1.0... ");
    klarinet::Pan pan;
    pan.prepare(48000, 2);
    pan.setParameter(klarinet::PanParams::kPan, 1.0f);

    // angle = (1+1)*pi/4 = pi/2
    // gainL = cos(pi/2) = 0.0
    // gainR = sin(pi/2) = 1.0
    float buffer[] = {1.0f, 1.0f};
    pan.process(buffer, 1, 2);

    assert(approxEqual(buffer[0], 0.0f, 0.001f));
    assert(approxEqual(buffer[1], 1.0f, 0.001f));
    printf("PASS\n");
}

static void test_pan_mono_noop() {
    printf("  Pan: mono is no-op... ");
    klarinet::Pan pan;
    pan.prepare(48000, 1);
    pan.setParameter(klarinet::PanParams::kPan, 1.0f); // Full right, but mono

    float buffer[] = {0.5f, -0.5f, 1.0f};
    float expected[] = {0.5f, -0.5f, 1.0f};
    pan.process(buffer, 3, 1);

    for (int i = 0; i < 3; ++i) {
        assert(approxEqual(buffer[i], expected[i]));
    }
    printf("PASS\n");
}

// ============================================================================
// Main
// ============================================================================

int main() {
    printf("=== Pan ===\n");
    test_pan_center_equal_power();
    test_pan_full_left();
    test_pan_full_right();
    test_pan_mono_noop();

    printf("\nAll Pan tests passed!\n");
    return 0;
}
