/**
 * @file AudioFifo.h
 * @brief Lock-free SPSC FIFO of interleaved float samples.
 *
 * One thread produces, one thread consumes. Used on Android to move
 * Kotlin/JNI audio work off the Oboe callback thread.
 */
#pragma once

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <cstring>
#include <vector>

namespace klarinet {

class AudioFifo {
public:
    explicit AudioFifo(int32_t capacitySamples) {
        int32_t cap = 1;
        while (cap < capacitySamples) {
            cap <<= 1;
        }
        capacity_ = static_cast<uint32_t>(cap);
        mask_ = capacity_ - 1;
        buffer_.assign(capacity_, 0.0f);
    }

    int32_t write(const float* src, int32_t count) {
        if (src == nullptr || count <= 0) return 0;
        const uint32_t w = writePos_.load(std::memory_order_relaxed);
        const uint32_t r = readPos_.load(std::memory_order_acquire);
        const uint32_t free = capacity_ - (w - r);
        const uint32_t n = std::min(static_cast<uint32_t>(count), free);
        copyIn(src, w, n);
        writePos_.store(w + n, std::memory_order_release);
        return static_cast<int32_t>(n);
    }

    int32_t read(float* dst, int32_t count) {
        if (dst == nullptr || count <= 0) return 0;
        const uint32_t r = readPos_.load(std::memory_order_relaxed);
        const uint32_t w = writePos_.load(std::memory_order_acquire);
        const uint32_t filled = w - r;
        const uint32_t n = std::min(static_cast<uint32_t>(count), filled);
        copyOut(dst, r, n);
        readPos_.store(r + n, std::memory_order_release);
        return static_cast<int32_t>(n);
    }

    int32_t availableToRead() const {
        const uint32_t w = writePos_.load(std::memory_order_acquire);
        const uint32_t r = readPos_.load(std::memory_order_acquire);
        return static_cast<int32_t>(w - r);
    }

    int32_t availableToWrite() const {
        return static_cast<int32_t>(capacity_) - availableToRead();
    }

    int32_t capacity() const { return static_cast<int32_t>(capacity_); }

private:
    void copyIn(const float* src, uint32_t start, uint32_t n) {
        if (n == 0) return;
        const uint32_t index = start & mask_;
        uint32_t first = n;
        if (index + n > capacity_) {
            first = capacity_ - index;
        }
        std::memcpy(&buffer_[index], src, first * sizeof(float));
        if (first < n) {
            std::memcpy(&buffer_[0], src + first, (n - first) * sizeof(float));
        }
    }

    void copyOut(float* dst, uint32_t start, uint32_t n) {
        if (n == 0) return;
        const uint32_t index = start & mask_;
        uint32_t first = n;
        if (index + n > capacity_) {
            first = capacity_ - index;
        }
        std::memcpy(dst, &buffer_[index], first * sizeof(float));
        if (first < n) {
            std::memcpy(dst + first, &buffer_[0], (n - first) * sizeof(float));
        }
    }

    std::vector<float> buffer_{};
    uint32_t capacity_ = 0;
    uint32_t mask_ = 0;
    std::atomic<uint32_t> writePos_{0};
    std::atomic<uint32_t> readPos_{0};
};

} // namespace klarinet
