#include "klarinet_ma.h"

#include <stdlib.h>
#include <string.h>

#include "miniaudio.h"

struct KlarinetMaContext {
    ma_context context;
};

struct KlarinetMaDevice {
    ma_device device;
    klarinet_ma_data_fn data_callback;
    void* user;
};

struct KlarinetMaDecoder {
    ma_decoder decoder;
};

struct KlarinetMaEncoder {
    ma_encoder encoder;
};

static void copy_cstr(char* out, int32_t out_len, const char* name) {
    size_t i = 0;
    if (out == NULL || out_len <= 0) {
        return;
    }
    if (name != NULL) {
        while (i + 1 < (size_t)out_len && name[i] != '\0') {
            out[i] = name[i];
            i++;
        }
    }
    out[i] = '\0';
}

static void ma_data_trampoline(
    ma_device* device, void* output, const void* input, ma_uint32 frame_count
) {
    KlarinetMaDevice* wrapper;
    int32_t is_capture;

    if (device == NULL) {
        return;
    }
    wrapper = (KlarinetMaDevice*)device->pUserData;
    if (wrapper == NULL || wrapper->data_callback == NULL) {
        return;
    }
    is_capture = (device->type == ma_device_type_capture && input != NULL) ? 1 : 0;
    wrapper->data_callback(
        wrapper->user,
        is_capture,
        (float*)output,
        (const float*)input,
        (int32_t)frame_count);
}

static int32_t device_id_available(
    KlarinetMaContext* context, int32_t direction, int32_t device_id
) {
    ma_device_info* playback = NULL;
    ma_device_info* capture = NULL;
    ma_uint32 playback_count = 0;
    ma_uint32 capture_count = 0;

    if (device_id < 0) {
        return 0;
    }
    if (context == NULL) {
        return -1;
    }
    if (ma_context_get_devices(
            &context->context, &playback, &playback_count, &capture, &capture_count) != MA_SUCCESS) {
        return -1;
    }
    if (direction == 0) {
        if ((ma_uint32)device_id >= playback_count) {
            return -1;
        }
    } else {
        int32_t capture_index = device_id - (int32_t)playback_count;
        if (capture_index < 0 || (ma_uint32)capture_index >= capture_count) {
            return -1;
        }
    }
    return 0;
}

static int apply_device_id(
    KlarinetMaContext* context, ma_device_config* config, int32_t direction, int32_t device_id
) {
    ma_device_info* playback = NULL;
    ma_device_info* capture = NULL;
    ma_uint32 playback_count = 0;
    ma_uint32 capture_count = 0;

    if (device_id < 0) {
        return 0;
    }
    if (context == NULL) {
        return -1;
    }
    if (ma_context_get_devices(
            &context->context, &playback, &playback_count, &capture, &capture_count) != MA_SUCCESS) {
        return -1;
    }
    if (direction == 0) {
        if ((ma_uint32)device_id >= playback_count) {
            return -1;
        }
        config->playback.pDeviceID = &playback[device_id].id;
    } else {
        int32_t capture_index = device_id - (int32_t)playback_count;
        if (capture_index < 0 || (ma_uint32)capture_index >= capture_count) {
            return -1;
        }
        config->capture.pDeviceID = &capture[capture_index].id;
    }
    return 0;
}

KlarinetMaContext* klarinet_ma_context_init(void) {
    KlarinetMaContext* context = (KlarinetMaContext*)malloc(sizeof(KlarinetMaContext));
    if (context == NULL) {
        return NULL;
    }
    if (ma_context_init(NULL, 0, NULL, &context->context) != MA_SUCCESS) {
        free(context);
        return NULL;
    }
    return context;
}

void klarinet_ma_context_uninit(KlarinetMaContext* context) {
    if (context == NULL) {
        return;
    }
    ma_context_uninit(&context->context);
    free(context);
}

int32_t klarinet_ma_playback_count(KlarinetMaContext* context) {
    ma_device_info* playback = NULL;
    ma_device_info* capture = NULL;
    ma_uint32 playback_count = 0;
    ma_uint32 capture_count = 0;
    if (context == NULL) {
        return 0;
    }
    if (ma_context_get_devices(
            &context->context, &playback, &playback_count, &capture, &capture_count) != MA_SUCCESS) {
        return 0;
    }
    return (int32_t)playback_count;
}

