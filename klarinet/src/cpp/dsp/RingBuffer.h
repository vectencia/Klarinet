/**
 * @file RingBuffer.h
 * @brief Lock-free single-producer / single-consumer (SPSC) ring buffer.
 *
 * Designed for passing lightweight messages (e.g., parameter changes) from a
 * control thread (producer) to the audio thread (consumer) without locks.
 *
 * ## Memory ordering
 *
 * The implementation relies on acquire/release semantics on the read and write
 * position atomics to ensure correct visibility:
 *
 * - **push()** writes the data *before* the release-store of writePos_, so the
 *   consumer sees the data once it observes the updated write position.
 * - **pop()** reads the data *before* the release-store of readPos_, so the
 *   producer sees the freed slot once it observes the updated read position.
 * - Local loads of a thread's own index use relaxed ordering (no cross-thread
 *   dependency on the thread's own variable).
 *
 * ## Capacity convention
 *
 * One slot is always kept empty to distinguish "full" from "empty":
 *   - **Empty:** readPos == writePos
 *   - **Full:**  (writePos + 1) % Capacity == readPos
 *
 * Therefore, the usable capacity is `Capacity - 1` items.
 *
 * @tparam T        Element type (must be trivially copyable for lock-free safety).
 * @tparam Capacity Total number of slots (usable capacity is Capacity - 1).
 */
#pragma once
#include <atomic>
#include <array>
#include <cstdint>

namespace klarinet {

template <typename T, int32_t Capacity = 256>
class RingBuffer {
public:
    /**
     * @brief Attempt to push an item into the buffer (producer side).
     *
     * Called from the **control thread** (single producer). If the buffer is
     * full the call is non-blocking and returns false.
     *
     * @param item The item to enqueue.
     * @return true if the item was enqueued, false if the buffer is full.
     */
    bool push(const T& item) {
        int32_t currentWrite = writePos_.load(std::memory_order_relaxed);
        int32_t nextWrite = (currentWrite + 1) % Capacity;
        if (nextWrite == readPos_.load(std::memory_order_acquire)) return false;
        buffer_[currentWrite] = item;
        writePos_.store(nextWrite, std::memory_order_release);
        return true;
    }

    /**
     * @brief Attempt to pop an item from the buffer (consumer side).
     *
     * Called from the **audio thread** (single consumer). If the buffer is
     * empty the call is non-blocking and returns false.
     *
     * @param[out] item Receives the dequeued item on success.
     * @return true if an item was dequeued, false if the buffer is empty.
     */
    bool pop(T& item) {
        int32_t currentRead = readPos_.load(std::memory_order_relaxed);
        if (currentRead == writePos_.load(std::memory_order_acquire)) return false;
        item = buffer_[currentRead];
        readPos_.store((currentRead + 1) % Capacity, std::memory_order_release);
        return true;
    }

    /**
     * @brief Check whether the buffer is empty.
     *
     * Uses acquire loads on both positions for a consistent snapshot. However,
     * the result may be stale by the time the caller acts on it.
     *
     * @return true if no items are available.
     */
    bool isEmpty() const {
        return readPos_.load(std::memory_order_acquire) == writePos_.load(std::memory_order_acquire);
    }

private:
    std::array<T, Capacity> buffer_{};       ///< Fixed-size storage for queued items.
    std::atomic<int32_t> readPos_{0};        ///< Consumer (audio thread) read cursor.
    std::atomic<int32_t> writePos_{0};       ///< Producer (control thread) write cursor.
};

} // namespace klarinet
