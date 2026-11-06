import Foundation
import ComposeApp

final class ToneGeneratorViewModel: ObservableObject {
    @Published var frequency: Float = 440
    @Published var isPlaying = false
    @Published var streamState = "Idle"
    @Published var xruns: Int = 0

    private var engine: AudioEngine?
    private var stream: AudioStream?
    private var callback: AudioStreamCallbackImpl?
    private var phase: Float = 0

    func togglePlayback() {
        isPlaying ? stop() : play()
    }

    private func play() {
        let sampleRate: Int32 = 48_000

        let eng = AudioEngine.companion.create()
        engine = eng

        let cb = AudioStreamCallbackImpl { [self] buffer, numFrames in
            let frames = numFrames.intValue
            let freq = frequency
            phase = SineFillKt.fillCallbackSine(
                buffer: buffer,
                frames: Int32(frames),
                phase: phase,
                frequencyHz: freq,
                sampleRate: sampleRate,
                amplitude: 0.5
            )
            return numFrames
        }
        callback = cb
        cb.onUnderrun = { [weak self] _, count in
            DispatchQueue.main.async { self?.xruns = count.intValue }
        }

        let config = AudioStreamConfig(
            sampleRate: sampleRate,
            channelCount: 1,
            audioFormat: .pcmFloat,
            bufferCapacityInFrames: 0,
            performanceMode: .lowLatency,
            sharingMode: .shared,
            direction: .output,
            deviceId: nil
        )

        let s = eng.openStream(config: config, callback: cb)
        stream = s
        s.start()
        isPlaying = true
        streamState = "Started"
    }

    private func stop() {
        stream?.stop()
        stream?.close()
        engine?.release()
        stream = nil
        callback = nil
        engine = nil
        isPlaying = false
        phase = 0
        streamState = "Stopped"
    }

    deinit { stop() }
}
