#include "klarinet_dsp.h"
#include "AudioFifo.h"

#include <algorithm>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstring>
#include <mutex>
#include <thread>
#include <vector>

namespace {

struct Offload {
    explicit Offload(int32_t capacitySamples)
        : fifo(capacitySamples) {}

    klarinet::AudioFifo fifo;
    int framesPerBurst = 0;
    int channelCount = 0;
    int isCapture = 0;
    KlarinetUserAudioCallback callback = nullptr;
    std::atomic<KlarinetXrunCallback> xrunCallback{nullptr};
    void* userData = nullptr;
    std::atomic<int> xrunCount{0};
    int lastReportedXruns = 0;
    std::atomic<bool> running{false};
    std::thread worker;
    std::mutex mu;
    std::condition_variable cv;
};

void maybeReportXruns(Offload* o) {
    const KlarinetXrunCallback cb = o->xrunCallback.load(std::memory_order_acquire);
    if (cb == nullptr) return;
    const int count = o->xrunCount.load(std::memory_order_acquire);
    if (count != o->lastReportedXruns) {
        o->lastReportedXruns = count;
        cb(o->userData, count);
    }
}

int burstSamples(const Offload* o) {
    return o->framesPerBurst * o->channelCount;
}

bool hasWork(const Offload* o) {
    const int needed = burstSamples(o);
    if (o->isCapture != 0) {
        return o->fifo.availableToRead() >= needed;
    }
    return o->fifo.availableToWrite() >= needed;
}

void waitForWork(Offload* o) {
    std::unique_lock<std::mutex> lock(o->mu);
    o->cv.wait_for(lock, std::chrono::milliseconds(2), [o] {
        return !o->running.load(std::memory_order_acquire) || hasWork(o);
    });
}

void workerLoop(Offload* o) {
    const int needed = burstSamples(o);
    std::vector<float> scratch(static_cast<size_t>(needed), 0.0f);

    while (o->running.load(std::memory_order_acquire)) {
        maybeReportXruns(o);
        if (!hasWork(o)) {
            waitForWork(o);
            continue;
        }

        if (o->isCapture != 0) {
            if (o->fifo.read(scratch.data(), needed) < needed) {
                waitForWork(o);
                continue;
            }
            if (o->callback != nullptr) {
                o->callback(o->userData, scratch.data(), o->framesPerBurst, o->channelCount);
            }
        } else {
            int frames = o->framesPerBurst;
            if (o->callback != nullptr) {
                frames = o->callback(o->userData, scratch.data(), o->framesPerBurst, o->channelCount);
            }
            if (frames <= 0) {
                continue;
            }
            const int samples = std::min(frames, o->framesPerBurst) * o->channelCount;
            int written = 0;
            while (written < samples && o->running.load(std::memory_order_acquire)) {
                const int n = o->fifo.write(scratch.data() + written, samples - written);
                if (n == 0) {
                    waitForWork(o);
                } else {
                    written += n;
                }
            }
        }
    }
}

} // namespace

KlarinetOffloadHandle klarinet_offload_create(
    int framesPerBurst,
    int channelCount,
    int isCapture,
    KlarinetUserAudioCallback callback,
    void* userData
) {
    if (framesPerBurst <= 0) framesPerBurst = 256;
    if (channelCount <= 0) channelCount = 1;
    if (callback == nullptr) return nullptr;

    const int needed = framesPerBurst * channelCount;
    auto* o = new Offload(needed * 8);
    o->framesPerBurst = framesPerBurst;
    o->channelCount = channelCount;
    o->isCapture = isCapture;
    o->callback = callback;
    o->userData = userData;
    o->running.store(true, std::memory_order_release);
    o->worker = std::thread(workerLoop, o);
    return o;
}

void klarinet_offload_destroy(KlarinetOffloadHandle handle) {
    auto* o = static_cast<Offload*>(handle);
    if (o == nullptr) return;
    o->running.store(false, std::memory_order_release);
    o->cv.notify_all();
    if (o->worker.joinable()) {
        o->worker.join();
    }
    delete o;
}

void klarinet_offload_process(KlarinetOffloadHandle handle, float* audio, int numFrames) {
    auto* o = static_cast<Offload*>(handle);
    if (o == nullptr || audio == nullptr || numFrames <= 0) return;

    const int total = numFrames * o->channelCount;
    if (o->isCapture != 0) {
        const int written = o->fifo.write(audio, total);
        if (written < total) {
            o->xrunCount.fetch_add(1, std::memory_order_release);
        }
    } else {
        const int got = o->fifo.read(audio, total);
        if (got < total) {
            std::memset(audio + got, 0, static_cast<size_t>(total - got) * sizeof(float));
            o->xrunCount.fetch_add(1, std::memory_order_release);
        }
    }
    o->cv.notify_one();
}

void klarinet_offload_set_xrun_callback(KlarinetOffloadHandle handle, KlarinetXrunCallback cb) {
    auto* o = static_cast<Offload*>(handle);
    if (o == nullptr) return;
    o->xrunCallback.store(cb, std::memory_order_release);
}
