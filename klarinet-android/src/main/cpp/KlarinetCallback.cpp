#include "KlarinetCallback.h"

#include <android/log.h>
#include <pthread.h>

#include <algorithm>
#include <chrono>
#include <cstring>
#include <vector>

#define LOG_TAG "KlarinetCallback"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

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
    stopWorker();
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
        callbackRef_ = nullptr;
    }
}

void KlarinetCallback::prepare(
    int32_t framesPerBurst,
    int32_t channelCount,
    oboe::Direction direction) {
    stopWorker();
    framesPerBurst_ = framesPerBurst > 0 ? framesPerBurst : 192;
    channelCount_ = channelCount > 0 ? channelCount : 1;
    direction_ = direction;
    const int32_t samplesPerBurst = framesPerBurst_ * channelCount_;
    fifo_ = std::make_unique<klarinet::AudioFifo>(samplesPerBurst * 8);
    if (callbackRef_ != nullptr && onAudioReadyMethod_ != nullptr && jvm_ != nullptr) {
        running_.store(true, std::memory_order_release);
        worker_ = std::thread(&KlarinetCallback::workerLoop, this);
    }
}

void KlarinetCallback::stopWorker() {
    running_.store(false, std::memory_order_release);
    cv_.notify_all();
    if (worker_.joinable()) {
        worker_.join();
    }
}

bool KlarinetCallback::hasWork() const {
    if (fifo_ == nullptr) return false;
    const int32_t needed = framesPerBurst_ * channelCount_;
    if (direction_ == oboe::Direction::Output) {
        return fifo_->availableToWrite() >= needed;
    }
    return fifo_->availableToRead() >= needed;
}

void KlarinetCallback::waitForWork() {
    std::unique_lock<std::mutex> lock(mu_);
    cv_.wait_for(lock, std::chrono::milliseconds(2), [this] {
        return !running_.load(std::memory_order_acquire) || hasWork();
    });
}

void KlarinetCallback::workerLoop() {
#if defined(__ANDROID__)
    pthread_setname_np(pthread_self(), "klarinet-jni");
#endif
    JNIEnv* env = nullptr;
    if (jvm_->AttachCurrentThread(&env, nullptr) != JNI_OK) {
        LOGE("Failed to attach Kotlin audio worker");
        return;
    }

    const int32_t totalSamples = framesPerBurst_ * channelCount_;
    jfloatArray localBuf = env->NewFloatArray(totalSamples);
    if (localBuf == nullptr) {
        LOGE("Failed to allocate reusable JNI float array");
        jvm_->DetachCurrentThread();
        return;
    }
    auto globalBuf = reinterpret_cast<jfloatArray>(env->NewGlobalRef(localBuf));
    env->DeleteLocalRef(localBuf);
    if (globalBuf == nullptr) {
        jvm_->DetachCurrentThread();
        return;
    }

    std::vector<float> scratch(static_cast<size_t>(totalSamples), 0.0f);

    while (running_.load(std::memory_order_acquire)) {
        if (!hasWork()) {
            waitForWork();
            continue;
        }

        if (direction_ == oboe::Direction::Output) {
            const jint framesProcessed = env->CallIntMethod(
                callbackRef_, onAudioReadyMethod_, globalBuf, framesPerBurst_);
            if (env->ExceptionCheck()) {
                env->ExceptionDescribe();
                env->ExceptionClear();
                LOGE("Exception in onAudioReady callback");
                continue;
            }
            if (framesProcessed <= 0) continue;
            const int32_t samples = std::min(framesProcessed, framesPerBurst_) * channelCount_;
            env->GetFloatArrayRegion(globalBuf, 0, samples, scratch.data());
            int32_t written = 0;
            while (written < samples && running_.load(std::memory_order_acquire)) {
                const int32_t n = fifo_->write(scratch.data() + written, samples - written);
                if (n == 0) {
                    waitForWork();
                } else {
                    written += n;
                }
            }
        } else {
            const int32_t got = fifo_->read(scratch.data(), totalSamples);
            if (got < totalSamples) {
                waitForWork();
                continue;
            }
            env->SetFloatArrayRegion(globalBuf, 0, totalSamples, scratch.data());
            env->CallIntMethod(callbackRef_, onAudioReadyMethod_, globalBuf, framesPerBurst_);
            if (env->ExceptionCheck()) {
                env->ExceptionDescribe();
                env->ExceptionClear();
                LOGE("Exception in onAudioReady callback");
            }
        }
    }

    env->DeleteGlobalRef(globalBuf);
    jvm_->DetachCurrentThread();
}

oboe::DataCallbackResult KlarinetCallback::onAudioReady(
    oboe::AudioStream* stream,
    void* audioData,
    int32_t numFrames) {

    auto* floatData = static_cast<float*>(audioData);
    const int32_t channelCount = stream->getChannelCount();
    const int32_t totalSamples = numFrames * channelCount;
    auto* chain = effectChain_.load(std::memory_order_acquire);

    if (stream->getDirection() == oboe::Direction::Input) {
        if (chain != nullptr) {
            chain->process(floatData, numFrames, channelCount);
        }
        if (fifo_ != nullptr && callbackRef_ != nullptr) {
            fifo_->write(floatData, totalSamples);
        }
    } else if (fifo_ != nullptr && callbackRef_ != nullptr) {
        const int32_t got = fifo_->read(floatData, totalSamples);
        if (got < totalSamples) {
            std::memset(floatData + got, 0, static_cast<size_t>(totalSamples - got) * sizeof(float));
        }
        if (chain != nullptr) {
            chain->process(floatData, numFrames, channelCount);
        }
    } else {
        std::memset(audioData, 0, static_cast<size_t>(totalSamples) * sizeof(float));
        if (chain != nullptr) {
            chain->process(floatData, numFrames, channelCount);
        }
    }

    cv_.notify_one();
    return oboe::DataCallbackResult::Continue;
}

void KlarinetCallback::onErrorBeforeClose(oboe::AudioStream* stream, oboe::Result error) {
    LOGE("Oboe error before close: %s", oboe::convertToText(error));
}

void KlarinetCallback::onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) {
    LOGE("Oboe error after close: %s", oboe::convertToText(error));
}
