import Foundation
import ComposeApp

final class ScenesViewModel: ObservableObject {
    static let layerLow = "low"
    static let layerHigh = "high"

    @Published var fadeMs: Float = 2_000
    @Published var currentId: String?
    @Published var layerIds: [String] = []
    @Published var json = ""
    @Published var isPlaying = false
    @Published var status = ""

    let low: AudioScene
    let high: AudioScene
    let stack: AudioScene
    let lowHall: AudioScene

    private var engine: AudioEngine?
    private var player: AudioScenePlayer?
    private var attached: [AudioStream] = []
    private var callbacks: [AudioStreamCallbackImpl] = []
    private var phases: [String: Float] = [:]

    init() {
        low = AudioScene(
            id: "low",
            layers: [SceneLayer(id: Self.layerLow, gainDb: 0, effects: [])]
        )
        high = AudioScene(
            id: "high",
            layers: [SceneLayer(id: Self.layerHigh, gainDb: 0, effects: [])]
        )
        stack = AudioScene(
            id: "stack",
            layers: [
                SceneLayer(id: Self.layerLow, gainDb: 0, effects: []),
                SceneLayer(id: Self.layerHigh, gainDb: -6, effects: []),
            ]
        )
        let reverbParams = KotlinMutableDictionary<KotlinInt, KotlinFloat>()
        reverbParams[KotlinInt(int: ReverbParams.shared.ROOM_SIZE)] = KotlinFloat(float: 0.7)
        reverbParams[KotlinInt(int: ReverbParams.shared.WET_DRY_MIX)] = KotlinFloat(float: 0.4)
        lowHall = AudioScene(
            id: "low-hall",
            layers: [
                SceneLayer(
                    id: Self.layerLow,
                    gainDb: 0,
                    effects: [
                        SceneEffect(
                            type: .reverb,
                            // Map<Int, Float> is [KotlinInt: KotlinFloat]; Swift literals fail to type-check.
                            params: reverbParams as NSDictionary as! [KotlinInt: KotlinFloat]
                        ),
                    ]
                ),
            ]
        )
    }

    func hz(for layerId: String) throws -> Double {
        guard let hz = Self.frequency(for: layerId) else {
            throw ScenesError.unknownLayer(layerId)
        }
        return hz
    }

    func transition(to scene: AudioScene) {
        do {
            for layer in scene.layers {
                _ = try hz(for: layer.id)
            }
            let active = ensurePlayer()
            let source = SceneLayerSource { [unowned self] layer in
                self.openLayer(layer.id)
            }
            active.transitionTo(scene: scene, fadeMs: fadeMs, source: source)
            let current = active.current
            currentId = current?.id
            layerIds = current?.layers.map(\.id) ?? []
            json = current.map { AudioSceneJson.shared.encode(scene: $0) } ?? ""
            isPlaying = true
            status = ""
        } catch {
            let message = error.localizedDescription
            status = message.isEmpty ? "transition failed" : message
        }
    }

    func stop() {
        for stream in attached {
            DemoSession.shared.detach(stream)
        }
        attached.removeAll()
        player?.close()
        engine?.release()
        player = nil
        engine = nil
        callbacks.removeAll()
        phases.removeAll()
        isPlaying = false
        currentId = nil
        layerIds = []
        json = ""
        status = ""
    }

    deinit { stop() }

    private func ensurePlayer() -> AudioScenePlayer {
        if let player { return player }
        let newEngine = AudioEngine.companion.create()
        engine = newEngine
        let created = AudioScenePlayer(engine: newEngine)
        player = created
        return created
    }

    private static func frequency(for layerId: String) -> Double? {
        switch layerId {
        case layerLow: return 220
        case layerHigh: return 660
        default: return nil
        }
    }

    private func openLayer(_ layerId: String) -> AudioStream {
        let hz: Double
        if let value = Self.frequency(for: layerId) {
            hz = value
        } else {
            status = "Unknown layer id: \(layerId)"
            hz = 0
        }
        let sampleRate: Int32 = 48_000
        let eng: AudioEngine
        if let existing = engine {
            eng = existing
        } else {
            eng = AudioEngine.companion.create()
            engine = eng
        }
        let phaseKey = layerId
        if phases[phaseKey] == nil { phases[phaseKey] = 0 }
        let cb = AudioStreamCallbackImpl { [weak self] buffer, numFrames in
            guard let self else { return numFrames }
            let frames = numFrames.intValue
            let twoPi = Float.pi * 2
            let increment = twoPi * Float(hz) / Float(sampleRate)
            var phase = self.phases[phaseKey] ?? 0
            for i in 0..<frames {
                buffer.set(index: Int32(i), value: sinf(phase) * 0.2)
                phase += increment
                if phase > twoPi { phase -= twoPi }
            }
            self.phases[phaseKey] = phase
            return numFrames
        }
        callbacks.append(cb)
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
        let stream = eng.openStream(config: config, callback: cb)
        DemoSession.shared.attach(stream)
        attached.append(stream)
        return stream
    }
}

private enum ScenesError: LocalizedError {
    case unknownLayer(String)

    var errorDescription: String? {
        switch self {
        case .unknownLayer(let layerId): return "Unknown layer id: \(layerId)"
        }
    }
}

private final class SceneLayerSource: NSObject, AudioScenePlayerLayerSource {
    private let open: (SceneLayer) -> AudioStream

    init(open: @escaping (SceneLayer) -> AudioStream) {
        self.open = open
    }

    func openStream(layer: SceneLayer) -> AudioStream {
        open(layer)
    }
}
