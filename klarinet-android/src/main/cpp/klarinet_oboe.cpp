#include "klarinet_oboe.h"

#include <android/log.h>
#include <oboe/Oboe.h>

#include <cstddef>
#include <cstdint>
#include <memory>
#include <vector>

static_assert(sizeof(KlarinetOboeOpen) == 56, "KlarinetOboeOpen size");
static_assert(offsetof(KlarinetOboeOpen, audio) == 32, "KlarinetOboeOpen.audio");
static_assert(sizeof(KlarinetOboeInfo) == 32, "KlarinetOboeInfo size");
static_assert(offsetof(KlarinetOboeInfo, device_ids) == 24, "KlarinetOboeInfo.device_ids");

namespace {

constexpr const char* kEngineTag = "KlarinetEngine";
constexpr const char* kCallbackTag = "KlarinetCallback";

class KlarinetOboeCallback : public oboe::AudioStreamDataCallback,
                             public oboe::AudioStreamErrorCallback,
                             public oboe::AudioStreamRoutingCallback {
public:
    KlarinetOboeCallback(KlarinetOboeAudioFn audio, KlarinetOboeRouteFn route, void* user)
        : audio_(audio), route_(route), user_(user) {}

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* stream,
        void* audioData,
        int32_t numFrames) override {
        if (audio_ != nullptr && stream != nullptr && audioData != nullptr) {
            const int isInput = stream->getDirection() == oboe::Direction::Input ? 1 : 0;
            audio_(
                user_,
                isInput,
                static_cast<float*>(audioData),
                numFrames,
                stream->getChannelCount());
        }
        return oboe::DataCallbackResult::Continue;
    }

    void onErrorBeforeClose(oboe::AudioStream* /*stream*/, oboe::Result error) override {
        __android_log_print(
            ANDROID_LOG_ERROR,
            kCallbackTag,
            "Oboe error before close: %s",
            oboe::convertToText(error));
    }

    void onErrorAfterClose(oboe::AudioStream* /*stream*/, oboe::Result error) override {
        __android_log_print(
            ANDROID_LOG_ERROR,
            kCallbackTag,
            "Oboe error after close: %s",
            oboe::convertToText(error));
    }

    void onRoutingChanged(
        oboe::AudioStream* /*stream*/,
        const int32_t* deviceIds,
        int32_t numDevices) override {
        if (route_ != nullptr) {
            route_(user_, deviceIds, numDevices);
        }
    }

private:
    KlarinetOboeAudioFn audio_;
    KlarinetOboeRouteFn route_;
    void* user_;
};

struct Owner {
    std::shared_ptr<oboe::AudioStream> stream;
    std::shared_ptr<KlarinetOboeCallback> callback;
};

Owner* owner_of(void* handle) {
    return static_cast<Owner*>(handle);
}

oboe::AudioStream* stream_of(void* handle) {
    Owner* owner = owner_of(handle);
    if (owner == nullptr) {
        return nullptr;
    }
    return owner->stream.get();
}

int result_code(oboe::Result result) {
    return static_cast<int>(result);
}

oboe::AudioFormat to_oboe_format(int32_t format) {
    (void)format;
    return oboe::AudioFormat::Float;
}

oboe::PerformanceMode to_oboe_performance(int32_t mode) {
    switch (mode) {
        case 0:
            return oboe::PerformanceMode::None;
        case 1:
            return oboe::PerformanceMode::LowLatency;
        case 2:
            return oboe::PerformanceMode::PowerSaving;
        default:
            return oboe::PerformanceMode::None;
    }
}

oboe::SharingMode to_oboe_sharing(int32_t mode) {
    switch (mode) {
        case 0:
            return oboe::SharingMode::Shared;
        case 1:
            return oboe::SharingMode::Exclusive;
        default:
            return oboe::SharingMode::Shared;
    }
}