int32_t klarinet_ma_capture_count(KlarinetMaContext* context) {
    ma_device_info* playback = NULL;
    ma_device_info* capture = NULL;
    ma_uint32 playback_count = 0;
    ma_uint32 capture_count = 0;
    if (context == NULL) {
        return 0;
    }
    if (ma_context_get_devices(
            &context->context, &playback, &playback_count, &capture, &capture_count) != MA_SUCCESS) {
        return 0;
    }
    return (int32_t)capture_count;
}

int32_t klarinet_ma_playback_name(
    KlarinetMaContext* context, int32_t index, char* out, int32_t out_len
) {
    ma_device_info* playback = NULL;
    ma_uint32 playback_count = 0;
    if (context == NULL || out == NULL || out_len <= 0) {
        return -1;
    }
    if (ma_context_get_devices(&context->context, &playback, &playback_count, NULL, NULL) != MA_SUCCESS
        || (ma_uint32)index >= playback_count) {
        return -1;
    }
    copy_cstr(out, out_len, playback[index].name);
    return 0;
}

int32_t klarinet_ma_capture_name(
    KlarinetMaContext* context, int32_t index, char* out, int32_t out_len
) {
    ma_device_info* capture = NULL;
    ma_uint32 capture_count = 0;
    if (context == NULL || out == NULL || out_len <= 0) {
        return -1;
    }
    if (ma_context_get_devices(&context->context, NULL, NULL, &capture, &capture_count) != MA_SUCCESS
        || (ma_uint32)index >= capture_count) {
        return -1;
    }
    copy_cstr(out, out_len, capture[index].name);
    return 0;
}

int32_t klarinet_ma_check_device_id(
    KlarinetMaContext* context, int32_t direction, int32_t device_id
) {
    return device_id_available(context, direction, device_id);
}

KlarinetMaDevice* klarinet_ma_device_init(
    KlarinetMaContext* context, const KlarinetMaDeviceConfig* config
) {
    KlarinetMaDevice* device;
    ma_device_config ma_config;
    ma_context* ma_context = NULL;

    if (config == NULL) {
        return NULL;
    }
    device = (KlarinetMaDevice*)calloc(1, sizeof(KlarinetMaDevice));
    if (device == NULL) {
        return NULL;
    }
    if (config->direction == 0) {
        ma_config = ma_device_config_init(ma_device_type_playback);
    } else {
        ma_config = ma_device_config_init(ma_device_type_capture);
    }
    ma_config.sampleRate = (ma_uint32)config->sample_rate;
    ma_config.playback.channels = (ma_uint32)config->channel_count;
    ma_config.playback.format = ma_format_f32;
    ma_config.capture.channels = (ma_uint32)config->channel_count;
    ma_config.capture.format = ma_format_f32;
    ma_config.performanceProfile = ma_performance_profile_low_latency;
    if (config->period_frames > 0) {
        ma_config.periodSizeInFrames = (ma_uint32)config->period_frames;
    }
    if (context != NULL) {
        ma_context = &context->context;
    }
    if (apply_device_id(context, &ma_config, config->direction, config->device_id) != 0) {
        free(device);
        return NULL;
    }
    device->data_callback = config->data_callback;
    device->user = config->user;
    if (config->data_callback != NULL) {
        ma_config.dataCallback = ma_data_trampoline;
        ma_config.pUserData = device;
    }
    if (ma_device_init(ma_context, &ma_config, &device->device) != MA_SUCCESS) {
        free(device);
        return NULL;
    }
    return device;
}

int32_t klarinet_ma_device_start(KlarinetMaDevice* device) {
    if (device == NULL) {
        return -1;
    }
    return ma_device_start(&device->device) == MA_SUCCESS ? 0 : -1;
}

int32_t klarinet_ma_device_stop(KlarinetMaDevice* device) {
    if (device == NULL) {
        return -1;
    }
    return ma_device_stop(&device->device) == MA_SUCCESS ? 0 : -1;
}

void klarinet_ma_device_uninit(KlarinetMaDevice* device) {
    if (device == NULL) {
        return;
    }
    ma_device_uninit(&device->device);
    free(device);
}

