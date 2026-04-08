#include <jni.h>
#include <android/log.h>
#include <mutex>
#include <unordered_map>
#include "KoboeEngine.h"

#define LOG_TAG "koboe_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// ─── Global registry: stream handle -> owning engine ────────────────────────
// The Kotlin JniBridge API only passes engineHandle for openStream.
// For all other stream operations, we need to look up which engine owns
// the stream. This global map provides that mapping.

static std::mutex g_registryMutex;
static std::unordered_map<jlong, KoboeEngine*> g_streamToEngine;

static void registerStream(jlong streamHandle, KoboeEngine* engine) {
    std::lock_guard<std::mutex> lock(g_registryMutex);
    g_streamToEngine[streamHandle] = engine;
}

static void unregisterStream(jlong streamHandle) {
    std::lock_guard<std::mutex> lock(g_registryMutex);
    g_streamToEngine.erase(streamHandle);
}

static KoboeEngine* findEngine(jlong streamHandle) {
    std::lock_guard<std::mutex> lock(g_registryMutex);
    auto it = g_streamToEngine.find(streamHandle);
    if (it != g_streamToEngine.end()) {
        return it->second;
    }
    return nullptr;
}

extern "C" {

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* /* reserved */) {
    LOGI("koboe-jni loaded");
    return JNI_VERSION_1_6;
}

// ─── Engine lifecycle ───────────────────────────────────────────────────────

