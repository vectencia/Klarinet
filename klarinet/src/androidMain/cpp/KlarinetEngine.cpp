#include "KlarinetEngine.h"
#include <android/log.h>

#define LOG_TAG "KlarinetEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

KlarinetEngine::~KlarinetEngine() {
    std::lock_guard<std::mutex> lock(mutex_);
    for (auto& [handle, entry] : streams_) {
        if (entry.stream) {
            entry.stream->stop();
            entry.stream->close();
        }
    }
    streams_.clear();
}

jlong KlarinetEngine::openStream(
    JNIEnv* env,
    jint sampleRate,
    jint channelCount,
    jint audioFormat,
    jint bufferCapacityInFrames,
    jint performanceMode,
    jint sharingMode,
    jint direction,
    jobject callback) {

    auto klarinetCallback = std::make_unique<KlarinetCallback>(env, callback);

    oboe::AudioStreamBuilder builder;
    builder.setDirection(toOboeDirection(direction));
    builder.setPerformanceMode(toOboePerformanceMode(performanceMode));
    builder.setSharingMode(toOboeSharingMode(sharingMode));
    builder.setFormat(toOboeFormat(audioFormat));
    builder.setChannelCount(channelCount);
    builder.setDataCallback(klarinetCallback.get());
    builder.setErrorCallback(klarinetCallback.get());

    if (sampleRate > 0) {
        builder.setSampleRate(sampleRate);
        // Enable sample rate conversion so Oboe resamples if the device
        // native rate differs from the requested rate (e.g., file at 22050 Hz,
        // device at 48000 Hz). Without this, audio plays at the wrong speed.
        builder.setSampleRateConversionQuality(
            oboe::SampleRateConversionQuality::Medium);
    }

    if (bufferCapacityInFrames > 0) {
        builder.setBufferCapacityInFrames(bufferCapacityInFrames);
    }

    std::shared_ptr<oboe::AudioStream> stream;
    oboe::Result result = builder.openStream(stream);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open stream: %s", oboe::convertToText(result));
        return 0;
    }

    jlong handle = reinterpret_cast<jlong>(stream.get());

    std::lock_guard<std::mutex> lock(mutex_);
    streams_[handle] = StreamEntry{std::move(stream), std::move(klarinetCallback)};

    LOGI("Opened stream: handle=%lld, sampleRate=%d, channels=%d",
         (long long)handle, streams_[handle].stream->getSampleRate(),
         streams_[handle].stream->getChannelCount());

    return handle;
}

void KlarinetEngine::startStream(jlong streamHandle) {
    auto* stream = getStream(streamHandle);
    if (stream) {
        oboe::Result result = stream->requestStart();
        if (result != oboe::Result::OK) {
            LOGE("Failed to start stream: %s", oboe::convertToText(result));
        }
    }
}

void KlarinetEngine::pauseStream(jlong streamHandle) {
    auto* stream = getStream(streamHandle);
    if (stream) {
        oboe::Result result = stream->requestPause();
        if (result != oboe::Result::OK) {
            LOGE("Failed to pause stream: %s", oboe::convertToText(result));
        }
    }
}

void KlarinetEngine::stopStream(jlong streamHandle) {
    auto* stream = getStream(streamHandle);
    if (stream) {
        oboe::Result result = stream->requestStop();
        if (result != oboe::Result::OK) {
            LOGE("Failed to stop stream: %s", oboe::convertToText(result));
        }
    }
}

void KlarinetEngine::closeStream(jlong streamHandle) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = streams_.find(streamHandle);
    if (it != streams_.end()) {
        if (it->second.stream) {
            it->second.stream->close();
        }
        streams_.erase(it);
    }
}

int KlarinetEngine::writeStream(jlong streamHandle, const float* data, int32_t numFrames, int64_t timeoutNanos) {
    auto* stream = getStream(streamHandle);
    if (!stream) return -1;

    auto result = stream->write(data, numFrames, timeoutNanos);
    if (!result) {
        LOGE("Write failed: %s", oboe::convertToText(result.error()));
        return static_cast<int>(result.error());
    }
    return result.value();
}

