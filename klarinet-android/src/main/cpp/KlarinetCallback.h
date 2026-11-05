#pragma once

#include <jni.h>
#include <oboe/Oboe.h>
#include <atomic>
#include <condition_variable>
#include <memory>
#include <mutex>
#include <thread>
#include <vector>

#include "AudioFifo.h"
#include "EffectChain.h"

/**
 * Bridges Oboe's audio callbacks to Kotlin's AudioStreamCallback.
 *
 * The Oboe data callback thread never enters the JVM. When a Kotlin callback is
 * provided, a dedicated worker thread calls onAudioReady() and exchanges
 * samples through a lock-free SPSC FIFO. Native effect processing stays
 * on the audio thread. Routing changes run on a separate Oboe thread and are
 * forwarded to Kotlin.
 */
class KlarinetCallback : public oboe::AudioStreamDataCallback,
                      public oboe::AudioStreamErrorCallback,
                      public oboe::AudioStreamRoutingCallback {
public:
    /**
     * @param env    JNI environment (used to obtain a global ref and cache method IDs)
     * @param callback  Kotlin AudioStreamCallback object, or nullptr for silence
     */
    KlarinetCallback(JNIEnv* env, jobject callback);
    ~KlarinetCallback() override;

    void prepare(int32_t framesPerBurst, int32_t channelCount, oboe::Direction direction);

    /**
     * Device ids reported by the stream after open. The next routing change
     * uses this list as the previous route.
     */
    void noteRoutedDevices(const int32_t* deviceIds, int32_t numDevices);

    // oboe::AudioStreamDataCallback
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* stream,
        void* audioData,
        int32_t numFrames) override;

    // oboe::AudioStreamErrorCallback
    void onErrorBeforeClose(oboe::AudioStream* stream, oboe::Result error) override;
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

    // oboe::AudioStreamRoutingCallback. Runs on an Oboe thread, not the data callback.
    void onRoutingChanged(
        oboe::AudioStream* stream,
        const int32_t* deviceIds,
        int32_t numDevices) override;

    void setEffectChain(klarinet::EffectChain* chain) {
        effectChain_.store(chain, std::memory_order_release);
    }

private:
    void stopWorker();
    void workerLoop();
    void waitForWork();
    bool hasWork() const;

    JavaVM* jvm_ = nullptr;
    jobject callbackRef_ = nullptr;
    jmethodID onAudioReadyMethod_ = nullptr;
    jmethodID notifyXrunMethod_ = nullptr;

    oboe::Direction direction_ = oboe::Direction::Output;
    int32_t framesPerBurst_ = 0;
    int32_t channelCount_ = 0;

    std::unique_ptr<klarinet::AudioFifo> fifo_;
    std::atomic<klarinet::EffectChain*> effectChain_{nullptr};
    std::atomic<int> xrunCount_{0};
    int lastReportedXruns_ = 0;

    std::atomic<bool> running_{false};
    std::thread worker_;
    std::mutex mu_;
    std::condition_variable cv_;

    std::mutex routeMu_;
    std::vector<int32_t> routedDeviceIds_;
};
