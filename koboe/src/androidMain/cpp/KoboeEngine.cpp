#include "KoboeEngine.h"
#include <android/log.h>

#define LOG_TAG "KoboeEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

KoboeEngine::~KoboeEngine() {
    std::lock_guard<std::mutex> lock(mutex_);
    for (auto& [handle, entry] : streams_) {
        if (entry.stream) {
            entry.stream->stop();
            entry.stream->close();
        }
    }
    streams_.clear();
}

jlong KoboeEngine::openStream(
    JNIEnv* env,
    jint sampleRate,
    jint channelCount,
    jint audioFormat,
    jint bufferCapacityInFrames,
    jint performanceMode,
    jint sharingMode,
    jint direction,
    jobject callback) {

    auto koboeCallback = std::make_unique<KoboeCallback>(env, callback);

    oboe::AudioStreamBuilder builder;
    builder.setDirection(toOboeDirection(direction));
    builder.setPerformanceMode(toOboePerformanceMode(performanceMode));
    builder.setSharingMode(toOboeSharingMode(sharingMode));
    builder.setFormat(toOboeFormat(audioFormat));
    builder.setChannelCount(channelCount);
    builder.setDataCallback(koboeCallback.get());
    builder.setErrorCallback(koboeCallback.get());

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
    streams_[handle] = StreamEntry{std::move(stream), std::move(koboeCallback)};

    LOGI("Opened stream: handle=%lld, sampleRate=%d, channels=%d",
         (long long)handle, streams_[handle].stream->getSampleRate(),
         streams_[handle].stream->getChannelCount());

    return handle;
}

void KoboeEngine::startStream(jlong streamHandle) {
    auto* stream = getStream(streamHandle);
    if (stream) {
        oboe::Result result = stream->requestStart();
        if (result != oboe::Result::OK) {
            LOGE("Failed to start stream: %s", oboe::convertToText(result));
        }
    }
}

void KoboeEngine::pauseStream(jlong streamHandle) {
    auto* stream = getStream(streamHandle);
    if (stream) {
        oboe::Result result = stream->requestPause();
        if (result != oboe::Result::OK) {
            LOGE("Failed to pause stream: %s", oboe::convertToText(result));
        }
    }
}

void KoboeEngine::stopStream(jlong streamHandle) {
    auto* stream = getStream(streamHandle);
    if (stream) {
        oboe::Result result = stream->requestStop();
        if (result != oboe::Result::OK) {
            LOGE("Failed to stop stream: %s", oboe::convertToText(result));
        }
    }
}

void KoboeEngine::closeStream(jlong streamHandle) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = streams_.find(streamHandle);
    if (it != streams_.end()) {
        if (it->second.stream) {
            it->second.stream->close();
        }
        streams_.erase(it);
    }
}

int KoboeEngine::writeStream(jlong streamHandle, const float* data, int32_t numFrames, int64_t timeoutNanos) {
    auto* stream = getStream(streamHandle);
    if (!stream) return -1;

    auto result = stream->write(data, numFrames, timeoutNanos);
    if (!result) {
        LOGE("Write failed: %s", oboe::convertToText(result.error()));
        return static_cast<int>(result.error());
    }
    return result.value();
}

int KoboeEngine::readStream(jlong streamHandle, float* data, int32_t numFrames, int64_t timeoutNanos) {
    auto* stream = getStream(streamHandle);
    if (!stream) return -1;

    auto result = stream->read(data, numFrames, timeoutNanos);
    if (!result) {
        LOGE("Read failed: %s", oboe::convertToText(result.error()));
        return static_cast<int>(result.error());
    }
    return result.value();
}

int KoboeEngine::getStreamState(jlong streamHandle) {
    auto* stream = getStream(streamHandle);
    if (!stream) return 0; // UNINITIALIZED
    return toKoboeStreamState(stream->getState());
}

double KoboeEngine::getOutputLatencyMs(jlong streamHandle) {
    auto* stream = getStream(streamHandle);
    if (!stream) return 0.0;

    auto result = stream->calculateLatencyMillis();
    if (!result) return 0.0;
    return result.value();
}

double KoboeEngine::getInputLatencyMs(jlong streamHandle) {
    auto* stream = getStream(streamHandle);
    if (!stream) return 0.0;

    auto result = stream->calculateLatencyMillis();
    if (!result) return 0.0;
    return result.value();
}

oboe::AudioStream* KoboeEngine::getStream(jlong handle) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = streams_.find(handle);
    if (it == streams_.end()) {
        LOGE("Stream handle not found: %lld", (long long)handle);
        return nullptr;
    }
    return it->second.stream.get();
}

// --- Enum mapping helpers ---

oboe::AudioFormat KoboeEngine::toOboeFormat(jint format) {
    switch (format) {
        case 0:  return oboe::AudioFormat::Float;    // PCM_FLOAT
        case 1:  return oboe::AudioFormat::I16;      // PCM_I16
        case 2:  return oboe::AudioFormat::I24;      // PCM_I24
        case 3:  return oboe::AudioFormat::I32;      // PCM_I32
        default: return oboe::AudioFormat::Float;
    }
}

oboe::PerformanceMode KoboeEngine::toOboePerformanceMode(jint mode) {
    switch (mode) {
        case 0:  return oboe::PerformanceMode::None;         // NONE
        case 1:  return oboe::PerformanceMode::LowLatency;   // LOW_LATENCY
        case 2:  return oboe::PerformanceMode::PowerSaving;  // POWER_SAVING
        default: return oboe::PerformanceMode::None;
    }
}

oboe::SharingMode KoboeEngine::toOboeSharingMode(jint mode) {
    switch (mode) {
        case 0:  return oboe::SharingMode::Shared;      // SHARED
        case 1:  return oboe::SharingMode::Exclusive;    // EXCLUSIVE
        default: return oboe::SharingMode::Shared;
    }
}

oboe::Direction KoboeEngine::toOboeDirection(jint direction) {
    switch (direction) {
        case 0:  return oboe::Direction::Output;  // OUTPUT
        case 1:  return oboe::Direction::Input;   // INPUT
        default: return oboe::Direction::Output;
    }
}

/**
 * Maps Oboe StreamState to Koboe StreamState ordinal.
 *
 * Koboe StreamState enum order:
 *   0=UNINITIALIZED, 1=OPEN, 2=STARTING, 3=STARTED,
 *   4=PAUSING, 5=PAUSED, 6=STOPPING, 7=STOPPED,
 *   8=CLOSING, 9=CLOSED
 */
int KoboeEngine::toKoboeStreamState(oboe::StreamState state) {
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
