#pragma once
#include <atomic>
#include <array>
#include <cstdint>

namespace klarinet {

template <typename T, int32_t Capacity = 256>
class RingBuffer {
public:
    bool push(const T& item) {
        int32_t currentWrite = writePos_.load(std::memory_order_relaxed);
        int32_t nextWrite = (currentWrite + 1) % Capacity;
        if (nextWrite == readPos_.load(std::memory_order_acquire)) return false;
        buffer_[currentWrite] = item;
        writePos_.store(nextWrite, std::memory_order_release);
        return true;
    }

    bool pop(T& item) {
        int32_t currentRead = readPos_.load(std::memory_order_relaxed);
        if (currentRead == writePos_.load(std::memory_order_acquire)) return false;
        item = buffer_[currentRead];
        readPos_.store((currentRead + 1) % Capacity, std::memory_order_release);
        return true;
    }

    bool isEmpty() const {
        return readPos_.load(std::memory_order_acquire) == writePos_.load(std::memory_order_acquire);
    }

private:
    std::array<T, Capacity> buffer_{};
    std::atomic<int32_t> readPos_{0};
    std::atomic<int32_t> writePos_{0};
};

} // namespace klarinet
