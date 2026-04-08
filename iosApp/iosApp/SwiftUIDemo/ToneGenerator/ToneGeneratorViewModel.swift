import Foundation
import ComposeApp

final class ToneGeneratorViewModel: ObservableObject {
    @Published var frequency: Float = 440
    @Published var isPlaying = false
    @Published var streamState = "Idle"

    private var engine: AudioEngine?
    private var stream: AudioStream?
    private var callback: AudioStreamCallbackImpl?
    private var phase: Float = 0

    func togglePlayback() {
        isPlaying ? stop() : play()
    }

    private func play() {
        let sampleRate: Int32 = 48_000
        let twoPi = Float.pi * 2

        let eng = AudioEngine.companion.create()
        engine = eng

        let cb = AudioStreamCallbackImpl { [self] buffer, numFrames in
            let frames = numFrames.intValue
            let freq = frequency
            let inc = twoPi * freq / Float(sampleRate)

            for i in 0..<frames {
                buffer.set(index: Int32(i), value: sinf(phase) * 0.5)
                phase += inc
                if phase > twoPi { phase -= twoPi }
            }
            return numFrames
        }
        callback = cb

        let config = AudioStreamConfig(
            sampleRate: sampleRate,
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
