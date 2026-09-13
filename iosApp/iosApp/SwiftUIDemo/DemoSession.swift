import Foundation
import ComposeApp

final class DemoSession: ObservableObject {
    static let shared = DemoSession()

    let manager = AudioSessionManager()
    @Published var interrupted = false

    private init() {
        do {
            try manager.configure(category: .playAndRecord, mode: .default_)
            try manager.setActive(active: true)
        } catch {
            NSLog("KlarinetDemo session configure failed: \(error)")
        }
        manager.observeInterruptions { [weak self] info in
            DispatchQueue.main.async {
                self?.interrupted = info.type == .began
            }
        }
    }

    func attach(_ stream: AudioStream) {
        manager.attach(stream: stream)
    }

    func detach(_ stream: AudioStream) {
        manager.detach(stream: stream)
    }
}
