import Foundation
import ComposeApp

final class EffectsViewModel: ObservableObject {
    @Published var isPlaying = false
    @Published var outputLevel: Float = 0

    @Published var gainEnabled = true
    @Published var gainDb: Float = 0
    @Published var fadeMs: Float = 500

    @Published var delayEnabled = true
    @Published var delayTimeMs: Float = 250
    @Published var delayFeedback: Float = 0.4
    @Published var delayMix: Float = 0.3

    @Published var bpfEnabled = false
    @Published var bpfCenterHz: Float = 1000
    @Published var bpfBandwidthOctaves: Float = 1
    @Published var reverbEnabled = true
    @Published var reverbRoomSize: Float = 0.5
    @Published var reverbDamping: Float = 0.5
    @Published var reverbMix: Float = 0.3

    private var engine: AudioEngine?
    private var stream: AudioStream?
    private var chain: AudioEffectChain?
    private var gainEffect: AudioEffect?
    private var delayEffect: AudioEffect?
    private var bpfEffect: AudioEffect?
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
        gain.setParameter(paramId: GainParams.shared.FADE_MS, value: fadeMs)
        gainEffect = gain

        let delay = eng.createEffect(type: .delay)
        delay.setParameter(paramId: DelayParams.shared.TIME_MS, value: delayTimeMs)
        delay.setParameter(paramId: DelayParams.shared.FEEDBACK, value: delayFeedback)
        delay.setParameter(paramId: DelayParams.shared.WET_DRY_MIX, value: delayMix)
        delayEffect = delay

        let bpf = eng.createEffect(type: .bandPassFilter)
        bpf.setParameter(paramId: BPFParams.shared.CENTER_HZ, value: bpfCenterHz)
        bpf.setParameter(paramId: BPFParams.shared.BANDWIDTH, value: bpfBandwidthOctaves)
        bpf.isEnabled = bpfEnabled
        bpfEffect = bpf

        let reverb = eng.createEffect(type: .reverb)
        reverb.setParameter(paramId: ReverbParams.shared.ROOM_SIZE, value: reverbRoomSize)
        reverb.setParameter(paramId: ReverbParams.shared.DAMPING, value: reverbDamping)
        reverb.setParameter(paramId: ReverbParams.shared.WET_DRY_MIX, value: reverbMix)
        reverbEffect = reverb

        // Build chain
        let ch = eng.createEffectChain()
        ch.add(effect: gain)
        ch.add(effect: bpf)
        ch.add(effect: delay)
        ch.add(effect: reverb)
        chain = ch

        // Tone generator callback
        let cb = AudioStreamCallbackImpl { [weak self] buffer, numFrames in
            guard let self else { return numFrames }
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
            direction: .output,
            deviceId: nil
        )

        let s = eng.openStream(config: config, callback: cb)
        s.effectChain = ch
        stream = s
        s.start()
        DemoSession.shared.attach(s)
        isPlaying = true

        levelTimer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak self] _ in
            guard let self, let stream = self.stream else { return }
            DispatchQueue.main.async {
                self.outputLevel = stream.peakLevel
            }
        }
    }

    private func stop() {
        if let stream { DemoSession.shared.detach(stream) }
        levelTimer?.invalidate()
        levelTimer = nil
        stream?.stop()
        stream?.close()
        chain?.release()
        gainEffect?.release()
        delayEffect?.release()
        bpfEffect?.release()
        reverbEffect?.release()
        engine?.release()
        stream = nil
        chain = nil
        gainEffect = nil
        delayEffect = nil
        bpfEffect = nil
        reverbEffect = nil
        callback = nil
        engine = nil
        isPlaying = false
        outputLevel = 0
        phase = 0
    }

    // MARK: - Parameter Updates

    func updateGainDb(_ v: Float) { gainDb = v; gainEffect?.setParameter(paramId: GainParams.shared.GAIN_DB, value: v) }
    func updateFadeMs(_ v: Float) { fadeMs = v; gainEffect?.setParameter(paramId: GainParams.shared.FADE_MS, value: v) }
    func updateGainEnabled(_ e: Bool) { gainEnabled = e; gainEffect?.isEnabled = e }
    func updateDelayTimeMs(_ v: Float) { delayTimeMs = v; delayEffect?.setParameter(paramId: DelayParams.shared.TIME_MS, value: v) }
    func updateDelayFeedback(_ v: Float) { delayFeedback = v; delayEffect?.setParameter(paramId: DelayParams.shared.FEEDBACK, value: v) }
    func updateDelayMix(_ v: Float) { delayMix = v; delayEffect?.setParameter(paramId: DelayParams.shared.WET_DRY_MIX, value: v) }
    func updateDelayEnabled(_ e: Bool) { delayEnabled = e; delayEffect?.isEnabled = e }
    func updateBpfEnabled(_ e: Bool) { bpfEnabled = e; bpfEffect?.isEnabled = e }
    func updateBpfCenterHz(_ v: Float) { bpfCenterHz = v; bpfEffect?.setParameter(paramId: BPFParams.shared.CENTER_HZ, value: v) }
    func updateBpfBandwidth(_ v: Float) { bpfBandwidthOctaves = v; bpfEffect?.setParameter(paramId: BPFParams.shared.BANDWIDTH, value: v) }
    func updateReverbRoomSize(_ v: Float) { reverbRoomSize = v; reverbEffect?.setParameter(paramId: ReverbParams.shared.ROOM_SIZE, value: v) }
    func updateReverbDamping(_ v: Float) { reverbDamping = v; reverbEffect?.setParameter(paramId: ReverbParams.shared.DAMPING, value: v) }
    func updateReverbMix(_ v: Float) { reverbMix = v; reverbEffect?.setParameter(paramId: ReverbParams.shared.WET_DRY_MIX, value: v) }
    func updateReverbEnabled(_ e: Bool) { reverbEnabled = e; reverbEffect?.isEnabled = e }

    deinit { stop() }
}
