#pragma once

#include <jni.h>
#include <oboe/Oboe.h>
#include <memory>

/**
 * Bridges Oboe's audio callbacks to Kotlin's AudioStreamCallback via JNI.
 *
 * If a Kotlin callback is provided, onAudioReady crosses JNI to call
 * AudioStreamCallback.onAudioReady(). Otherwise, fills the buffer with silence.
 */
class KoboeCallback : public oboe::AudioStreamDataCallback,
                      public oboe::AudioStreamErrorCallback {
public:
    /**
     * @param env    JNI environment (used to obtain a global ref and cache method IDs)
     * @param callback  Kotlin AudioStreamCallback object, or nullptr for silence
     */
    KoboeCallback(JNIEnv* env, jobject callback);
    ~KoboeCallback() override;

    // oboe::AudioStreamDataCallback
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* stream,
        void* audioData,
        int32_t numFrames) override;

    // oboe::AudioStreamErrorCallback
    void onErrorBeforeClose(oboe::AudioStream* stream, oboe::Result error) override;
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

private:
    JavaVM* jvm_ = nullptr;
    jobject callbackRef_ = nullptr;   // global ref to Kotlin callback
    jmethodID onAudioReadyMethod_ = nullptr;
    int32_t channelCount_ = 0;
};
