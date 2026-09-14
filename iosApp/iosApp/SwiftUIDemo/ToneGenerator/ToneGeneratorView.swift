import SwiftUI
import ComposeApp

struct ToneGeneratorView: View {
    @ObservedObject var viewModel: ToneGeneratorViewModel

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
                    Text("\(viewModel.streamState)  xruns \(viewModel.xruns)")
                        .font(.subheadline.monospaced())
                }
                .padding()
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))

                VStack(spacing: 12) {
                    HStack {
                        Button("5 s") { viewModel.sleepDurationMs = 5_000 }
                        Button("10 s") { viewModel.sleepDurationMs = 10_000 }
                        Button("30 s") { viewModel.sleepDurationMs = 30_000 }
                    }
                    HStack {
                        Button("0.5 s") { viewModel.sleepFadeMs = 500 }
                        Button("1 s") { viewModel.sleepFadeMs = 1_000 }
                        Button("2 s") { viewModel.sleepFadeMs = 2_000 }
                    }
                    HStack {
                        Button("Schedule") { viewModel.scheduleSleep() }
                            .disabled(!viewModel.isPlaying)
                        Button("Pause") { viewModel.pauseSleep() }
                            .disabled(!viewModel.isPlaying)
                        Button("Resume") { viewModel.resumeSleep() }
                            .disabled(!viewModel.isPlaying)
                        Button("Cancel") { viewModel.cancelSleep() }
                            .disabled(!viewModel.isPlaying)
                    }
                    Text("Timer: \(viewModel.sleepState.name) remaining \(viewModel.sleepRemainingMs) ms")
                        .font(.subheadline)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding()
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))

            }
            .padding()
        }
    }
}
