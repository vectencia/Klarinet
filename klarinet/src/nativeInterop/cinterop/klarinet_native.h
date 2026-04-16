#ifndef KLARINET_NATIVE_H
#define KLARINET_NATIVE_H

#ifdef __cplusplus
extern "C" {
#endif

/* Opaque handles */
typedef struct KlarinetContext KlarinetContext;
typedef struct KlarinetDevice  KlarinetDevice;
typedef struct KlarinetDecoder KlarinetDecoder;
typedef struct KlarinetEncoder KlarinetEncoder;

/* Callback: called on the audio thread with interleaved float samples. */
typedef void (*KlarinetDataCallback)(
    void* userData,
    float* buffer,       /* output: write here; input: read from here */
    int frameCount,
    int channelCount,
    int isCapture        /* 1 = input device, 0 = output device */
);

/* ---- Context ---- */
KlarinetContext* klarinet_context_init(void);
void             klarinet_context_uninit(KlarinetContext* ctx);

/* ---- Device (AudioStream) ---- */
KlarinetDevice* klarinet_device_init(
    KlarinetContext* ctx,
    int sampleRate,
    int channelCount,
    int bufferCapacityInFrames,
    int direction,           /* 0 = playback, 1 = capture */
    KlarinetDataCallback cb,
    void* userData
);
int    klarinet_device_start(KlarinetDevice* dev);
int    klarinet_device_stop(KlarinetDevice* dev);
void   klarinet_device_uninit(KlarinetDevice* dev);
int    klarinet_device_get_state(KlarinetDevice* dev);
double klarinet_device_get_latency_ms(KlarinetDevice* dev);

/* ---- Device enumeration ---- */
int         klarinet_get_playback_device_count(KlarinetContext* ctx);
int         klarinet_get_capture_device_count(KlarinetContext* ctx);
const char* klarinet_get_playback_device_name(KlarinetContext* ctx, int index);
const char* klarinet_get_capture_device_name(KlarinetContext* ctx, int index);

/* ---- Decoder (AudioFileReader) ---- */
KlarinetDecoder* klarinet_decoder_init_file(const char* path);
int              klarinet_decoder_get_sample_rate(KlarinetDecoder* dec);
int              klarinet_decoder_get_channels(KlarinetDecoder* dec);
long long        klarinet_decoder_get_total_frames(KlarinetDecoder* dec);
long long        klarinet_decoder_read_frames(KlarinetDecoder* dec, float* output, int frameCount);
int              klarinet_decoder_seek(KlarinetDecoder* dec, long long frameIndex);
void             klarinet_decoder_uninit(KlarinetDecoder* dec);

/* ---- Encoder (AudioFileWriter) ---- */
KlarinetEncoder* klarinet_encoder_init_file(const char* path, int format, int channels, int sampleRate);
int              klarinet_encoder_write_frames(KlarinetEncoder* enc, const float* data, int frameCount);
void             klarinet_encoder_uninit(KlarinetEncoder* enc);

#ifdef __cplusplus
}
#endif

#endif /* KLARINET_NATIVE_H */
