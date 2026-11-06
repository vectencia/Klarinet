#pragma once

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Audio-thread entry. The adapter does not allocate, lock, log, or enter the
 * JVM before this call. `is_input` is 1 for capture and 0 for playback.
 */
typedef void (*KlarinetOboeAudioFn)(
    void* user,
    int is_input,
    float* samples,
    int32_t num_frames,
    int32_t channel_count);

/**
 * Routing-thread entry. This is not the data callback. The adapter does not
 * take the engine lock before forwarding the device ids.
 */
typedef void (*KlarinetOboeRouteFn)(
    void* user,
    const int32_t* device_ids,
    int32_t num_devices);

typedef struct KlarinetOboeOpen {
    int32_t sample_rate;
    int32_t channel_count;
    int32_t audio_format;
    int32_t buffer_capacity_in_frames;
    int32_t performance_mode;
    int32_t sharing_mode;
    int32_t direction;
    int32_t device_id;
    KlarinetOboeAudioFn audio;
    KlarinetOboeRouteFn route;
    void* user;
} KlarinetOboeOpen;

typedef struct KlarinetOboeInfo {
    int32_t frames_per_burst;
    int32_t channel_count;
    int32_t direction;
    int32_t sample_rate;
    int32_t device_count;
    int32_t device_capacity;
    int32_t* device_ids;
} KlarinetOboeInfo;

/** Opens an Oboe stream. The returned owner is the stream handle. NULL on failure. */
void* klarinet_oboe_open(const KlarinetOboeOpen* request, KlarinetOboeInfo* info);

int klarinet_oboe_request_start(void* handle);
int klarinet_oboe_request_pause(void* handle);
int klarinet_oboe_request_stop(void* handle);
/** Blocking stop used by the engine destructor. */
int klarinet_oboe_stop(void* handle);
void klarinet_oboe_close(void* handle);

int klarinet_oboe_write(void* handle, const float* data, int32_t num_frames, int64_t timeout_nanos);
int klarinet_oboe_read(void* handle, float* data, int32_t num_frames, int64_t timeout_nanos);
int klarinet_oboe_state(void* handle);
double klarinet_oboe_latency_ms(void* handle);

int klarinet_oboe_invalid_handle(void);
const char* klarinet_oboe_result_text(int result);
void klarinet_oboe_log(int priority, const char* tag, const char* message);

#ifdef __cplusplus
}
#endif
