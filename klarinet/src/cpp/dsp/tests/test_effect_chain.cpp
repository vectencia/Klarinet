#include <cassert>
#include <cstdio>
#include <cmath>
#include <memory>
#include <thread>
#include <atomic>

#include "AudioEffect.h"
#include "EffectChain.h"

static bool approxEqual(float a, float b, float tolerance = 1e-4f) {
    return std::fabs(a - b) < tolerance;
}

// ============================================================================
// TestMultiplyEffect — multiplies all samples by a fixed value
// ============================================================================

class TestMultiplyEffect : public klarinet::AudioEffect {
public:
    explicit TestMultiplyEffect(float multiplier)
        : AudioEffect(klarinet::EffectType::Gain), multiplier_(multiplier) {}

    void process(float* buffer, int32_t numFrames, int32_t channelCount) override {
        int32_t totalSamples = numFrames * channelCount;
        for (int32_t i = 0; i < totalSamples; i++) {
            buffer[i] *= multiplier_;
        }
    }

    void setParameter(int32_t paramId, float value) override {
        if (paramId == 0) multiplier_ = value;
    }

    float getParameter(int32_t paramId) const override {
        if (paramId == 0) return multiplier_;
        return 0.0f;
    }

    void prepare(int32_t sampleRate, int32_t channelCount) override {
        sampleRate_ = sampleRate;
        channelCount_ = channelCount;
    }

    void reset() override {}

private:
    float multiplier_;
};

// ============================================================================
// Test: empty chain is passthrough
// ============================================================================

static void test_empty_chain_passthrough() {
    printf("  EffectChain: empty chain passthrough... ");
    klarinet::EffectChain chain;

    constexpr int NUM_FRAMES = 4;
    constexpr int CHANNELS = 1;
    float buffer[NUM_FRAMES] = {1.0f, 2.0f, 3.0f, 4.0f};

    chain.process(buffer, NUM_FRAMES, CHANNELS);

    assert(approxEqual(buffer[0], 1.0f));
    assert(approxEqual(buffer[1], 2.0f));
    assert(approxEqual(buffer[2], 3.0f));
    assert(approxEqual(buffer[3], 4.0f));

    printf("PASS\n");
}

// ============================================================================
// Test: single effect processing
// ============================================================================

static void test_single_effect() {
    printf("  EffectChain: single effect processing... ");
    klarinet::EffectChain chain;

    auto effect = std::make_shared<TestMultiplyEffect>(2.0f);
    chain.addEffect(effect);

    constexpr int NUM_FRAMES = 4;
    constexpr int CHANNELS = 1;
    float buffer[NUM_FRAMES] = {1.0f, 2.0f, 3.0f, 4.0f};

    chain.process(buffer, NUM_FRAMES, CHANNELS);

    assert(approxEqual(buffer[0], 2.0f));
    assert(approxEqual(buffer[1], 4.0f));
    assert(approxEqual(buffer[2], 6.0f));
    assert(approxEqual(buffer[3], 8.0f));

    printf("PASS\n");
}

// ============================================================================
// Test: chain ordering (2x then 3x = 6x)
// ============================================================================

static void test_chain_ordering() {
    printf("  EffectChain: chain ordering (2x then 3x = 6x)... ");
    klarinet::EffectChain chain;

    auto effect2x = std::make_shared<TestMultiplyEffect>(2.0f);
    auto effect3x = std::make_shared<TestMultiplyEffect>(3.0f);
    chain.addEffect(effect2x);
    chain.addEffect(effect3x);

    constexpr int NUM_FRAMES = 4;
    constexpr int CHANNELS = 1;
    float buffer[NUM_FRAMES] = {1.0f, 2.0f, 3.0f, 4.0f};

    chain.process(buffer, NUM_FRAMES, CHANNELS);

    assert(approxEqual(buffer[0], 6.0f));
    assert(approxEqual(buffer[1], 12.0f));
    assert(approxEqual(buffer[2], 18.0f));
    assert(approxEqual(buffer[3], 24.0f));

    printf("PASS\n");
}

