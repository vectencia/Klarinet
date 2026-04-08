import Foundation
import ComposeApp

final class LatencyViewModel: ObservableObject {
    @Published var isMeasuring = false
    @Published var outputLatency = "--"
    @Published var inputLatency = "--"
    @Published var sampleRate = "--"
    @Published var bufferSize = "--"
    @Published var performanceMode = "--"

    private var engine: AudioEngine?
    private var stream: AudioStream?
    private var callback: AudioStreamCallbackImpl?

    func toggleMeasure() {
        isMeasuring ? stop() : measure()
    }

    private func measure() {
        let eng = AudioEngine.companion.create()
        engine = eng

        let cb = AudioStreamCallbackImpl { _, numFrames in
            return numFrames ?? KotlinInt(int: 0)
        }
        callback = cb

        let config = AudioStreamConfig(
            sampleRate: 48_000,
            channelCount: 1,
            audioFormat: .pcmFloat,
            bufferCapacityInFrames: 0,
            performanceMode: .lowLatency,
            sharingMode: .shared,
            direction: .output
        )

        let s = eng.openStream(config: config, callback: cb)
        stream = s
        s.start()
        isMeasuring = true

        let latency = s.latencyInfo
        let cfg = s.config
        outputLatency = "\(Int(latency.outputLatencyMs)) ms"
        inputLatency = "\(Int(latency.inputLatencyMs)) ms"
        sampleRate = "\(cfg.sampleRate) Hz"
        bufferSize = cfg.bufferCapacityInFrames > 0
            ? "\(cfg.bufferCapacityInFrames) frames"
            : "Platform default"
        performanceMode = cfg.performanceMode.name
    }

    private func stop() {
        stream?.stop()
        stream?.close()
        engine?.release()
        stream = nil
        callback = nil
        engine = nil
        isMeasuring = false
    }

    deinit { stop() }
}
