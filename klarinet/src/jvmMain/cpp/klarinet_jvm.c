#include <jni.h>
#include <stdatomic.h>
#include <stdint.h>
#include <string.h>
#include <stdlib.h>
#include "miniaudio.h"
#include "klarinet_dsp.h"

/* ------------------------------------------------------------------ */
/* Callback bridging                                                   */
/* ------------------------------------------------------------------ */

typedef struct {
    JavaVM*   jvm;
    jobject   callbackObj;
    jmethodID onAudioReadyMethod;
} JvmUserAudio;

typedef struct {
    JavaVM*     jvm;
    jobject     callbackObj;
    jmethodID   onAudioReadyMethod;
    ma_device   device;
    ma_uint32   channelCount;
    _Atomic(KlarinetEffectChainHandle) effectChain;
    KlarinetOffloadHandle offload;
    JvmUserAudio userAudio;
} KlarinetDevice;

static KlarinetEffectChainHandle device_chain(KlarinetDevice* kd) {
    return atomic_load(&kd->effectChain);
}

static int jvm_user_audio(void* userData, float* buffer, int numFrames, int channelCount) {
    JvmUserAudio* user = (JvmUserAudio*)userData;
    if (user == NULL || user->jvm == NULL || user->callbackObj == NULL) return 0;

    JNIEnv* env = NULL;
    if ((*user->jvm)->GetEnv(user->jvm, (void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
        if ((*user->jvm)->AttachCurrentThreadAsDaemon(user->jvm, (void**)&env, NULL) != JNI_OK) {
            return 0;
        }
    }
    if (env == NULL) return 0;

    const int totalSamples = numFrames * channelCount;
    jfloatArray jbuffer = (*env)->NewFloatArray(env, (jsize)totalSamples);
    if (jbuffer == NULL) return 0;
    (*env)->SetFloatArrayRegion(env, jbuffer, 0, (jsize)totalSamples, buffer);
    const jint frames = (*env)->CallIntMethod(
        env, user->callbackObj, user->onAudioReadyMethod, jbuffer, (jint)numFrames);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        (*env)->DeleteLocalRef(env, jbuffer);
        return 0;
    }
    if (frames > 0) {
        const int outSamples = frames * channelCount;
        (*env)->GetFloatArrayRegion(env, jbuffer, 0, (jsize)outSamples, buffer);
    }
    (*env)->DeleteLocalRef(env, jbuffer);
    return frames;
}

static void data_callback(ma_device* pDevice, void* pOutput, const void* pInput, ma_uint32 frameCount) {
    KlarinetDevice* kd = (KlarinetDevice*)pDevice->pUserData;
    if (kd == NULL) return;

    KlarinetEffectChainHandle chain = device_chain(kd);
    if (pDevice->type == ma_device_type_capture && pInput != NULL) {
        const int totalSamples = (int)frameCount * (int)kd->channelCount;
        float work[8192];
        float* samples = (float*)pInput;
        if (chain != NULL && totalSamples <= 8192) {
            memcpy(work, pInput, (size_t)totalSamples * sizeof(float));
            klarinet_chain_process(chain, work, (int)frameCount, (int)kd->channelCount);
            samples = work;
        }
        if (kd->offload != NULL) {
            klarinet_offload_process(kd->offload, samples, (int)frameCount);
        }
    } else if (pOutput != NULL) {
        if (kd->offload != NULL) {
            klarinet_offload_process(kd->offload, (float*)pOutput, (int)frameCount);
        } else {
            memset(pOutput, 0, (size_t)frameCount * kd->channelCount * sizeof(float));
        }
        if (chain != NULL) {
            klarinet_chain_process(chain, (float*)pOutput, (int)frameCount, (int)kd->channelCount);
        }
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

static int apply_miniaudio_device_id(ma_context* ctx, ma_device_config* config, int direction, int deviceId) {
    if (deviceId < 0) return 0;

    ma_device_info* playbackInfos = NULL;
    ma_uint32 playbackCount = 0;
    ma_device_info* captureInfos = NULL;
    ma_uint32 captureCount = 0;
    if (ma_context_get_devices(ctx, &playbackInfos, &playbackCount, &captureInfos, &captureCount) != MA_SUCCESS) {
        return -1;
    }

    if (direction == 0) {
        if ((ma_uint32)deviceId >= playbackCount) return -1;
        config->playback.pDeviceID = &playbackInfos[deviceId].id;
    } else {
        int captureIndex = deviceId - (int)playbackCount;
        if (captureIndex < 0 || (ma_uint32)captureIndex >= captureCount) return -1;
        config->capture.pDeviceID = &captureInfos[captureIndex].id;
    }
    return 0;
}

JNIEXPORT jlong JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeDeviceInit(
    JNIEnv* env, jobject thiz, jlong contextPtr,
    jint sampleRate, jint channelCount, jint bufferCapacityInFrames,
    jint direction, jint deviceId, jobject callbackObj
) {
    ma_context* ctx = (ma_context*)(intptr_t)contextPtr;
    KlarinetDevice* kd = (KlarinetDevice*)calloc(1, sizeof(KlarinetDevice));
    if (kd == NULL) return 0;

    kd->channelCount = (ma_uint32)channelCount;
    atomic_init(&kd->effectChain, NULL);

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

    if (apply_miniaudio_device_id(ctx, &config, direction, deviceId) != 0) {
        free(kd);
        return 0;
    }

    if (callbackObj != NULL) {
        (*env)->GetJavaVM(env, &kd->jvm);
        kd->callbackObj = (*env)->NewGlobalRef(env, callbackObj);

        jclass cbClass = (*env)->GetObjectClass(env, callbackObj);
        kd->onAudioReadyMethod = (*env)->GetMethodID(env, cbClass, "onAudioReady", "([FI)I");
        (*env)->DeleteLocalRef(env, cbClass);

        kd->userAudio.jvm = kd->jvm;
        kd->userAudio.callbackObj = kd->callbackObj;
        kd->userAudio.onAudioReadyMethod = kd->onAudioReadyMethod;
        int burst = bufferCapacityInFrames > 0 ? bufferCapacityInFrames : 256;
        kd->offload = klarinet_offload_create(
            burst, channelCount, direction == 0 ? 0 : 1, jvm_user_audio, &kd->userAudio);

        config.dataCallback = data_callback;
        config.pUserData    = kd;
    }

    ma_result result = ma_device_init(ctx, &config, &kd->device);
    if (result != MA_SUCCESS) {
        if (kd->offload != NULL) klarinet_offload_destroy(kd->offload);
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
        if (kd->offload != NULL) klarinet_offload_destroy(kd->offload);
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

/* ------------------------------------------------------------------ */
/* Effects / chains                                                    */
/* ------------------------------------------------------------------ */

JNIEXPORT jlong JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeCreateEffect(JNIEnv* env, jobject thiz, jint effectType) {
    (void)env; (void)thiz;
    return (jlong)(intptr_t)klarinet_create_effect((int)effectType);
}

JNIEXPORT void JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeDestroyEffect(JNIEnv* env, jobject thiz, jlong effectHandle) {
    (void)env; (void)thiz;
    klarinet_effect_destroy((KlarinetEffectHandle)(intptr_t)effectHandle);
}

JNIEXPORT void JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeSetEffectParameter(
    JNIEnv* env, jobject thiz, jlong effectHandle, jint paramId, jfloat value
) {
    (void)env; (void)thiz;
    klarinet_effect_set_parameter((KlarinetEffectHandle)(intptr_t)effectHandle, (int)paramId, value);
}

JNIEXPORT jfloat JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeGetEffectParameter(
    JNIEnv* env, jobject thiz, jlong effectHandle, jint paramId
) {
    (void)env; (void)thiz;
    return klarinet_effect_get_parameter((KlarinetEffectHandle)(intptr_t)effectHandle, (int)paramId);
}

JNIEXPORT void JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeSetEffectEnabled(
    JNIEnv* env, jobject thiz, jlong effectHandle, jboolean enabled
) {
    (void)env; (void)thiz;
    klarinet_effect_set_enabled((KlarinetEffectHandle)(intptr_t)effectHandle, enabled ? 1 : 0);
}

JNIEXPORT jboolean JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeIsEffectEnabled(JNIEnv* env, jobject thiz, jlong effectHandle) {
    (void)env; (void)thiz;
    return klarinet_effect_is_enabled((KlarinetEffectHandle)(intptr_t)effectHandle) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeEffectPrepare(
    JNIEnv* env, jobject thiz, jlong effectHandle, jint sampleRate, jint channelCount
) {
    (void)env; (void)thiz;
    klarinet_effect_prepare(
        (KlarinetEffectHandle)(intptr_t)effectHandle, (int)sampleRate, (int)channelCount);
}

JNIEXPORT jlong JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeCreateEffectChain(JNIEnv* env, jobject thiz) {
    (void)env; (void)thiz;
    return (jlong)(intptr_t)klarinet_chain_create();
}

JNIEXPORT void JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeDestroyEffectChain(JNIEnv* env, jobject thiz, jlong chainHandle) {
    (void)env; (void)thiz;
    klarinet_chain_destroy((KlarinetEffectChainHandle)(intptr_t)chainHandle);
}

JNIEXPORT void JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeChainAddEffect(
    JNIEnv* env, jobject thiz, jlong chainHandle, jlong effectHandle
) {
    (void)env; (void)thiz;
    klarinet_chain_add(
        (KlarinetEffectChainHandle)(intptr_t)chainHandle,
        (KlarinetEffectHandle)(intptr_t)effectHandle);
}

JNIEXPORT void JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeChainRemoveEffect(
    JNIEnv* env, jobject thiz, jlong chainHandle, jlong effectHandle
) {
    (void)env; (void)thiz;
    klarinet_chain_remove(
        (KlarinetEffectChainHandle)(intptr_t)chainHandle,
        (KlarinetEffectHandle)(intptr_t)effectHandle);
}

JNIEXPORT void JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeChainClear(JNIEnv* env, jobject thiz, jlong chainHandle) {
    (void)env; (void)thiz;
    klarinet_chain_clear((KlarinetEffectChainHandle)(intptr_t)chainHandle);
}

JNIEXPORT void JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeChainPrepare(
    JNIEnv* env, jobject thiz, jlong chainHandle, jint sampleRate, jint channelCount
) {
    (void)env; (void)thiz;
    klarinet_chain_prepare(
        (KlarinetEffectChainHandle)(intptr_t)chainHandle, (int)sampleRate, (int)channelCount);
}

JNIEXPORT jint JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeChainGetEffectCount(JNIEnv* env, jobject thiz, jlong chainHandle) {
    (void)env; (void)thiz;
    return (jint)klarinet_chain_get_effect_count((KlarinetEffectChainHandle)(intptr_t)chainHandle);
}

JNIEXPORT void JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeChainEnqueueParam(
    JNIEnv* env, jobject thiz, jlong chainHandle, jlong effectHandle, jint paramId, jfloat value
) {
    (void)env; (void)thiz;
    klarinet_chain_enqueue_param(
        (KlarinetEffectChainHandle)(intptr_t)chainHandle,
        (KlarinetEffectHandle)(intptr_t)effectHandle,
        (int)paramId,
        value);
}

JNIEXPORT void JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeChainProcess(
    JNIEnv* env, jobject thiz, jlong chainHandle, jfloatArray data, jint numFrames, jint channelCount
) {
    if (data == NULL) return;
    jfloat* samples = (*env)->GetFloatArrayElements(env, data, NULL);
    if (samples == NULL) return;
    klarinet_chain_process(
        (KlarinetEffectChainHandle)(intptr_t)chainHandle,
        samples,
        (int)numFrames,
        (int)channelCount);
    (*env)->ReleaseFloatArrayElements(env, data, samples, 0);
}

JNIEXPORT void JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeSetDeviceEffectChain(
    JNIEnv* env, jobject thiz, jlong devicePtr, jlong chainHandle
) {
    (void)env; (void)thiz;
    KlarinetDevice* kd = (KlarinetDevice*)(intptr_t)devicePtr;
    if (kd != NULL) {
        atomic_store(&kd->effectChain, (KlarinetEffectChainHandle)(intptr_t)chainHandle);
    }
}

JNIEXPORT void JNICALL
Java_com_vectencia_klarinet_JniBridge_nativeClearDeviceEffectChain(JNIEnv* env, jobject thiz, jlong devicePtr) {
    (void)env; (void)thiz;
    KlarinetDevice* kd = (KlarinetDevice*)(intptr_t)devicePtr;
    if (kd != NULL) {
        atomic_store(&kd->effectChain, NULL);
    }
}