// ============================================================================
// Test: bypassed effect (setEnabled(false))
// ============================================================================

static void test_bypassed_effect() {
    printf("  EffectChain: bypassed effect... ");
    klarinet::EffectChain chain;

    auto effect2x = std::make_shared<TestMultiplyEffect>(2.0f);
    auto effect3x = std::make_shared<TestMultiplyEffect>(3.0f);
    chain.addEffect(effect2x);
    chain.addEffect(effect3x);

    // Bypass the 2x effect
    effect2x->setEnabled(false);

    constexpr int NUM_FRAMES = 4;
    constexpr int CHANNELS = 1;
    float buffer[NUM_FRAMES] = {1.0f, 2.0f, 3.0f, 4.0f};

    chain.process(buffer, NUM_FRAMES, CHANNELS);

    // Only the 3x effect should apply
    assert(approxEqual(buffer[0], 3.0f));
    assert(approxEqual(buffer[1], 6.0f));
    assert(approxEqual(buffer[2], 9.0f));
    assert(approxEqual(buffer[3], 12.0f));

    printf("PASS\n");
}

// ============================================================================
// Test: remove effect
// ============================================================================

static void test_remove_effect() {
    printf("  EffectChain: remove effect... ");
    klarinet::EffectChain chain;

    auto effect2x = std::make_shared<TestMultiplyEffect>(2.0f);
    auto effect3x = std::make_shared<TestMultiplyEffect>(3.0f);
    chain.addEffect(effect2x);
    chain.addEffect(effect3x);

    assert(chain.getEffectCount() == 2);

    // Remove the 2x effect
    chain.removeEffect(effect2x.get());

    assert(chain.getEffectCount() == 1);

    constexpr int NUM_FRAMES = 4;
    constexpr int CHANNELS = 1;
    float buffer[NUM_FRAMES] = {1.0f, 2.0f, 3.0f, 4.0f};

    // Run process once to clean up pendingDelete_, then test with fresh buffer
    chain.process(buffer, NUM_FRAMES, CHANNELS);

    // Only the 3x effect should apply
    assert(approxEqual(buffer[0], 3.0f));
    assert(approxEqual(buffer[1], 6.0f));
    assert(approxEqual(buffer[2], 9.0f));
    assert(approxEqual(buffer[3], 12.0f));

    printf("PASS\n");
}

// ============================================================================
// Test: clear chain
// ============================================================================

static void test_clear_chain() {
    printf("  EffectChain: clear chain... ");
    klarinet::EffectChain chain;

    chain.addEffect(std::make_shared<TestMultiplyEffect>(2.0f));
    chain.addEffect(std::make_shared<TestMultiplyEffect>(3.0f));
    assert(chain.getEffectCount() == 2);

    chain.clear();
    assert(chain.getEffectCount() == 0);

    constexpr int NUM_FRAMES = 4;
    constexpr int CHANNELS = 1;
    float buffer[NUM_FRAMES] = {1.0f, 2.0f, 3.0f, 4.0f};
    chain.process(buffer, NUM_FRAMES, CHANNELS);

    // Passthrough
    assert(approxEqual(buffer[0], 1.0f));
    assert(approxEqual(buffer[1], 2.0f));

    printf("PASS\n");
}

// ============================================================================
// Test: parameter change via queue
// ============================================================================

static void test_parameter_change() {
    printf("  EffectChain: parameter change via queue... ");
    klarinet::EffectChain chain;

    auto effect = std::make_shared<TestMultiplyEffect>(2.0f);
    chain.addEffect(effect);

    // Enqueue parameter change to set multiplier to 5.0
    chain.enqueueParameterChange(effect.get(), 0, 5.0f);

    constexpr int NUM_FRAMES = 4;
    constexpr int CHANNELS = 1;
    float buffer[NUM_FRAMES] = {1.0f, 2.0f, 3.0f, 4.0f};

    // Process will apply pending parameter changes first
    chain.process(buffer, NUM_FRAMES, CHANNELS);

    assert(approxEqual(buffer[0], 5.0f));
    assert(approxEqual(buffer[1], 10.0f));
    assert(approxEqual(buffer[2], 15.0f));
    assert(approxEqual(buffer[3], 20.0f));

    printf("PASS\n");
}

