#include <cassert>
#include <cstdio>
#include <numeric>
#include <thread>
#include <vector>

#include "AudioFifo.h"

static void test_write_read() {
    printf("  AudioFifo: write/read... ");
    klarinet::AudioFifo fifo(16);
    const float src[] = {1.f, 2.f, 3.f, 4.f};
    assert(fifo.write(src, 4) == 4);
    float dst[4] = {};
    assert(fifo.read(dst, 4) == 4);
    assert(dst[0] == 1.f && dst[3] == 4.f);
    assert(fifo.availableToRead() == 0);
    printf("PASS\n");
}

static void test_wrap() {
    printf("  AudioFifo: wrap-around... ");
    klarinet::AudioFifo fifo(8);
    float first[6] = {1, 2, 3, 4, 5, 6};
    assert(fifo.write(first, 6) == 6);
    float skip[6] = {};
    assert(fifo.read(skip, 6) == 6);
    float second[6] = {7, 8, 9, 10, 11, 12};
    assert(fifo.write(second, 6) == 6);
    float dst[6] = {};
    assert(fifo.read(dst, 6) == 6);
    assert(dst[0] == 7.f && dst[5] == 12.f);
    printf("PASS\n");
}

static void test_full_rejects() {
    printf("  AudioFifo: full write is partial... ");
    klarinet::AudioFifo fifo(8);
    float src[8] = {1, 2, 3, 4, 5, 6, 7, 8};
    assert(fifo.write(src, 8) == 8);
    assert(fifo.write(src, 8) == 0);
    printf("PASS\n");
}

static void test_spsc() {
    printf("  AudioFifo: SPSC transfer... ");
    constexpr int kCount = 10'000;
    klarinet::AudioFifo fifo(1024);
    std::thread producer([&] {
        std::vector<float> chunk(64);
        int sent = 0;
        while (sent < kCount) {
            const int n = std::min(64, kCount - sent);
            std::iota(chunk.begin(), chunk.begin() + n, static_cast<float>(sent));
            const int wrote = fifo.write(chunk.data(), n);
            sent += wrote;
        }
    });
    std::vector<float> received;
    received.reserve(kCount);
    float chunk[64];
    while (static_cast<int>(received.size()) < kCount) {
        const int got = fifo.read(chunk, 64);
        received.insert(received.end(), chunk, chunk + got);
    }
    producer.join();
    assert(static_cast<int>(received.size()) == kCount);
    for (int i = 0; i < kCount; ++i) {
        assert(received[static_cast<size_t>(i)] == static_cast<float>(i));
    }
    printf("PASS\n");
}

int main() {
    printf("=== AudioFifo ===\n");
    test_write_read();
    test_wrap();
    test_full_rejects();
    test_spsc();
    printf("\nAll AudioFifo tests passed!\n");
    return 0;
}
