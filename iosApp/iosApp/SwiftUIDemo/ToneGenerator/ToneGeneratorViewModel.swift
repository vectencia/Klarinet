import Foundation
import ComposeApp

final class ToneGeneratorViewModel: ObservableObject {
    @Published var frequency: Float = 440
    @Published var isPlaying = false
    @Published var streamState = "Idle"
    @Published var sleepState: SleepTimerState = .idle
    @Published var sleepRemainingMs: Int64 = 0
    @Published var sleepDurationMs: Int64 = 5_000
    @Published var sleepFadeMs: Float = 1_000

    private var engine: AudioEngine?
    private var stream: AudioStream?
    private var chain: AudioEffectChain?
    private var gain: AudioEffect?
    private var sleepTimer: SleepTimer?
    private var sleepPollTimer: Timer?
    private var callback: AudioStreamCallbackImpl?
    private var phase: Float = 0

    func togglePlayback() {
        isPlaying ? stop() : play()
    }

    func scheduleSleep() {
        guard isPlaying else { return }
        sleepTimer?.schedule(durationMs: sleepDurationMs, fadeMs: sleepFadeMs)
    }

    func pauseSleep() {
        guard isPlaying else { return }
        sleepTimer?.pause(pauseStreams: false)
    }

    func resumeSleep() {
        guard isPlaying else { return }
        sleepTimer?.resume()
    }

    func cancelSleep() {
        guard isPlaying else { return }
        sleepTimer?.cancel()
    }

    private func play() {
        let sampleRate: Int32 = 48_000
        let twoPi = Float.pi * 2

        let eng = AudioEngine.companion.create()
        engine = eng

        let cb = AudioStreamCallbackImpl { [weak self] buffer, numFrames in
            guard let self else { return numFrames }
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

        let gainFx = eng.createEffect(type: .gain)
        gainFx.setParameter(paramId: GainParams.shared.GAIN_DB, value: 0)
        gain = gainFx
        let ch = eng.createEffectChain()
        ch.add(effect: gainFx)
        chain = ch

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
        s.effectChain = ch
        stream = s
        s.start()
        DemoSession.shared.attach(s)
        let timer = SleepTimer(
            stream: s,
            gain: gainFx,
            extraStreams: KotlinArray(size: 0) { _ in nil }
        )
        sleepTimer = timer
        isPlaying = true
        sleepState = .idle
        sleepRemainingMs = 0
        streamState = "Started"
        startSleepPoll()
    }

    private func stop(keepCompleted: Bool = false) {
        sleepPollTimer?.invalidate()
        sleepPollTimer = nil
        sleepTimer?.close()
        sleepTimer = nil
        if let stream { DemoSession.shared.detach(stream) }
        stream?.stop()
        stream?.close()
        chain?.close()
        gain?.close()
        engine?.release()
        stream = nil
        chain = nil
        gain = nil
        callback = nil
        engine = nil
        isPlaying = false
        phase = 0
        streamState = "Stopped"
        sleepState = keepCompleted ? .completed : .idle
        sleepRemainingMs = 0
    }

    private func startSleepPoll() {
        sleepPollTimer?.invalidate()
        sleepPollTimer = Timer.scheduledTimer(withTimeInterval: 0.2, repeats: true) { [weak self] _ in
            guard let self, let timer = self.sleepTimer else { return }
            DispatchQueue.main.async {
                self.sleepState = timer.state
                self.sleepRemainingMs = timer.remainingMs
                if timer.state == .completed {
                    self.stop(keepCompleted: true)
                }
            }
        }
    }

    deinit { stop() }
}
