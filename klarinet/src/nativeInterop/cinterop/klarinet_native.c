#define MINIAUDIO_IMPLEMENTATION
#include "miniaudio.h"
#include "klarinet_native.h"
#include <stdlib.h>
#include <string.h>

/* ------------------------------------------------------------------ */
/* Opaque structs                                                      */
/* ------------------------------------------------------------------ */

struct KlarinetContext {
    ma_context ctx;
};

struct KlarinetDevice {
    ma_device              device;
    KlarinetDataCallback   callback;
    void*                  userData;
    int                    channelCount;
};

struct KlarinetDecoder {
    ma_decoder decoder;
};

struct KlarinetEncoder {
    ma_encoder encoder;
};

/* ------------------------------------------------------------------ */
/* miniaudio data callback bridge                                      */
/* ------------------------------------------------------------------ */

static void ma_data_callback(ma_device* pDevice, void* pOutput, const void* pInput, ma_uint32 frameCount) {
    KlarinetDevice* kd = (KlarinetDevice*)pDevice->pUserData;
    if (kd == NULL || kd->callback == NULL) return;

    if (pDevice->type == ma_device_type_playback) {
        kd->callback(kd->userData, (float*)pOutput, (int)frameCount, kd->channelCount, 0);
    } else if (pDevice->type == ma_device_type_capture) {
        /* Copy input to a mutable buffer for the callback */
        int totalSamples = (int)frameCount * kd->channelCount;
        float* buf = (float*)pInput; /* miniaudio guarantees f32 format, safe to cast away const for read */
        kd->callback(kd->userData, buf, (int)frameCount, kd->channelCount, 1);
    }
}

/* ------------------------------------------------------------------ */
/* Context                                                             */
/* ------------------------------------------------------------------ */

KlarinetContext* klarinet_context_init(void) {
    KlarinetContext* ctx = (KlarinetContext*)calloc(1, sizeof(KlarinetContext));
    if (ctx == NULL) return NULL;
    if (ma_context_init(NULL, 0, NULL, &ctx->ctx) != MA_SUCCESS) {
        free(ctx);
        return NULL;
    }
    return ctx;
}

void klarinet_context_uninit(KlarinetContext* ctx) {
    if (ctx == NULL) return;
    ma_context_uninit(&ctx->ctx);
    free(ctx);
}

/* ------------------------------------------------------------------ */
/* Device                                                              */
/* ------------------------------------------------------------------ */

KlarinetDevice* klarinet_device_init(
    KlarinetContext* ctx,
    int sampleRate,
    int channelCount,
    int bufferCapacityInFrames,
    int direction,
    KlarinetDataCallback cb,
    void* userData
) {
    if (ctx == NULL) return NULL;

    KlarinetDevice* kd = (KlarinetDevice*)calloc(1, sizeof(KlarinetDevice));
    if (kd == NULL) return NULL;

    kd->callback     = cb;
    kd->userData      = userData;
    kd->channelCount  = channelCount;

    ma_device_config config;
    if (direction == 0) {
        config = ma_device_config_init(ma_device_type_playback);
    } else {
        config = ma_device_config_init(ma_device_type_capture);
    }

    config.sampleRate            = (ma_uint32)sampleRate;
    config.playback.channels     = (ma_uint32)channelCount;
    config.playback.format       = ma_format_f32;
    config.capture.channels      = (ma_uint32)channelCount;
    config.capture.format        = ma_format_f32;
    config.performanceProfile    = ma_performance_profile_low_latency;

    if (bufferCapacityInFrames > 0) {
        config.periodSizeInFrames = (ma_uint32)bufferCapacityInFrames;
    }

    if (cb != NULL) {
        config.dataCallback = ma_data_callback;
        config.pUserData    = kd;
    }

    if (ma_device_init(&ctx->ctx, &config, &kd->device) != MA_SUCCESS) {
        free(kd);
        return NULL;
    }

    return kd;
}

int klarinet_device_start(KlarinetDevice* dev) {
    if (dev == NULL) return -1;
    return ma_device_start(&dev->device) == MA_SUCCESS ? 0 : -1;
}

int klarinet_device_stop(KlarinetDevice* dev) {
    if (dev == NULL) return -1;
    return ma_device_stop(&dev->device) == MA_SUCCESS ? 0 : -1;
}

void klarinet_device_uninit(KlarinetDevice* dev) {
    if (dev == NULL) return;
    ma_device_uninit(&dev->device);
    free(dev);
}

int klarinet_device_get_state(KlarinetDevice* dev) {
    if (dev == NULL) return 0;
    return (int)ma_device_get_state(&dev->device);
}

double klarinet_device_get_latency_ms(KlarinetDevice* dev) {
    if (dev == NULL) return 0.0;
    ma_uint32 sr = dev->device.sampleRate;
    if (sr == 0) return 0.0;
    ma_uint32 period = dev->device.playback.internalPeriodSizeInFrames;
    if (period == 0) period = dev->device.capture.internalPeriodSizeInFrames;
    return (double)period * 1000.0 / (double)sr;
}

/* ------------------------------------------------------------------ */
/* Device enumeration                                                  */
/* ------------------------------------------------------------------ */

int klarinet_get_playback_device_count(KlarinetContext* ctx) {
    if (ctx == NULL) return 0;
    ma_device_info* pPlay; ma_uint32 playCount;
    ma_device_info* pCap;  ma_uint32 capCount;
    if (ma_context_get_devices(&ctx->ctx, &pPlay, &playCount, &pCap, &capCount) != MA_SUCCESS) return 0;
    return (int)playCount;
}