oboe::Direction to_oboe_direction(int32_t direction) {
    switch (direction) {
        case 0:
            return oboe::Direction::Output;
        case 1:
            return oboe::Direction::Input;
        default:
            return oboe::Direction::Output;
    }
}

/**
 * Klarinet StreamState ordinals, copied from KlarinetEngine::toKlarinetStreamState.
 * Oboe 1.11 values that Klarinet does not name (Unknown, Flushing, Flushed,
 * Disconnected) stay on the old default, which is UNINITIALIZED.
 */
int to_klarinet_state(oboe::StreamState state) {
    switch (state) {
        case oboe::StreamState::Uninitialized:
            return 0;
        case oboe::StreamState::Open:
            return 1;
        case oboe::StreamState::Starting:
            return 2;
        case oboe::StreamState::Started:
            return 3;
        case oboe::StreamState::Pausing:
            return 4;
        case oboe::StreamState::Paused:
            return 5;
        case oboe::StreamState::Stopping:
            return 6;
        case oboe::StreamState::Stopped:
            return 7;
        case oboe::StreamState::Closing:
            return 8;
        case oboe::StreamState::Closed:
            return 9;
        case oboe::StreamState::Unknown:
        case oboe::StreamState::Flushing:
        case oboe::StreamState::Flushed:
        case oboe::StreamState::Disconnected:
        default:
            return 0;
    }
}

void copy_device_ids(const std::vector<int32_t>& ids, KlarinetOboeInfo* info) {
    info->device_count = 0;
    if (info->device_ids == nullptr || info->device_capacity <= 0 || ids.empty()) {
        return;
    }
    const size_t cap = static_cast<size_t>(info->device_capacity);
    const size_t n = ids.size() < cap ? ids.size() : cap;
    for (size_t i = 0; i < n; ++i) {
        info->device_ids[i] = ids[i];
    }
    info->device_count = static_cast<int32_t>(n);
}

int log_request(const char* op, oboe::Result result) {
    if (result != oboe::Result::OK) {
        __android_log_print(
            ANDROID_LOG_ERROR,
            kEngineTag,
            "Failed to %s stream: %s",
            op,
            oboe::convertToText(result));
    }
    return result_code(result);
}

}  // namespace

extern "C" {

void* klarinet_oboe_open(const KlarinetOboeOpen* request, KlarinetOboeInfo* info) {
    if (request == nullptr || info == nullptr) {
        return nullptr;
    }
    info->frames_per_burst = 0;
    info->channel_count = 0;
    info->direction = 0;
    info->sample_rate = 0;
    info->device_count = 0;

    auto callback = std::make_shared<KlarinetOboeCallback>(request->audio, request->route, request->user);

    oboe::AudioStreamBuilder builder;
    builder.setDirection(to_oboe_direction(request->direction));
    builder.setPerformanceMode(to_oboe_performance(request->performance_mode));
    builder.setSharingMode(to_oboe_sharing(request->sharing_mode));
    builder.setFormat(to_oboe_format(request->audio_format));
    builder.setChannelCount(request->channel_count);
    builder.setDataCallback(callback);
    builder.setErrorCallback(callback);
    builder.setRoutingCallback(callback);

    if (request->sample_rate > 0) {
        builder.setSampleRate(request->sample_rate);
        builder.setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium);
        builder.setChannelConversionAllowed(true);
        builder.setFormatConversionAllowed(true);
    }
    if (request->buffer_capacity_in_frames > 0) {
        builder.setBufferCapacityInFrames(request->buffer_capacity_in_frames);
    }
    if (request->device_id >= 0) {
        builder.setDeviceId(request->device_id);
    }

    std::shared_ptr<oboe::AudioStream> stream;
    const oboe::Result result = builder.openStream(stream);
    if (result != oboe::Result::OK) {
        __android_log_print(
            ANDROID_LOG_ERROR,
            kEngineTag,
            "Failed to open stream: %s",
            oboe::convertToText(result));
        return nullptr;
    }

    info->frames_per_burst = stream->getFramesPerBurst();
    info->channel_count = stream->getChannelCount();
    info->direction = stream->getDirection() == oboe::Direction::Input ? 1 : 0;
    info->sample_rate = stream->getSampleRate();
    const std::vector<int32_t> opened = stream->getDeviceIds();
    if (!opened.empty()) {
        copy_device_ids(opened, info);
    } else {
        const int32_t device = stream->getDeviceId();
        if (device >= 0 && info->device_ids != nullptr && info->device_capacity > 0) {
            info->device_ids[0] = device;
            info->device_count = 1;
        }
    }

    auto* owner = new Owner{std::move(stream), std::move(callback)};
    return owner;
}

