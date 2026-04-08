import Foundation
import ComposeApp

final class EffectsViewModel: ObservableObject {
    @Published var isPlaying = false
    @Published var outputLevel: Float = 0

    @Published var gainEnabled = true
    @Published var gainDb: Float = 0

    @Published var delayEnabled = true
    @Published var delayTimeMs: Float = 250
    @Published var delayFeedback: Float = 0.4
    @Published var delayMix: Float = 0.3

    @Published var reverbEnabled = true
    @Published var reverbRoomSize: Float = 0.7
    @Published var reverbDamping: Float = 0.5
    @Published var reverbMix: Float = 0.3

    private var engine: AudioEngine?
    private var stream: AudioStream?
    private var chain: AudioEffectChain?
    private var gainEffect: AudioEffect?
    private var delayEffect: AudioEffect?
    private var reverbEffect: AudioEffect?
    private var callback: AudioStreamCallbackImpl?
    private var phase: Float = 0
    private var levelTimer: Timer?

    func togglePlayback() {
        isPlaying ? stop() : play()
    }

    private func play() {
        let sampleRate: Int32 = 48_000
        let twoPi = Float.pi * 2

        let eng = AudioEngine.companion.create()
        engine = eng

        // Create effects
        let gain = eng.createEffect(type: .gain)
        gain.setParameter(paramId: GainParams.shared.GAIN_DB, value: gainDb)
        gainEffect = gain

        let delay = eng.createEffect(type: .delay)
        delay.setParameter(paramId: DelayParams.shared.TIME_MS, value: delayTimeMs)
        delay.setParameter(paramId: DelayParams.shared.FEEDBACK, value: delayFeedback)
        delay.setParameter(paramId: DelayParams.shared.WET_DRY_MIX, value: delayMix)
        delayEffect = delay

        let reverb = eng.createEffect(type: .reverb)
        reverb.setParameter(paramId: ReverbParams.shared.ROOM_SIZE, value: reverbRoomSize)
        reverb.setParameter(paramId: ReverbParams.shared.DAMPING, value: reverbDamping)
        reverb.setParameter(paramId: ReverbParams.shared.WET_DRY_MIX, value: reverbMix)
        reverbEffect = reverb

        // Build chain
        let ch = eng.createEffectChain()
        ch.add(effect: gain)
        ch.add(effect: delay)
        ch.add(effect: reverb)
        chain = ch

        // Tone generator callback
        let cb = AudioStreamCallbackImpl { [self] buffer, numFrames in
            let frames = numFrames.intValue
            let inc = twoPi * 440.0 / Float(sampleRate)

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
        s.effectChain = ch
        stream = s
        s.start()
        isPlaying = true

        levelTimer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak self] _ in
            guard let self, let stream = self.stream else { return }
            DispatchQueue.main.async {
                self.outputLevel = stream.peakLevel
            }
        }
    }

    private func stop() {
        levelTimer?.invalidate()
        levelTimer = nil
        stream?.stop()
        stream?.close()
        chain?.release()
        gainEffect?.release()
        delayEffect?.release()
        reverbEffect?.release()
        engine?.release()
        stream = nil
        chain = nil
        gainEffect = nil
        delayEffect = nil
        reverbEffect = nil
        callback = nil
        engine = nil
        isPlaying = false
        outputLevel = 0
        phase = 0
    }

    // MARK: - Parameter Updates

    func updateGainDb(_ v: Float) { gainDb = v; gainEffect?.setParameter(paramId: GainParams.shared.GAIN_DB, value: v) }
    func updateGainEnabled(_ e: Bool) { gainEnabled = e; gainEffect?.isEnabled = e }
    func updateDelayTimeMs(_ v: Float) { delayTimeMs = v; delayEffect?.setParameter(paramId: DelayParams.shared.TIME_MS, value: v) }
    func updateDelayFeedback(_ v: Float) { delayFeedback = v; delayEffect?.setParameter(paramId: DelayParams.shared.FEEDBACK, value: v) }
    func updateDelayMix(_ v: Float) { delayMix = v; delayEffect?.setParameter(paramId: DelayParams.shared.WET_DRY_MIX, value: v) }
    func updateDelayEnabled(_ e: Bool) { delayEnabled = e; delayEffect?.isEnabled = e }
    func updateReverbRoomSize(_ v: Float) { reverbRoomSize = v; reverbEffect?.setParameter(paramId: ReverbParams.shared.ROOM_SIZE, value: v) }
    func updateReverbDamping(_ v: Float) { reverbDamping = v; reverbEffect?.setParameter(paramId: ReverbParams.shared.DAMPING, value: v) }
    func updateReverbMix(_ v: Float) { reverbMix = v; reverbEffect?.setParameter(paramId: ReverbParams.shared.WET_DRY_MIX, value: v) }
    func updateReverbEnabled(_ e: Bool) { reverbEnabled = e; reverbEffect?.isEnabled = e }

    deinit { stop() }
}