int KlarinetEngine::readStream(jlong streamHandle, float* data, int32_t numFrames, int64_t timeoutNanos) {
    auto* stream = getStream(streamHandle);
    if (!stream) return -1;

    auto result = stream->read(data, numFrames, timeoutNanos);
    if (!result) {
        LOGE("Read failed: %s", oboe::convertToText(result.error()));
        return static_cast<int>(result.error());
    }
    return result.value();
}

int KlarinetEngine::getStreamState(jlong streamHandle) {
    auto* stream = getStream(streamHandle);
    if (!stream) return 0; // UNINITIALIZED
    return toKlarinetStreamState(stream->getState());
}

double KlarinetEngine::getOutputLatencyMs(jlong streamHandle) {
    auto* stream = getStream(streamHandle);
    if (!stream) return 0.0;

    auto result = stream->calculateLatencyMillis();
    if (!result) return 0.0;
    return result.value();
}

double KlarinetEngine::getInputLatencyMs(jlong streamHandle) {
    auto* stream = getStream(streamHandle);
    if (!stream) return 0.0;

    auto result = stream->calculateLatencyMillis();
    if (!result) return 0.0;
    return result.value();
}

oboe::AudioStream* KlarinetEngine::getStream(jlong handle) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = streams_.find(handle);
    if (it == streams_.end()) {
        LOGE("Stream handle not found: %lld", (long long)handle);
        return nullptr;
    }
    return it->second.stream.get();
}

// --- Effect management ---

jlong KlarinetEngine::createEffect(int effectType) {
    auto effect = klarinet::EffectFactory::create(static_cast<klarinet::EffectType>(effectType));
    if (!effect) return 0;

    jlong handle = reinterpret_cast<jlong>(effect.get());
    std::lock_guard<std::mutex> lock(mutex_);
    effects_[handle] = std::move(effect);
    return handle;
}

void KlarinetEngine::destroyEffect(jlong effectHandle) {
    std::lock_guard<std::mutex> lock(mutex_);
    effects_.erase(effectHandle);
}

void KlarinetEngine::setEffectParameter(jlong effectHandle, int paramId, float value) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = effects_.find(effectHandle);
    if (it != effects_.end()) {
        it->second->setParameter(paramId, value);
    }
}

float KlarinetEngine::getEffectParameter(jlong effectHandle, int paramId) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = effects_.find(effectHandle);
    if (it != effects_.end()) {
        return it->second->getParameter(paramId);
    }
    return 0.0f;
}

void KlarinetEngine::setEffectEnabled(jlong effectHandle, bool enabled) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = effects_.find(effectHandle);
    if (it != effects_.end()) {
        it->second->setEnabled(enabled);
    }
}

bool KlarinetEngine::isEffectEnabled(jlong effectHandle) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = effects_.find(effectHandle);
    if (it != effects_.end()) {
        return it->second->isEnabled();
    }
    return false;
}

// --- Effect chain management ---

jlong KlarinetEngine::createEffectChain() {
    auto chain = std::make_unique<klarinet::EffectChain>();
    jlong handle = reinterpret_cast<jlong>(chain.get());
    std::lock_guard<std::mutex> lock(mutex_);
    chains_[handle] = std::move(chain);
    return handle;
}

void KlarinetEngine::destroyEffectChain(jlong chainHandle) {
    std::lock_guard<std::mutex> lock(mutex_);
    chains_.erase(chainHandle);
}

void KlarinetEngine::chainAddEffect(jlong chainHandle, jlong effectHandle) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto chainIt = chains_.find(chainHandle);
    auto effectIt = effects_.find(effectHandle);
    if (chainIt != chains_.end() && effectIt != effects_.end()) {
        chainIt->second->addEffect(effectIt->second);
    }
}

void KlarinetEngine::chainRemoveEffect(jlong chainHandle, jlong effectHandle) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto chainIt = chains_.find(chainHandle);
    auto effectIt = effects_.find(effectHandle);
    if (chainIt != chains_.end() && effectIt != effects_.end()) {
        chainIt->second->removeEffect(effectIt->second.get());
    }
}

void KlarinetEngine::chainClear(jlong chainHandle) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = chains_.find(chainHandle);
    if (it != chains_.end()) {
        it->second->clear();
    }
}

