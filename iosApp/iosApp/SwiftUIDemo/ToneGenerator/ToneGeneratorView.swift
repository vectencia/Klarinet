import SwiftUI

struct ToneGeneratorView: View {
    @StateObject private var viewModel = ToneGeneratorViewModel()

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Header
                VStack(spacing: 4) {
                    Image(systemName: "waveform")
                        .font(.system(size: 40))
                        .foregroundStyle(.tint)
                    Text("Tone Generator")
                        .font(.title2.bold())
                    Text("Generate a sine wave at a configurable frequency")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(.top, 8)

                // Frequency card
                VStack(spacing: 12) {
                    HStack {
                        Image(systemName: "tuningfork")
                            .foregroundStyle(.secondary)
                        Text("Frequency")
                            .font(.headline)
                        Spacer()
                        Text("\(Int(viewModel.frequency)) Hz")
                            .font(.title3.monospacedDigit().bold())
                            .foregroundStyle(.tint)
                    }

                    Slider(
                        value: $viewModel.frequency,
                        in: 220...880,
                        step: 1
                    )

                    HStack {
                        Text("220 Hz")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                        Spacer()
                        Text("880 Hz")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
                .padding()
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))

                // Play / Stop button
                Button {
                    viewModel.togglePlayback()
                } label: {
                    Label(
                        viewModel.isPlaying ? "Stop" : "Play",
                        systemImage: viewModel.isPlaying ? "stop.circle.fill" : "play.circle.fill"
                    )
                    .font(.title3.bold())
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                }
                .buttonStyle(.borderedProminent)
                .tint(viewModel.isPlaying ? .red : .accentColor)

                // State info
                HStack {
                    Image(systemName: "info.circle")
                        .foregroundStyle(.secondary)
                    Text("Stream State")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Spacer()
                    Text(viewModel.streamState)
                        .font(.subheadline.monospaced())
                }
                .padding()
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))

            }
            .padding()
        }
    }
}
