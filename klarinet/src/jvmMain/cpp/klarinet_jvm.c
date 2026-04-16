#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include "miniaudio.h"

/* ------------------------------------------------------------------ */
/* Callback bridging                                                   */
/* ------------------------------------------------------------------ */

typedef struct {
    JavaVM*     jvm;
    jobject     callbackObj;
    jmethodID   onAudioReadyMethod;
    ma_device   device;
    ma_uint32   channelCount;
} KlarinetDevice;

static void data_callback(ma_device* pDevice, void* pOutput, const void* pInput, ma_uint32 frameCount) {
    KlarinetDevice* kd = (KlarinetDevice*)pDevice->pUserData;
    if (kd == NULL || kd->callbackObj == NULL) return;

    JNIEnv* env = NULL;
    int attached = 0;
    if ((*kd->jvm)->GetEnv(kd->jvm, (void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
        (*kd->jvm)->AttachCurrentThreadAsDaemon(kd->jvm, (void**)&env, NULL);
        attached = 1;
    }
    if (env == NULL) return;

    ma_uint32 totalSamples = frameCount * kd->channelCount;
    jfloatArray jbuffer = (*env)->NewFloatArray(env, (jsize)totalSamples);
    if (jbuffer == NULL) goto detach;

    if (pDevice->type == ma_device_type_capture) {
        (*env)->SetFloatArrayRegion(env, jbuffer, 0, (jsize)totalSamples, (const jfloat*)pInput);
    }

    (*env)->CallIntMethod(env, kd->callbackObj, kd->onAudioReadyMethod,
                          jbuffer, (jint)frameCount);

    if (pDevice->type == ma_device_type_playback) {
        (*env)->GetFloatArrayRegion(env, jbuffer, 0, (jsize)totalSamples, (jfloat*)pOutput);
    }

    (*env)->DeleteLocalRef(env, jbuffer);

detach:
    if (attached) {
        (*kd->jvm)->DetachCurrentThread(kd->jvm);
    }
}

/* ------------------------------------------------------------------ */
/* Context                                                             */
/* ------------------------------------------------------------------ */

JNIEXPORT jlong JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeContextInit(JNIEnv* env, jobject thiz) {
    ma_context* ctx = (ma_context*)malloc(sizeof(ma_context));
    if (ctx == NULL) return 0;
    ma_result result = ma_context_init(NULL, 0, NULL, ctx);
    if (result != MA_SUCCESS) { free(ctx); return 0; }
    return (jlong)(intptr_t)ctx;
}

JNIEXPORT void JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeContextUninit(JNIEnv* env, jobject thiz, jlong contextPtr) {
    ma_context* ctx = (ma_context*)(intptr_t)contextPtr;
    if (ctx != NULL) { ma_context_uninit(ctx); free(ctx); }
}

/* ------------------------------------------------------------------ */
/* Device (AudioStream)                                                */
/* ------------------------------------------------------------------ */

JNIEXPORT jlong JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeDeviceInit(
    JNIEnv* env, jobject thiz, jlong contextPtr,
    jint sampleRate, jint channelCount, jint bufferCapacityInFrames,
    jint direction, jobject callbackObj
) {
    ma_context* ctx = (ma_context*)(intptr_t)contextPtr;
    KlarinetDevice* kd = (KlarinetDevice*)calloc(1, sizeof(KlarinetDevice));
    if (kd == NULL) return 0;

    kd->channelCount = (ma_uint32)channelCount;

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

    if (callbackObj != NULL) {
        (*env)->GetJavaVM(env, &kd->jvm);
        kd->callbackObj = (*env)->NewGlobalRef(env, callbackObj);

        jclass cbClass = (*env)->GetObjectClass(env, callbackObj);
        kd->onAudioReadyMethod = (*env)->GetMethodID(env, cbClass, "onAudioReady", "([FI)I");

        config.dataCallback = data_callback;
        config.pUserData    = kd;
    }

    ma_result result = ma_device_init(ctx, &config, &kd->device);
    if (result != MA_SUCCESS) {
        if (kd->callbackObj != NULL) (*env)->DeleteGlobalRef(env, kd->callbackObj);
        free(kd);
        return 0;
    }

    return (jlong)(intptr_t)kd;
}

JNIEXPORT void JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeDeviceStart(JNIEnv* env, jobject thiz, jlong devicePtr) {
    KlarinetDevice* kd = (KlarinetDevice*)(intptr_t)devicePtr;
    if (kd != NULL) ma_device_start(&kd->device);
}

JNIEXPORT void JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeDeviceStop(JNIEnv* env, jobject thiz, jlong devicePtr) {
    KlarinetDevice* kd = (KlarinetDevice*)(intptr_t)devicePtr;
    if (kd != NULL) ma_device_stop(&kd->device);
}

JNIEXPORT void JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeDeviceUninit(JNIEnv* env, jobject thiz, jlong devicePtr) {
    KlarinetDevice* kd = (KlarinetDevice*)(intptr_t)devicePtr;
    if (kd != NULL) {
        ma_device_uninit(&kd->device);
        if (kd->callbackObj != NULL) (*env)->DeleteGlobalRef(env, kd->callbackObj);
        free(kd);
    }
}

JNIEXPORT jint JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeDeviceWriteFloat(
    JNIEnv* env, jobject thiz, jlong devicePtr, jfloatArray data, jint numFrames, jlong timeoutNanos
) {
    (void)timeoutNanos;
    (void)data;
    (void)numFrames;
    /* miniaudio device API is callback-driven; push-mode not supported */
    return -1;
}

JNIEXPORT jint JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeDeviceReadFloat(
    JNIEnv* env, jobject thiz, jlong devicePtr, jfloatArray data, jint numFrames, jlong timeoutNanos
) {
    (void)timeoutNanos;
    (void)data;
    (void)numFrames;
    return -1;
}

JNIEXPORT jint JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeDeviceGetState(JNIEnv* env, jobject thiz, jlong devicePtr) {
    KlarinetDevice* kd = (KlarinetDevice*)(intptr_t)devicePtr;
    if (kd == NULL) return 0;
    return (jint)ma_device_get_state(&kd->device);
}

JNIEXPORT jdouble JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeDeviceGetLatencyMs(JNIEnv* env, jobject thiz, jlong devicePtr) {
    KlarinetDevice* kd = (KlarinetDevice*)(intptr_t)devicePtr;
    if (kd == NULL) return 0.0;
    ma_uint32 sr = kd->device.sampleRate;
    if (sr == 0) return 0.0;
    ma_uint32 period = kd->device.playback.internalPeriodSizeInFrames;
    if (period == 0) period = kd->device.capture.internalPeriodSizeInFrames;
    return (double)period * 1000.0 / (double)sr;
}

/* ------------------------------------------------------------------ */
/* Device enumeration                                                  */
/* ------------------------------------------------------------------ */

JNIEXPORT jint JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeGetPlaybackDeviceCount(JNIEnv* env, jobject thiz, jlong contextPtr) {
    ma_context* ctx = (ma_context*)(intptr_t)contextPtr;
    ma_device_info* pPlay; ma_uint32 playCount;
    ma_device_info* pCap;  ma_uint32 capCount;
    if (ma_context_get_devices(ctx, &pPlay, &playCount, &pCap, &capCount) != MA_SUCCESS) return 0;
    return (jint)playCount;
}

JNIEXPORT jint JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeGetCaptureDeviceCount(JNIEnv* env, jobject thiz, jlong contextPtr) {
    ma_context* ctx = (ma_context*)(intptr_t)contextPtr;
    ma_device_info* pPlay; ma_uint32 playCount;
    ma_device_info* pCap;  ma_uint32 capCount;
    if (ma_context_get_devices(ctx, &pPlay, &playCount, &pCap, &capCount) != MA_SUCCESS) return 0;
    return (jint)capCount;
}

JNIEXPORT jstring JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeGetPlaybackDeviceName(JNIEnv* env, jobject thiz, jlong contextPtr, jint index) {
    ma_context* ctx = (ma_context*)(intptr_t)contextPtr;
    ma_device_info* pPlay; ma_uint32 playCount;
    if (ma_context_get_devices(ctx, &pPlay, &playCount, NULL, NULL) != MA_SUCCESS || (ma_uint32)index >= playCount)
        return (*env)->NewStringUTF(env, "Unknown");
    return (*env)->NewStringUTF(env, pPlay[index].name);
}

JNIEXPORT jstring JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeGetCaptureDeviceName(JNIEnv* env, jobject thiz, jlong contextPtr, jint index) {
    ma_context* ctx = (ma_context*)(intptr_t)contextPtr;
    ma_device_info* pCap; ma_uint32 capCount;
    if (ma_context_get_devices(ctx, NULL, NULL, &pCap, &capCount) != MA_SUCCESS || (ma_uint32)index >= capCount)
        return (*env)->NewStringUTF(env, "Unknown");
    return (*env)->NewStringUTF(env, pCap[index].name);
}

/* ------------------------------------------------------------------ */
/* Decoder (AudioFileReader)                                           */
/* ------------------------------------------------------------------ */

JNIEXPORT jlong JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeDecoderInitFile(JNIEnv* env, jobject thiz, jstring path) {
    const char* cpath = (*env)->GetStringUTFChars(env, path, NULL);
    ma_decoder* decoder = (ma_decoder*)malloc(sizeof(ma_decoder));
    ma_decoder_config config = ma_decoder_config_init(ma_format_f32, 0, 0);
    ma_result result = ma_decoder_init_file(cpath, &config, decoder);
    (*env)->ReleaseStringUTFChars(env, path, cpath);
    if (result != MA_SUCCESS) { free(decoder); return 0; }
    return (jlong)(intptr_t)decoder;
}

JNIEXPORT jint JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeDecoderGetSampleRate(JNIEnv* env, jobject thiz, jlong decoderPtr) {
    ma_decoder* d = (ma_decoder*)(intptr_t)decoderPtr;
    return (jint)d->outputSampleRate;
}

JNIEXPORT jint JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeDecoderGetChannels(JNIEnv* env, jobject thiz, jlong decoderPtr) {
    ma_decoder* d = (ma_decoder*)(intptr_t)decoderPtr;
    return (jint)d->outputChannels;
}

JNIEXPORT jlong JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeDecoderGetTotalFrames(JNIEnv* env, jobject thiz, jlong decoderPtr) {
    ma_decoder* d = (ma_decoder*)(intptr_t)decoderPtr;
    ma_uint64 totalFrames;
    if (ma_decoder_get_length_in_pcm_frames(d, &totalFrames) != MA_SUCCESS) return 0;
    return (jlong)totalFrames;
}

JNIEXPORT jlong JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeDecoderReadFrames(JNIEnv* env, jobject thiz, jlong decoderPtr, jfloatArray output, jint frameCount) {
    ma_decoder* d = (ma_decoder*)(intptr_t)decoderPtr;
    jfloat* buf = (*env)->GetFloatArrayElements(env, output, NULL);
    ma_uint64 framesRead;
    ma_decoder_read_pcm_frames(d, buf, (ma_uint64)frameCount, &framesRead);
    (*env)->ReleaseFloatArrayElements(env, output, buf, 0);
    return (jlong)framesRead;
}

JNIEXPORT jboolean JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeDecoderSeek(JNIEnv* env, jobject thiz, jlong decoderPtr, jlong frameIndex) {
    ma_decoder* d = (ma_decoder*)(intptr_t)decoderPtr;
    return ma_decoder_seek_to_pcm_frame(d, (ma_uint64)frameIndex) == MA_SUCCESS ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeDecoderUninit(JNIEnv* env, jobject thiz, jlong decoderPtr) {
    ma_decoder* d = (ma_decoder*)(intptr_t)decoderPtr;
    if (d != NULL) { ma_decoder_uninit(d); free(d); }
}

/* ------------------------------------------------------------------ */
/* Encoder (AudioFileWriter)                                           */
/* ------------------------------------------------------------------ */

JNIEXPORT jlong JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeEncoderInitFile(
    JNIEnv* env, jobject thiz, jstring path, jint format, jint channels, jint sampleRate
) {
    (void)format; /* only WAV supported */
    const char* cpath = (*env)->GetStringUTFChars(env, path, NULL);
    ma_encoder* encoder = (ma_encoder*)malloc(sizeof(ma_encoder));
    ma_encoder_config config = ma_encoder_config_init(
        ma_encoding_format_wav, ma_format_f32,
        (ma_uint32)channels, (ma_uint32)sampleRate
    );
    ma_result result = ma_encoder_init_file(cpath, &config, encoder);
    (*env)->ReleaseStringUTFChars(env, path, cpath);
    if (result != MA_SUCCESS) { free(encoder); return 0; }
    return (jlong)(intptr_t)encoder;
}

JNIEXPORT jboolean JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeEncoderWriteFrames(
    JNIEnv* env, jobject thiz, jlong encoderPtr, jfloatArray data, jint frameCount
) {
    ma_encoder* enc = (ma_encoder*)(intptr_t)encoderPtr;
    jfloat* samples = (*env)->GetFloatArrayElements(env, data, NULL);
    ma_uint64 framesWritten;
    ma_result result = ma_encoder_write_pcm_frames(enc, samples, (ma_uint64)frameCount, &framesWritten);
    (*env)->ReleaseFloatArrayElements(env, data, samples, JNI_ABORT);
    return result == MA_SUCCESS ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeEncoderUninit(JNIEnv* env, jobject thiz, jlong encoderPtr) {
    ma_encoder* enc = (ma_encoder*)(intptr_t)encoderPtr;
    if (enc != NULL) { ma_encoder_uninit(enc); free(enc); }
}
