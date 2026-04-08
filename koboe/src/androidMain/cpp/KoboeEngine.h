#pragma once

#include <oboe/Oboe.h>
#include <memory>
#include <unordered_map>
#include <mutex>
#include <jni.h>

#include "KoboeCallback.h"

/**
 * Manages Oboe audio stream creation and lifecycle.
 *
 * Each KoboeEngine instance holds a collection of open streams.
 * Streams are identified by their pointer address cast to a handle.
 */
class KoboeEngine {
public:
    KoboeEngine() = default;
    ~KoboeEngine();

    /**
     * Open a new audio stream.
     *
     * @param env JNI environment
     * @param sampleRate Desired sample rate (0 = device default)
     * @param channelCount Number of channels
     * @param audioFormat Koboe AudioFormat ordinal (0=Float, 1=I16, 2=I24, 3=I32)
     * @param bufferCapacityInFrames Buffer capacity (0 = device default)
     * @param performanceMode Koboe PerformanceMode ordinal (0=None, 1=LowLatency, 2=PowerSaving)
     * @param sharingMode Koboe SharingMode ordinal (0=Shared, 1=Exclusive)
     * @param direction Koboe StreamDirection ordinal (0=Output, 1=Input)
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

private:
    struct StreamEntry {
        std::shared_ptr<oboe::AudioStream> stream;
        std::unique_ptr<KoboeCallback> callback;
    };

    std::mutex mutex_;
    std::unordered_map<jlong, StreamEntry> streams_;

    oboe::AudioStream* getStream(jlong handle);

    static oboe::AudioFormat toOboeFormat(jint format);
    static oboe::PerformanceMode toOboePerformanceMode(jint mode);
    static oboe::SharingMode toOboeSharingMode(jint mode);
    static oboe::Direction toOboeDirection(jint direction);
    static int toKoboeStreamState(oboe::StreamState state);
};
