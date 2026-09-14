import Foundation
import ComposeApp

final class DemoSession: ObservableObject {
    static let shared = DemoSession()

    let manager = AudioSessionManager()
    @Published var interrupted = false
    @Published var routeLabel: String?

    private var interruptionListener: ((AudioInterruptionInfo) -> Void)?

    private init() {
        do {
            try manager.configure(category: .playAndRecord, mode: .default_)
            try manager.setActive(active: true)
        } catch {
            NSLog("KlarinetDemo session configure failed: \(error)")
        }
        let listener: (AudioInterruptionInfo) -> Void = { [weak self] info in
            DispatchQueue.main.async {
                self?.interrupted = info.type == .began
            }
        }
        interruptionListener = listener
        manager.observeInterruptions(listener: listener)
        manager.observeRouteChanges { [weak self] info in
            DispatchQueue.main.async {
                self?.routeLabel = "Route: \(info.reason)"
            }
        }
    }

    func requestRecordPermission(_ onResult: @escaping (Bool) -> Void) {
        manager.requestRecordPermission { granted in
            DispatchQueue.main.async { onResult(granted.boolValue) }
        }
    }

    func attach(_ stream: AudioStream) {
        manager.attach(stream: stream)
    }

    func detach(_ stream: AudioStream) {
        manager.detach(stream: stream)
    }
}
