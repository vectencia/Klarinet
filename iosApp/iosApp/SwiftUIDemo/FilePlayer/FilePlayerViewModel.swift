import Foundation
import ComposeApp

final class FilePlayerViewModel: ObservableObject {
    @Published var filePath = ""
    @Published var isPlaying = false
    @Published var errorMessage: String?
    @Published var hasFileInfo = false
    @Published var fileFormat = "--"
    @Published var duration = "--"
    @Published var fileSampleRate = "--"
    @Published var channels = "--"
    @Published var bitRate = "--"

    private var engine: AudioEngine?
    private var stream: AudioStream?

    func loadFile() {
        errorMessage = nil
        hasFileInfo = false
        guard !filePath.isEmpty else { return }

        let reader = AudioFileReader(filePath: filePath)
        let info = reader.info
        fileFormat = info.format.name
        let totalSec = info.durationMs / 1000
        duration = "\(totalSec / 60):\(String(format: "%02d", totalSec % 60))"
        fileSampleRate = "\(info.sampleRate) Hz"
        channels = "\(info.channelCount)"
        bitRate = "\(info.bitRate / 1000) kbps"
        hasFileInfo = true
        reader.close()
    }

    func togglePlayback() {
        isPlaying ? stop() : play()
    }

    private func play() {
        guard !filePath.isEmpty else {
            errorMessage = "Please enter a file path"
            return
        }

        let eng = AudioEngine.companion.create()
        engine = eng

        let config = AudioStreamConfig(
            sampleRate: 48_000,
            channelCount: 2,
            audioFormat: .pcmFloat,
            bufferCapacityInFrames: 0,
            performanceMode: .lowLatency,
            sharingMode: .shared,
            direction: .output
        )

        let s = eng.playFile(filePath: filePath, config: config)
        stream = s
        s.start()
        isPlaying = true
        errorMessage = nil
    }

    private func stop() {
        stream?.stop()
        stream?.close()
        engine?.release()
        stream = nil
        engine = nil
        isPlaying = false
    }

    deinit { stop() }
}
