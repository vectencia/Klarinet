#include "KlarinetCallback.h"
#include <android/log.h>
#include <cstring>

#define LOG_TAG "KlarinetCallback"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

KlarinetCallback::KlarinetCallback(JNIEnv* env, jobject callback) {
    if (callback != nullptr) {
        env->GetJavaVM(&jvm_);
        callbackRef_ = env->NewGlobalRef(callback);

        jclass cls = env->GetObjectClass(callback);
        onAudioReadyMethod_ = env->GetMethodID(cls, "onAudioReady", "([FI)I");
        if (onAudioReadyMethod_ == nullptr) {
            LOGE("Could not find onAudioReady method on callback");
        }
        env->DeleteLocalRef(cls);
    }
}

KlarinetCallback::~KlarinetCallback() {
    if (callbackRef_ != nullptr && jvm_ != nullptr) {
        JNIEnv* env = nullptr;
        bool attached = false;
        jint result = jvm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
        if (result == JNI_EDETACHED) {
            if (jvm_->AttachCurrentThread(&env, nullptr) == JNI_OK) {
                attached = true;
            }
        }
        if (env != nullptr) {
            env->DeleteGlobalRef(callbackRef_);
        }
        if (attached) {
            jvm_->DetachCurrentThread();
        }
    }
}

oboe::DataCallbackResult KlarinetCallback::onAudioReady(
    oboe::AudioStream* stream,
    void* audioData,
    int32_t numFrames) {

    auto* floatData = static_cast<float*>(audioData);
    int32_t channelCount = stream->getChannelCount();
    int32_t totalSamples = numFrames * channelCount;

    if (callbackRef_ == nullptr || jvm_ == nullptr || onAudioReadyMethod_ == nullptr) {
        // No callback — fill with silence
        std::memset(audioData, 0, totalSamples * sizeof(float));
        return oboe::DataCallbackResult::Continue;
    }

    JNIEnv* env = nullptr;
    bool attached = false;
    jint result = jvm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (result == JNI_EDETACHED) {
        if (jvm_->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            LOGE("Failed to attach thread in onAudioReady");
            std::memset(audioData, 0, totalSamples * sizeof(float));
            return oboe::DataCallbackResult::Continue;
        }
        attached = true;
    }

    // Create a float array and pass it to the Kotlin callback
    jfloatArray jBuffer = env->NewFloatArray(totalSamples);
    if (jBuffer == nullptr) {
        LOGE("Failed to allocate JNI float array");
        std::memset(audioData, 0, totalSamples * sizeof(float));
        if (attached) jvm_->DetachCurrentThread();
        return oboe::DataCallbackResult::Continue;
    }

    // For input streams, process effects BEFORE passing to Kotlin callback
    if (stream->getDirection() == oboe::Direction::Input) {
        if (effectChain_) {
            effectChain_->process(floatData, numFrames, channelCount);
        }
        env->SetFloatArrayRegion(jBuffer, 0, totalSamples, floatData);
    }

    jint framesProcessed = env->CallIntMethod(callbackRef_, onAudioReadyMethod_, jBuffer, numFrames);

    // For output streams, copy the callback's data back, then process effects
    if (stream->getDirection() == oboe::Direction::Output) {
        env->GetFloatArrayRegion(jBuffer, 0, totalSamples, floatData);
        if (effectChain_) {
            effectChain_->process(floatData, numFrames, channelCount);
        }
    }

    env->DeleteLocalRef(jBuffer);

    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        LOGE("Exception in onAudioReady callback");
        std::memset(audioData, 0, totalSamples * sizeof(float));
        if (attached) jvm_->DetachCurrentThread();
        return oboe::DataCallbackResult::Continue;
    }

    if (attached) {
        jvm_->DetachCurrentThread();
    }

    return (framesProcessed >= 0)
        ? oboe::DataCallbackResult::Continue
        : oboe::DataCallbackResult::Stop;
}

void KlarinetCallback::onErrorBeforeClose(oboe::AudioStream* stream, oboe::Result error) {
    LOGE("Oboe error before close: %s", oboe::convertToText(error));
}

void KlarinetCallback::onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) {
    LOGE("Oboe error after close: %s", oboe::convertToText(error));
}