int klarinet_oboe_request_start(void* handle) {
    oboe::AudioStream* stream = stream_of(handle);
    if (stream == nullptr) {
        return result_code(oboe::Result::ErrorInvalidHandle);
    }
    return log_request("start", stream->requestStart());
}

int klarinet_oboe_request_pause(void* handle) {
    oboe::AudioStream* stream = stream_of(handle);
    if (stream == nullptr) {
        return result_code(oboe::Result::ErrorInvalidHandle);
    }
    return log_request("pause", stream->requestPause());
}

int klarinet_oboe_request_stop(void* handle) {
    oboe::AudioStream* stream = stream_of(handle);
    if (stream == nullptr) {
        return result_code(oboe::Result::ErrorInvalidHandle);
    }
    return log_request("stop", stream->requestStop());
}

int klarinet_oboe_stop(void* handle) {
    oboe::AudioStream* stream = stream_of(handle);
    if (stream == nullptr) {
        return result_code(oboe::Result::ErrorInvalidHandle);
    }
    return result_code(stream->stop());
}

void klarinet_oboe_close(void* handle) {
    Owner* owner = owner_of(handle);
    if (owner == nullptr) {
        return;
    }
    if (owner->stream) {
        owner->stream->close();
    }
    delete owner;
}

int klarinet_oboe_write(
    void* handle,
    const float* data,
    int32_t num_frames,
    int64_t timeout_nanos) {
    oboe::AudioStream* stream = stream_of(handle);
    if (stream == nullptr) {
        return -1;
    }
    auto result = stream->write(data, num_frames, timeout_nanos);
    if (!result) {
        __android_log_print(
            ANDROID_LOG_ERROR,
            kEngineTag,
            "Write failed: %s",
            oboe::convertToText(result.error()));
        return result_code(result.error());
    }
    return result.value();
}

int klarinet_oboe_read(void* handle, float* data, int32_t num_frames, int64_t timeout_nanos) {
    oboe::AudioStream* stream = stream_of(handle);
    if (stream == nullptr) {
        return -1;
    }
    auto result = stream->read(data, num_frames, timeout_nanos);
    if (!result) {
        __android_log_print(
            ANDROID_LOG_ERROR,
            kEngineTag,
            "Read failed: %s",
            oboe::convertToText(result.error()));
        return result_code(result.error());
    }
    return result.value();
}

int klarinet_oboe_state(void* handle) {
    oboe::AudioStream* stream = stream_of(handle);
    if (stream == nullptr) {
        return 0;
    }
    return to_klarinet_state(stream->getState());
}

double klarinet_oboe_latency_ms(void* handle) {
    oboe::AudioStream* stream = stream_of(handle);
    if (stream == nullptr) {
        return 0.0;
    }
    auto result = stream->calculateLatencyMillis();
    if (!result) {
        return 0.0;
    }
    return result.value();
}

int klarinet_oboe_invalid_handle(void) {
    return result_code(oboe::Result::ErrorInvalidHandle);
}

const char* klarinet_oboe_result_text(int result) {
    return oboe::convertToText(static_cast<oboe::Result>(result));
}

void klarinet_oboe_log(int priority, const char* tag, const char* message) {
    if (tag == nullptr || message == nullptr) {
        return;
    }
    __android_log_print(priority, tag, "%s", message);
}

}  // extern "C"