JNIEXPORT jlong JNICALL
Java_com_vectencia_koboe_JniBridge_nativeCreateEngine(JNIEnv* /* env */, jobject /* thiz */) {
    auto* engine = new KoboeEngine();
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT void JNICALL
Java_com_vectencia_koboe_JniBridge_nativeDestroyEngine(JNIEnv* /* env */, jobject /* thiz */, jlong engineHandle) {
    auto* engine = reinterpret_cast<KoboeEngine*>(engineHandle);
    if (engine) {
        // Unregister all streams belonging to this engine
        {
            std::lock_guard<std::mutex> lock(g_registryMutex);
            for (auto it = g_streamToEngine.begin(); it != g_streamToEngine.end(); ) {
                if (it->second == engine) {
                    it = g_streamToEngine.erase(it);
                } else {
                    ++it;
                }
            }
        }
        delete engine;
    }
}

// ─── Stream lifecycle ───────────────────────────────────────────────────────

JNIEXPORT jlong JNICALL
Java_com_vectencia_koboe_JniBridge_nativeOpenStream(
    JNIEnv* env, jobject /* thiz */,
    jlong engineHandle,
    jint sampleRate, jint channelCount, jint audioFormat,
    jint bufferCapacityInFrames, jint performanceMode,
    jint sharingMode, jint direction,
    jobject callbackObj) {

    auto* engine = reinterpret_cast<KoboeEngine*>(engineHandle);
    if (!engine) return 0;

    jlong streamHandle = engine->openStream(
        env, sampleRate, channelCount, audioFormat,
        bufferCapacityInFrames, performanceMode,
        sharingMode, direction, callbackObj);

    if (streamHandle != 0) {
        registerStream(streamHandle, engine);
    }
    return streamHandle;
}

JNIEXPORT void JNICALL
Java_com_vectencia_koboe_JniBridge_nativeStartStream(JNIEnv* /* env */, jobject /* thiz */, jlong streamHandle) {
    auto* engine = findEngine(streamHandle);
    if (engine) {
        engine->startStream(streamHandle);
    }
}

JNIEXPORT void JNICALL
Java_com_vectencia_koboe_JniBridge_nativePauseStream(JNIEnv* /* env */, jobject /* thiz */, jlong streamHandle) {
    auto* engine = findEngine(streamHandle);
    if (engine) {
        engine->pauseStream(streamHandle);
    }
}

JNIEXPORT void JNICALL
Java_com_vectencia_koboe_JniBridge_nativeStopStream(JNIEnv* /* env */, jobject /* thiz */, jlong streamHandle) {
    auto* engine = findEngine(streamHandle);
    if (engine) {
        engine->stopStream(streamHandle);
    }
}

JNIEXPORT void JNICALL
Java_com_vectencia_koboe_JniBridge_nativeCloseStream(JNIEnv* /* env */, jobject /* thiz */, jlong streamHandle) {
    auto* engine = findEngine(streamHandle);
    if (engine) {
        engine->closeStream(streamHandle);
        unregisterStream(streamHandle);
    }
}

// ─── Stream I/O ─────────────────────────────────────────────────────────────

JNIEXPORT jint JNICALL
Java_com_vectencia_koboe_JniBridge_nativeWriteStream(
    JNIEnv* env, jobject /* thiz */,
    jlong streamHandle, jfloatArray data, jint numFrames, jlong timeoutNanos) {

    auto* engine = findEngine(streamHandle);
    if (!engine) return -1;

    jfloat* nativeData = env->GetFloatArrayElements(data, nullptr);
    if (!nativeData) return -1;

    int result = engine->writeStream(streamHandle, nativeData, numFrames, timeoutNanos);

    env->ReleaseFloatArrayElements(data, nativeData, JNI_ABORT);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_vectencia_koboe_JniBridge_nativeReadStream(
    JNIEnv* env, jobject /* thiz */,
    jlong streamHandle, jfloatArray data, jint numFrames, jlong timeoutNanos) {

    auto* engine = findEngine(streamHandle);
    if (!engine) return -1;

    jfloat* nativeData = env->GetFloatArrayElements(data, nullptr);
    if (!nativeData) return -1;

    int result = engine->readStream(streamHandle, nativeData, numFrames, timeoutNanos);

    // Commit changes back to the Java array (input data was written by native)
    env->ReleaseFloatArrayElements(data, nativeData, 0);
    return result;
}

// ─── Stream properties ──────────────────────────────────────────────────────

JNIEXPORT jint JNICALL
Java_com_vectencia_koboe_JniBridge_nativeGetStreamState(JNIEnv* /* env */, jobject /* thiz */, jlong streamHandle) {
    auto* engine = findEngine(streamHandle);
    if (!engine) return 0; // UNINITIALIZED
    return engine->getStreamState(streamHandle);
}

JNIEXPORT jdouble JNICALL
Java_com_vectencia_koboe_JniBridge_nativeGetOutputLatencyMs(JNIEnv* /* env */, jobject /* thiz */, jlong streamHandle) {
    auto* engine = findEngine(streamHandle);
    if (!engine) return 0.0;
    return engine->getOutputLatencyMs(streamHandle);
}

JNIEXPORT jdouble JNICALL
Java_com_vectencia_koboe_JniBridge_nativeGetInputLatencyMs(JNIEnv* /* env */, jobject /* thiz */, jlong streamHandle) {
    auto* engine = findEngine(streamHandle);
    if (!engine) return 0.0;
    return engine->getInputLatencyMs(streamHandle);
}

// ─── Device enumeration (placeholder — requires Android Context) ────────────

JNIEXPORT jint JNICALL
Java_com_vectencia_koboe_JniBridge_nativeGetDeviceCount(JNIEnv* /* env */, jobject /* thiz */) {
    // Placeholder: device enumeration requires Android Context / AudioManager
    return 0;
}

JNIEXPORT jintArray JNICALL
Java_com_vectencia_koboe_JniBridge_nativeGetDeviceInfo(JNIEnv* env, jobject /* thiz */, jint /* index */) {
    // Return empty array [id=0, isInput=0, isOutput=0]
    jintArray result = env->NewIntArray(3);
    jint fill[3] = {0, 0, 0};
    env->SetIntArrayRegion(result, 0, 3, fill);
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_vectencia_koboe_JniBridge_nativeGetDeviceName(JNIEnv* env, jobject /* thiz */, jint /* index */) {
    return env->NewStringUTF("Unknown");
}

JNIEXPORT jintArray JNICALL
Java_com_vectencia_koboe_JniBridge_nativeGetDeviceSampleRates(JNIEnv* env, jobject /* thiz */, jint /* index */) {
    jintArray result = env->NewIntArray(0);
    return result;
}

JNIEXPORT jintArray JNICALL
Java_com_vectencia_koboe_JniBridge_nativeGetDeviceChannelCounts(JNIEnv* env, jobject /* thiz */, jint /* index */) {
    jintArray result = env->NewIntArray(0);
    return result;
}

} // extern "C"
