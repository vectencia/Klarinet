#include <atomic>
#include <cassert>
#include <chrono>
#include <cstdio>
#include <thread>

#include "klarinet_dsp.h"

namespace {

struct XrunCtx {
    std::atomic<int> count{0};
    std::thread::id audioThread{};
    std::atomic<bool> calledOnAudioThread{false};
    std::atomic<bool> inCallback{false};
    std::atomic<bool> releaseCallback{false};
};

void xrunCb(void* userData, int count) {
    auto* ctx = static_cast<XrunCtx*>(userData);
    if (std::this_thread::get_id() == ctx->audioThread) {
        ctx->calledOnAudioThread.store(true, std::memory_order_release);
    }
    ctx->count.store(count, std::memory_order_release);
}

int emptyPlayback(void* userData, float* buffer, int numFrames, int channelCount) {
    (void)userData;
    (void)buffer;
    (void)numFrames;
    (void)channelCount;
    return 0;
}

int blockingCapture(void* userData, float* buffer, int numFrames, int channelCount) {
    (void)buffer;
    (void)numFrames;
    (void)channelCount;
    auto* ctx = static_cast<XrunCtx*>(userData);
    ctx->inCallback.store(true, std::memory_order_release);
    while (!ctx->releaseCallback.load(std::memory_order_acquire)) {
        std::this_thread::sleep_for(std::chrono::milliseconds(1));
    }
    return numFrames;
}

bool waitFor(const std::atomic<int>& value, int min, int timeoutMs) {
    const auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(timeoutMs);
    while (value.load(std::memory_order_acquire) < min) {
        if (std::chrono::steady_clock::now() >= deadline) return false;
        std::this_thread::sleep_for(std::chrono::milliseconds(1));
    }
    return true;
}

bool waitFlag(const std::atomic<bool>& flag, int timeoutMs) {
    const auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(timeoutMs);
    while (!flag.load(std::memory_order_acquire)) {
        if (std::chrono::steady_clock::now() >= deadline) return false;
        std::this_thread::sleep_for(std::chrono::milliseconds(1));
    }
    return true;
}

} // namespace

static void test_playback_underrun_notifies_worker() {
    printf("  Offload: playback underrun notifies worker... ");
    XrunCtx ctx;
    ctx.audioThread = std::this_thread::get_id();
    KlarinetOffloadHandle handle = klarinet_offload_create(16, 1, 0, emptyPlayback, &ctx);
    assert(handle != nullptr);
    klarinet_offload_set_xrun_callback(handle, xrunCb);

    float burst[16];
    for (float& s : burst) s = 99.f;
    klarinet_offload_process(handle, burst, 16);
    for (float s : burst) {
        assert(s == 0.f);
    }

    assert(waitFor(ctx.count, 1, 500));
    assert(!ctx.calledOnAudioThread.load(std::memory_order_acquire));
    klarinet_offload_destroy(handle);
    printf("PASS\n");
}

static void test_capture_overrun_notifies_worker() {
    printf("  Offload: capture overrun notifies worker... ");
    XrunCtx ctx;
    ctx.audioThread = std::this_thread::get_id();
    KlarinetOffloadHandle handle = klarinet_offload_create(16, 1, 1, blockingCapture, &ctx);
    assert(handle != nullptr);
    klarinet_offload_set_xrun_callback(handle, xrunCb);

    float burst[16];
    for (float& s : burst) s = 0.1f;
    klarinet_offload_process(handle, burst, 16);
    assert(waitFlag(ctx.inCallback, 500));

    for (int i = 0; i < 16; ++i) {
        klarinet_offload_process(handle, burst, 16);
    }

    ctx.releaseCallback.store(true, std::memory_order_release);
    assert(waitFor(ctx.count, 1, 500));
    assert(!ctx.calledOnAudioThread.load(std::memory_order_acquire));
    klarinet_offload_destroy(handle);
    printf("PASS\n");
}

int main() {
    printf("=== AudioCallbackOffload xrun ===\n");
    test_playback_underrun_notifies_worker();
    test_capture_overrun_notifies_worker();
    printf("\nAll AudioCallbackOffload tests passed!\n");
    return 0;
}
