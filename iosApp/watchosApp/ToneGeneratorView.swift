import SwiftUI
import Klarinet

struct ToneGeneratorView: View {
    @StateObject private var viewModel = ToneGeneratorViewModel()

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                Image(systemName: "waveform")
                    .font(.title)
                    .foregroundStyle(.tint)

                Text("\(Int(viewModel.frequency)) Hz")
                    .font(.title3.monospacedDigit().bold())

                HStack {
                    Button("−") {
                        viewModel.frequency = max(220, viewModel.frequency - 110)
                    }
                    Button("+") {
                        viewModel.frequency = min(880, viewModel.frequency + 110)
                    }
                }

                Button {
                    viewModel.togglePlayback()
                } label: {
                    Image(systemName: viewModel.isPlaying ? "stop.fill" : "play.fill")
                }
                .buttonStyle(.borderedProminent)
                .tint(viewModel.isPlaying ? .red : .accentColor)

                Text(viewModel.streamState)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
    }
}