// ============================================================================
// Test: reorder effects
// ============================================================================

static void test_reorder_effects() {
    printf("  EffectChain: reorder effects... ");
    klarinet::EffectChain chain;

    auto effect2x = std::make_shared<TestMultiplyEffect>(2.0f);
    auto effect5x = std::make_shared<TestMultiplyEffect>(5.0f);
    chain.addEffect(effect2x);
    chain.addEffect(effect5x);

    // Reorder: 5x first, then 2x
    chain.reorderEffects({effect5x.get(), effect2x.get()});

    // Run a process cycle to clean up pendingDelete_
    constexpr int NUM_FRAMES = 2;
    constexpr int CHANNELS = 1;
    float buffer[NUM_FRAMES] = {1.0f, 3.0f};
    chain.process(buffer, NUM_FRAMES, CHANNELS);

    // 1.0 * 5 * 2 = 10, 3.0 * 5 * 2 = 30
    assert(approxEqual(buffer[0], 10.0f));
    assert(approxEqual(buffer[1], 30.0f));

    printf("PASS\n");
}

// ============================================================================
// Test: multichannel processing
// ============================================================================

static void test_multichannel() {
    printf("  EffectChain: multichannel processing... ");
    klarinet::EffectChain chain;

    auto effect = std::make_shared<TestMultiplyEffect>(3.0f);
    chain.addEffect(effect);

    constexpr int NUM_FRAMES = 2;
    constexpr int CHANNELS = 2;
    // Interleaved: [L0, R0, L1, R1]
    float buffer[NUM_FRAMES * CHANNELS] = {1.0f, 2.0f, 3.0f, 4.0f};

    chain.process(buffer, NUM_FRAMES, CHANNELS);

    assert(approxEqual(buffer[0], 3.0f));
    assert(approxEqual(buffer[1], 6.0f));
    assert(approxEqual(buffer[2], 9.0f));
    assert(approxEqual(buffer[3], 12.0f));

    printf("PASS\n");
}

// ============================================================================
// Test: hot-swap safety (add/remove from one thread while processing on another)
// ============================================================================

static void test_hot_swap_safety() {
    printf("  EffectChain: hot-swap safety (100 cycles)... ");
    klarinet::EffectChain chain;
    std::atomic<bool> stop{false};

    // Audio thread: continuously processes buffers
    std::thread audioThread([&]() {
        constexpr int NUM_FRAMES = 64;
        constexpr int CHANNELS = 2;
        float buffer[NUM_FRAMES * CHANNELS];

        while (!stop.load(std::memory_order_acquire)) {
            // Fill with 1.0
            for (int i = 0; i < NUM_FRAMES * CHANNELS; i++) {
                buffer[i] = 1.0f;
            }
            chain.process(buffer, NUM_FRAMES, CHANNELS);

            // Verify no NaN or infinity
            for (int i = 0; i < NUM_FRAMES * CHANNELS; i++) {
                assert(!std::isnan(buffer[i]));
                assert(!std::isinf(buffer[i]));
            }
        }
    });

    // Control thread: add/remove effects 100 times
    for (int cycle = 0; cycle < 100; cycle++) {
        auto effect = std::make_shared<TestMultiplyEffect>(1.0f);
        chain.addEffect(effect);
        chain.removeEffect(effect.get());
    }

    stop.store(true, std::memory_order_release);
    audioThread.join();

    assert(chain.getEffectCount() == 0);

    printf("PASS\n");
}

// ============================================================================
// Main
// ============================================================================

int main() {
    printf("=== EffectChain ===\n");
    test_empty_chain_passthrough();
    test_single_effect();
    test_chain_ordering();
    test_bypassed_effect();
    test_remove_effect();
    test_clear_chain();
    test_parameter_change();
    test_reorder_effects();
    test_multichannel();
    test_hot_swap_safety();

    printf("\nAll EffectChain tests passed!\n");
    return 0;
}
