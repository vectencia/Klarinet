import SwiftUI
import Klarinet

struct ToneGeneratorView: View {
    @StateObject private var viewModel = ToneGeneratorViewModel()

    var body: some View {
        VStack(spacing: 40) {
            Image(systemName: "waveform")
                .font(.system(size: 60))
                .foregroundStyle(.tint)

            Text("Klarinet — tvOS Demo")
                .font(.title)

            Text("Frequency: \(Int(viewModel.frequency)) Hz")
                .font(.title2.monospacedDigit())

            HStack(spacing: 40) {
                Button("220 Hz") { viewModel.frequency = 220 }
                Button("440 Hz") { viewModel.frequency = 440 }
                Button("660 Hz") { viewModel.frequency = 660 }
                Button("880 Hz") { viewModel.frequency = 880 }
            }

            Button {
                viewModel.togglePlayback()
            } label: {
                Label(
                    viewModel.isPlaying ? "Stop" : "Play",
                    systemImage: viewModel.isPlaying ? "stop.circle.fill" : "play.circle.fill"
                )
                .font(.title2)
            }
            .buttonStyle(.borderedProminent)
            .tint(viewModel.isPlaying ? .red : .accentColor)

            Text("Stream: \(viewModel.streamState)")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding()
    }
}