void KlarinetEngine::chainPrepare(jlong chainHandle, int sampleRate, int channelCount) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = chains_.find(chainHandle);
    if (it != chains_.end()) {
        it->second->prepare(sampleRate, channelCount);
    }
}

void KlarinetEngine::chainEnqueueParam(jlong chainHandle, jlong effectHandle, int paramId, float value) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto chainIt = chains_.find(chainHandle);
    auto effectIt = effects_.find(effectHandle);
    if (chainIt != chains_.end() && effectIt != effects_.end()) {
        chainIt->second->enqueueParameterChange(effectIt->second.get(), paramId, value);
    }
}

int KlarinetEngine::chainGetEffectCount(jlong chainHandle) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = chains_.find(chainHandle);
    if (it != chains_.end()) {
        return it->second->getEffectCount();
    }
    return 0;
}

void KlarinetEngine::setStreamEffectChain(jlong streamHandle, jlong chainHandle) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto streamIt = streams_.find(streamHandle);
    auto chainIt = chains_.find(chainHandle);
    if (streamIt != streams_.end() && chainIt != chains_.end() && streamIt->second.callback) {
        streamIt->second.callback->setEffectChain(chainIt->second.get());
    }
}

void KlarinetEngine::clearStreamEffectChain(jlong streamHandle) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto streamIt = streams_.find(streamHandle);
    if (streamIt != streams_.end() && streamIt->second.callback) {
        streamIt->second.callback->setEffectChain(nullptr);
    }
}

// --- Enum mapping helpers ---

oboe::AudioFormat KlarinetEngine::toOboeFormat(jint format) {
    switch (format) {
        case 0:  return oboe::AudioFormat::Float;    // PCM_FLOAT
        case 1:  return oboe::AudioFormat::I16;      // PCM_I16
        case 2:  return oboe::AudioFormat::I24;      // PCM_I24
        case 3:  return oboe::AudioFormat::I32;      // PCM_I32
        default: return oboe::AudioFormat::Float;
    }
}

oboe::PerformanceMode KlarinetEngine::toOboePerformanceMode(jint mode) {
    switch (mode) {
        case 0:  return oboe::PerformanceMode::None;         // NONE
        case 1:  return oboe::PerformanceMode::LowLatency;   // LOW_LATENCY
        case 2:  return oboe::PerformanceMode::PowerSaving;  // POWER_SAVING
        default: return oboe::PerformanceMode::None;
    }
}

oboe::SharingMode KlarinetEngine::toOboeSharingMode(jint mode) {
    switch (mode) {
        case 0:  return oboe::SharingMode::Shared;      // SHARED
        case 1:  return oboe::SharingMode::Exclusive;    // EXCLUSIVE
        default: return oboe::SharingMode::Shared;
    }
}

oboe::Direction KlarinetEngine::toOboeDirection(jint direction) {
    switch (direction) {
        case 0:  return oboe::Direction::Output;  // OUTPUT
        case 1:  return oboe::Direction::Input;   // INPUT
        default: return oboe::Direction::Output;
    }
}

/**
 * Maps Oboe StreamState to Klarinet StreamState ordinal.
 *
 * Klarinet StreamState enum order:
 *   0=UNINITIALIZED, 1=OPEN, 2=STARTING, 3=STARTED,
 *   4=PAUSING, 5=PAUSED, 6=STOPPING, 7=STOPPED,
 *   8=CLOSING, 9=CLOSED
 */
int KlarinetEngine::toKlarinetStreamState(oboe::StreamState state) {
    switch (state) {
        case oboe::StreamState::Uninitialized: return 0;
        case oboe::StreamState::Open:          return 1;
        case oboe::StreamState::Starting:      return 2;
        case oboe::StreamState::Started:       return 3;
        case oboe::StreamState::Pausing:       return 4;
        case oboe::StreamState::Paused:        return 5;
        case oboe::StreamState::Stopping:      return 6;
        case oboe::StreamState::Stopped:       return 7;
        case oboe::StreamState::Closing:       return 8;
        case oboe::StreamState::Closed:        return 9;
        default:                               return 0;
    }
}
