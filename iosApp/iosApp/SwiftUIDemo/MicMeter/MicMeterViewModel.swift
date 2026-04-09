import Foundation
import ComposeApp

final class MicMeterViewModel: ObservableObject {
    @Published var isRecording = false
    @Published var level: Float = 0
    @Published var inputLatency = "--"

    private var engine: AudioEngine?
    private var stream: AudioStream?
    private var callback: AudioStreamCallbackImpl?
    private var levelTimer: Timer?

    func toggleRecording() {
        isRecording ? stop() : start()
    }

    private func start() {
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
            direction: .input
        )

        let s = eng.openStream(config: config, callback: cb)
        stream = s
        s.start()
        isRecording = true

        let latency = s.latencyInfo
        inputLatency = "\(Int(latency.inputLatencyMs)) ms"

        levelTimer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak self] _ in
            guard let self, let stream = self.stream else { return }
            DispatchQueue.main.async {
                self.level = stream.peakLevel
            }
        }
    }

    private func stop() {
        levelTimer?.invalidate()
        levelTimer = nil
        stream?.stop()
        stream?.close()
        engine?.release()
        stream = nil
        callback = nil
        engine = nil
        isRecording = false
        level = 0
    }

    deinit { stop() }
}
