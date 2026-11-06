#ifndef KLARINET_MA_H
#define KLARINET_MA_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Opaque miniaudio objects. The JVM cdylib never includes miniaudio.h. */

typedef struct KlarinetMaContext KlarinetMaContext;
typedef struct KlarinetMaDevice KlarinetMaDevice;
typedef struct KlarinetMaDecoder KlarinetMaDecoder;
typedef struct KlarinetMaEncoder KlarinetMaEncoder;

/*
 * Audio-thread trampoline target. is_capture is 1 only when the device is a
 * capture device and the input pointer is non-NULL. Otherwise the caller
 * treats a non-NULL output as playback. This function must not allocate.
 */
typedef void (*klarinet_ma_data_fn)(
    void* user,
    int32_t is_capture,
    float* output,
    const float* input,
    int32_t frame_count);

typedef struct KlarinetMaDeviceConfig {
    int32_t sample_rate;
    int32_t channel_count;
    int32_t period_frames;
    int32_t direction;
    int32_t device_id;
    klarinet_ma_data_fn data_callback;
    void* user;
} KlarinetMaDeviceConfig;

KlarinetMaContext* klarinet_ma_context_init(void);
void klarinet_ma_context_uninit(KlarinetMaContext* context);

int32_t klarinet_ma_playback_count(KlarinetMaContext* context);
int32_t klarinet_ma_capture_count(KlarinetMaContext* context);
int32_t klarinet_ma_playback_name(
    KlarinetMaContext* context, int32_t index, char* out, int32_t out_len);
int32_t klarinet_ma_capture_name(
    KlarinetMaContext* context, int32_t index, char* out, int32_t out_len);

/* 0 when the id can be opened, -1 when it is out of range or enumeration fails. */
int32_t klarinet_ma_check_device_id(
    KlarinetMaContext* context, int32_t direction, int32_t device_id);

KlarinetMaDevice* klarinet_ma_device_init(
    KlarinetMaContext* context, const KlarinetMaDeviceConfig* config);
void klarinet_ma_device_start(KlarinetMaDevice* device);
void klarinet_ma_device_stop(KlarinetMaDevice* device);
void klarinet_ma_device_uninit(KlarinetMaDevice* device);
int32_t klarinet_ma_device_state(KlarinetMaDevice* device);
double klarinet_ma_device_latency_ms(KlarinetMaDevice* device);

KlarinetMaDecoder* klarinet_ma_decoder_open(const char* path);
int32_t klarinet_ma_decoder_sample_rate(KlarinetMaDecoder* decoder);
int32_t klarinet_ma_decoder_channels(KlarinetMaDecoder* decoder);
int64_t klarinet_ma_decoder_total_frames(KlarinetMaDecoder* decoder);
int64_t klarinet_ma_decoder_read(KlarinetMaDecoder* decoder, float* dst, int32_t frame_count);
int32_t klarinet_ma_decoder_seek(KlarinetMaDecoder* decoder, int64_t frame_index);
void klarinet_ma_decoder_close(KlarinetMaDecoder* decoder);

KlarinetMaEncoder* klarinet_ma_encoder_open(
    const char* path, int32_t channels, int32_t sample_rate);
int32_t klarinet_ma_encoder_write(
    KlarinetMaEncoder* encoder, const float* data, int32_t frame_count);
void klarinet_ma_encoder_close(KlarinetMaEncoder* encoder);

#ifdef __cplusplus
}
#endif

#endif