int32_t klarinet_ma_device_state(KlarinetMaDevice* device) {
    if (device == NULL) {
        return 0;
    }
    return (int32_t)ma_device_get_state(&device->device);
}

double klarinet_ma_device_latency_ms(KlarinetMaDevice* device) {
    ma_uint32 sample_rate;
    ma_uint32 period;
    if (device == NULL) {
        return 0.0;
    }
    sample_rate = device->device.sampleRate;
    if (sample_rate == 0) {
        return 0.0;
    }
    period = device->device.playback.internalPeriodSizeInFrames;
    if (period == 0) {
        period = device->device.capture.internalPeriodSizeInFrames;
    }
    return (double)period * 1000.0 / (double)sample_rate;
}

KlarinetMaDecoder* klarinet_ma_decoder_open(const char* path) {
    KlarinetMaDecoder* decoder;
    ma_decoder_config config;
    if (path == NULL) {
        return NULL;
    }
    decoder = (KlarinetMaDecoder*)malloc(sizeof(KlarinetMaDecoder));
    if (decoder == NULL) {
        return NULL;
    }
    config = ma_decoder_config_init(ma_format_f32, 0, 0);
    if (ma_decoder_init_file(path, &config, &decoder->decoder) != MA_SUCCESS) {
        free(decoder);
        return NULL;
    }
    return decoder;
}

int32_t klarinet_ma_decoder_sample_rate(KlarinetMaDecoder* decoder) {
    return (int32_t)decoder->decoder.outputSampleRate;
}

int32_t klarinet_ma_decoder_channels(KlarinetMaDecoder* decoder) {
    return (int32_t)decoder->decoder.outputChannels;
}

int64_t klarinet_ma_decoder_total_frames(KlarinetMaDecoder* decoder) {
    ma_uint64 total = 0;
    if (ma_decoder_get_length_in_pcm_frames(&decoder->decoder, &total) != MA_SUCCESS) {
        return 0;
    }
    return (int64_t)total;
}

int64_t klarinet_ma_decoder_read(KlarinetMaDecoder* decoder, float* dst, int32_t frame_count) {
    ma_uint64 frames_read = 0;
    ma_decoder_read_pcm_frames(&decoder->decoder, dst, (ma_uint64)frame_count, &frames_read);
    return (int64_t)frames_read;
}

int32_t klarinet_ma_decoder_seek(KlarinetMaDecoder* decoder, int64_t frame_index) {
    return ma_decoder_seek_to_pcm_frame(&decoder->decoder, (ma_uint64)frame_index) == MA_SUCCESS ? 1 : 0;
}

void klarinet_ma_decoder_close(KlarinetMaDecoder* decoder) {
    if (decoder == NULL) {
        return;
    }
    ma_decoder_uninit(&decoder->decoder);
    free(decoder);
}

KlarinetMaEncoder* klarinet_ma_encoder_open(
    const char* path, int32_t channels, int32_t sample_rate
) {
    KlarinetMaEncoder* encoder;
    ma_encoder_config config;
    if (path == NULL) {
        return NULL;
    }
    encoder = (KlarinetMaEncoder*)malloc(sizeof(KlarinetMaEncoder));
    if (encoder == NULL) {
        return NULL;
    }
    config = ma_encoder_config_init(
        ma_encoding_format_wav, ma_format_f32, (ma_uint32)channels, (ma_uint32)sample_rate);
    if (ma_encoder_init_file(path, &config, &encoder->encoder) != MA_SUCCESS) {
        free(encoder);
        return NULL;
    }
    return encoder;
}

int32_t klarinet_ma_encoder_write(
    KlarinetMaEncoder* encoder, const float* data, int32_t frame_count
) {
    ma_uint64 frames_written = 0;
    ma_result result = ma_encoder_write_pcm_frames(
        &encoder->encoder, data, (ma_uint64)frame_count, &frames_written);
    (void)frames_written;
    return result == MA_SUCCESS ? 1 : 0;
}

void klarinet_ma_encoder_close(KlarinetMaEncoder* encoder) {
    if (encoder == NULL) {
        return;
    }
    ma_encoder_uninit(&encoder->encoder);
    free(encoder);
}
