#pragma once

#include <oboe/Oboe.h>
#include <memory>
#include <unordered_map>
#include <mutex>
#include <jni.h>

#include "KlarinetCallback.h"
#include "EffectFactory.h"
#include "EffectChain.h"

/**
 * Manages Oboe audio stream creation and lifecycle.
 *
 * Each KlarinetEngine instance holds a collection of open streams.
 * Streams are identified by their pointer address cast to a handle.
 */
class KlarinetEngine {
public:
    KlarinetEngine() = default;
    ~KlarinetEngine();

    /**
     * Open a new audio stream.
     *
     * @param env JNI environment
     * @param sampleRate Desired sample rate (0 = device default)
     * @param channelCount Number of channels
     * @param audioFormat Klarinet AudioFormat ordinal (0=Float, 1=I16, 2=I24, 3=I32)
     * @param bufferCapacityInFrames Buffer capacity (0 = device default)
     * @param performanceMode Klarinet PerformanceMode ordinal (0=None, 1=LowLatency, 2=PowerSaving)
     * @param sharingMode Klarinet SharingMode ordinal (0=Shared, 1=Exclusive)
     * @param direction Klarinet StreamDirection ordinal (0=Output, 1=Input)
     * @param callback Kotlin AudioStreamCallback or nullptr
     * @return Stream handle (pointer cast to long), or 0 on failure
     */
    jlong openStream(
        JNIEnv* env,
        jint sampleRate,
        jint channelCount,
        jint audioFormat,
        jint bufferCapacityInFrames,
        jint performanceMode,
        jint sharingMode,
        jint direction,
        jobject callback);

    void startStream(jlong streamHandle);
    void pauseStream(jlong streamHandle);
    void stopStream(jlong streamHandle);
    void closeStream(jlong streamHandle);

    int writeStream(jlong streamHandle, const float* data, int32_t numFrames, int64_t timeoutNanos);
    int readStream(jlong streamHandle, float* data, int32_t numFrames, int64_t timeoutNanos);

    int getStreamState(jlong streamHandle);
    double getOutputLatencyMs(jlong streamHandle);
    double getInputLatencyMs(jlong streamHandle);

    // --- Effects ---
    jlong createEffect(int effectType);
    void destroyEffect(jlong effectHandle);
    void setEffectParameter(jlong effectHandle, int paramId, float value);
    float getEffectParameter(jlong effectHandle, int paramId);
    void setEffectEnabled(jlong effectHandle, bool enabled);
    bool isEffectEnabled(jlong effectHandle);

    // --- Effect chains ---
    jlong createEffectChain();
    void destroyEffectChain(jlong chainHandle);
    void chainAddEffect(jlong chainHandle, jlong effectHandle);
    void chainRemoveEffect(jlong chainHandle, jlong effectHandle);
    void chainClear(jlong chainHandle);
    void chainPrepare(jlong chainHandle, int sampleRate, int channelCount);
    void chainEnqueueParam(jlong chainHandle, jlong effectHandle, int paramId, float value);
    int chainGetEffectCount(jlong chainHandle);
    void setStreamEffectChain(jlong streamHandle, jlong chainHandle);
    void clearStreamEffectChain(jlong streamHandle);

private:
    struct StreamEntry {
        std::shared_ptr<oboe::AudioStream> stream;
        std::unique_ptr<KlarinetCallback> callback;
    };

    std::mutex mutex_;
    std::unordered_map<jlong, StreamEntry> streams_;
    std::unordered_map<jlong, std::shared_ptr<klarinet::AudioEffect>> effects_;
    std::unordered_map<jlong, std::unique_ptr<klarinet::EffectChain>> chains_;

    oboe::AudioStream* getStream(jlong handle);

    static oboe::AudioFormat toOboeFormat(jint format);
    static oboe::PerformanceMode toOboePerformanceMode(jint mode);
    static oboe::SharingMode toOboeSharingMode(jint mode);
    static oboe::Direction toOboeDirection(jint direction);
    static int toKlarinetStreamState(oboe::StreamState state);
};