int klarinet_get_capture_device_count(KlarinetContext* ctx) {
    if (ctx == NULL) return 0;
    ma_device_info* pPlay; ma_uint32 playCount;
    ma_device_info* pCap;  ma_uint32 capCount;
    if (ma_context_get_devices(&ctx->ctx, &pPlay, &playCount, &pCap, &capCount) != MA_SUCCESS) return 0;
    return (int)capCount;
}

/* Static buffer for device names — not thread-safe, but sufficient for enumeration */
static char g_deviceNameBuf[256];

const char* klarinet_get_playback_device_name(KlarinetContext* ctx, int index) {
    if (ctx == NULL) return "Unknown";
    ma_device_info* pPlay; ma_uint32 playCount;
    if (ma_context_get_devices(&ctx->ctx, &pPlay, &playCount, NULL, NULL) != MA_SUCCESS) return "Unknown";
    if ((ma_uint32)index >= playCount) return "Unknown";
    strncpy(g_deviceNameBuf, pPlay[index].name, sizeof(g_deviceNameBuf) - 1);
    g_deviceNameBuf[sizeof(g_deviceNameBuf) - 1] = '\0';
    return g_deviceNameBuf;
}

const char* klarinet_get_capture_device_name(KlarinetContext* ctx, int index) {
    if (ctx == NULL) return "Unknown";
    ma_device_info* pCap; ma_uint32 capCount;
    if (ma_context_get_devices(&ctx->ctx, NULL, NULL, &pCap, &capCount) != MA_SUCCESS) return "Unknown";
    if ((ma_uint32)index >= capCount) return "Unknown";
    strncpy(g_deviceNameBuf, pCap[index].name, sizeof(g_deviceNameBuf) - 1);
    g_deviceNameBuf[sizeof(g_deviceNameBuf) - 1] = '\0';
    return g_deviceNameBuf;
}

/* ------------------------------------------------------------------ */
/* Decoder                                                             */
/* ------------------------------------------------------------------ */

KlarinetDecoder* klarinet_decoder_init_file(const char* path) {
    KlarinetDecoder* dec = (KlarinetDecoder*)calloc(1, sizeof(KlarinetDecoder));
    if (dec == NULL) return NULL;
    ma_decoder_config config = ma_decoder_config_init(ma_format_f32, 0, 0);
    if (ma_decoder_init_file(path, &config, &dec->decoder) != MA_SUCCESS) {
        free(dec);
        return NULL;
    }
    return dec;
}

int klarinet_decoder_get_sample_rate(KlarinetDecoder* dec) {
    if (dec == NULL) return 0;
    return (int)dec->decoder.outputSampleRate;
}

int klarinet_decoder_get_channels(KlarinetDecoder* dec) {
    if (dec == NULL) return 0;
    return (int)dec->decoder.outputChannels;
}

long long klarinet_decoder_get_total_frames(KlarinetDecoder* dec) {
    if (dec == NULL) return 0;
    ma_uint64 total;
    if (ma_decoder_get_length_in_pcm_frames(&dec->decoder, &total) != MA_SUCCESS) return 0;
    return (long long)total;
}

long long klarinet_decoder_read_frames(KlarinetDecoder* dec, float* output, int frameCount) {
    if (dec == NULL) return 0;
    ma_uint64 framesRead;
    ma_decoder_read_pcm_frames(&dec->decoder, output, (ma_uint64)frameCount, &framesRead);
    return (long long)framesRead;
}

int klarinet_decoder_seek(KlarinetDecoder* dec, long long frameIndex) {
    if (dec == NULL) return -1;
    return ma_decoder_seek_to_pcm_frame(&dec->decoder, (ma_uint64)frameIndex) == MA_SUCCESS ? 0 : -1;
}

void klarinet_decoder_uninit(KlarinetDecoder* dec) {
    if (dec == NULL) return;
    ma_decoder_uninit(&dec->decoder);
    free(dec);
}

/* ------------------------------------------------------------------ */
/* Encoder                                                             */
/* ------------------------------------------------------------------ */

KlarinetEncoder* klarinet_encoder_init_file(const char* path, int format, int channels, int sampleRate) {
    (void)format; /* only WAV */
    KlarinetEncoder* enc = (KlarinetEncoder*)calloc(1, sizeof(KlarinetEncoder));
    if (enc == NULL) return NULL;
    ma_encoder_config config = ma_encoder_config_init(
        ma_encoding_format_wav, ma_format_f32,
        (ma_uint32)channels, (ma_uint32)sampleRate
    );
    if (ma_encoder_init_file(path, &config, &enc->encoder) != MA_SUCCESS) {
        free(enc);
        return NULL;
    }
    return enc;
}

int klarinet_encoder_write_frames(KlarinetEncoder* enc, const float* data, int frameCount) {
    if (enc == NULL) return -1;
    ma_uint64 written;
    return ma_encoder_write_pcm_frames(&enc->encoder, data, (ma_uint64)frameCount, &written) == MA_SUCCESS ? 0 : -1;
}

void klarinet_encoder_uninit(KlarinetEncoder* enc) {
    if (enc == NULL) return;
    ma_encoder_uninit(&enc->encoder);
    free(enc);
}
