/**
 * @file klarinet_queues.h
 * @brief Internal C ABI for the Rust FIFO and ring.
 *
 * The dspTests host library compiles `AudioFifo` and `RingBuffer` as shims
 * over these symbols. They are not part of `klarinet_dsp.h`. Other targets
 * keep the C++ bodies and do not link this ABI.
 */
#pragma once

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct KlarinetFifo KlarinetFifo;

KlarinetFifo* klarinet_fifo_create(int32_t capacitySamples);
void klarinet_fifo_destroy(KlarinetFifo* fifo);
int32_t klarinet_fifo_write(KlarinetFifo* fifo, const float* src, int32_t count);
int32_t klarinet_fifo_read(KlarinetFifo* fifo, float* dst, int32_t count);
int32_t klarinet_fifo_available_to_read(const KlarinetFifo* fifo);
int32_t klarinet_fifo_available_to_write(const KlarinetFifo* fifo);
int32_t klarinet_fifo_capacity(const KlarinetFifo* fifo);

typedef struct KlarinetRing KlarinetRing;

KlarinetRing* klarinet_ring_create(int32_t capacity, int32_t elemSize, int32_t elemAlign);
void klarinet_ring_destroy(KlarinetRing* ring);
int klarinet_ring_push(KlarinetRing* ring, const void* item);
int klarinet_ring_pop(KlarinetRing* ring, void* item);
int klarinet_ring_is_empty(const KlarinetRing* ring);

#ifdef __cplusplus
}
#endif
