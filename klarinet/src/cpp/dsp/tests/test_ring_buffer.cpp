#include <cassert>
#include <cstdio>
#include <thread>
#include <atomic>

#include "RingBuffer.h"

// ============================================================================
// Basic push/pop
// ============================================================================

static void test_push_pop_basic() {
    printf("  RingBuffer: basic push/pop... ");
    klarinet::RingBuffer<int, 8> rb;
    assert(rb.isEmpty());

    assert(rb.push(10));
    assert(rb.push(20));
    assert(rb.push(30));
    assert(!rb.isEmpty());

    int val = 0;
    assert(rb.pop(val));
    assert(val == 10);
    assert(rb.pop(val));
    assert(val == 20);
    assert(rb.pop(val));
    assert(val == 30);
    assert(rb.isEmpty());

    printf("PASS\n");
}

// ============================================================================
// Full buffer
// ============================================================================

static void test_full_buffer() {
    printf("  RingBuffer: full buffer rejects push... ");
    klarinet::RingBuffer<int, 4> rb; // capacity 4, usable slots = 3

    assert(rb.push(1));
    assert(rb.push(2));
    assert(rb.push(3));
    // Buffer should be full now (one slot reserved)
    assert(!rb.push(4));

    int val = 0;
    assert(rb.pop(val));
    assert(val == 1);

    // Now there's room for one more
    assert(rb.push(4));
    assert(!rb.push(5));

    printf("PASS\n");
}

// ============================================================================
// Empty buffer
// ============================================================================

static void test_empty_buffer() {
    printf("  RingBuffer: pop from empty returns false... ");
    klarinet::RingBuffer<int, 8> rb;

    int val = -1;
    assert(!rb.pop(val));
    assert(val == -1); // unchanged
    assert(rb.isEmpty());

    printf("PASS\n");
}

// ============================================================================
// Wrap-around
// ============================================================================

static void test_wrap_around() {
    printf("  RingBuffer: wrap-around... ");
    klarinet::RingBuffer<int, 4> rb;

    // Fill and drain multiple times to force wrap-around
    for (int round = 0; round < 5; round++) {
        assert(rb.push(round * 10 + 1));
        assert(rb.push(round * 10 + 2));
        assert(rb.push(round * 10 + 3));

        int val = 0;
        assert(rb.pop(val));
        assert(val == round * 10 + 1);
        assert(rb.pop(val));
        assert(val == round * 10 + 2);
        assert(rb.pop(val));
        assert(val == round * 10 + 3);
        assert(rb.isEmpty());
    }

    printf("PASS\n");
}

// ============================================================================
// Concurrent producer/consumer with 10000 items
// ============================================================================

static void test_concurrent_producer_consumer() {
    printf("  RingBuffer: concurrent producer/consumer (10000 items)... ");
    constexpr int NUM_ITEMS = 10000;
    klarinet::RingBuffer<int, 256> rb;
    std::atomic<bool> done{false};

    std::thread producer([&]() {
        for (int i = 0; i < NUM_ITEMS; i++) {
            while (!rb.push(i)) {
                // spin until space available
            }
        }
        done.store(true, std::memory_order_release);
    });

    int received = 0;
    int expected = 0;
    while (true) {
        int val;
        if (rb.pop(val)) {
            assert(val == expected);
            expected++;
            received++;
            if (received == NUM_ITEMS) break;
        } else if (done.load(std::memory_order_acquire) && rb.isEmpty()) {
            break;
        }
    }

    producer.join();

    assert(received == NUM_ITEMS);
    assert(expected == NUM_ITEMS);

    printf("PASS\n");
}

// ============================================================================
// Struct type
// ============================================================================

struct TestStruct {
    int a;
    float b;
};

static void test_struct_type() {
    printf("  RingBuffer: works with struct type... ");
    klarinet::RingBuffer<TestStruct, 8> rb;

    assert(rb.push({1, 2.0f}));
    assert(rb.push({3, 4.5f}));

    TestStruct val{};
    assert(rb.pop(val));
    assert(val.a == 1);
    assert(val.b == 2.0f);
    assert(rb.pop(val));
    assert(val.a == 3);
    assert(val.b == 4.5f);

    printf("PASS\n");
}

// ============================================================================
// Main
// ============================================================================

int main() {
    printf("=== RingBuffer ===\n");
    test_push_pop_basic();
    test_full_buffer();
    test_empty_buffer();
    test_wrap_around();
    test_concurrent_producer_consumer();
    test_struct_type();

    printf("\nAll RingBuffer tests passed!\n");
    return 0;
}
