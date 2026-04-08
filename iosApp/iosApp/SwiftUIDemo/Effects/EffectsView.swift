import SwiftUI

struct EffectsView: View {
    @StateObject private var viewModel = EffectsViewModel()

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Header
                VStack(spacing: 4) {
                    Image(systemName: "slider.horizontal.3")
                        .font(.system(size: 40))
                        .foregroundStyle(.tint)
                    Text("Effects Chain")
                        .font(.title2.bold())
                    Text("Apply Gain, Delay, and Reverb to a 440 Hz tone")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(.top, 8)

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

                // Output level meter
                if viewModel.isPlaying {
                    VStack(spacing: 8) {
                        HStack {
                            Image(systemName: "speaker.wave.3")
                                .foregroundStyle(.secondary)
                            Text("Output Level")
                                .font(.subheadline)
                            Spacer()
                            Text("\(Int(viewModel.outputLevel * 100))%")
                                .font(.subheadline.monospacedDigit())
                        }
                        OutputLevelBar(level: viewModel.outputLevel)
                            .frame(height: 12)
                    }
                    .padding()
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
                }

                // Gain effect card
                EffectCard(
                    name: "Gain",
                    icon: "speaker.wave.2",
                    enabled: viewModel.gainEnabled,
                    onEnabledChange: { viewModel.updateGainEnabled($0) }
                ) {
                    ParameterRow(
                        label: "Volume",
                        value: viewModel.gainDb,
                        range: -24...12,
                        format: { "\(Int($0)) dB" },
                        onChange: { viewModel.updateGainDb($0) }
                    )
                }

                // Delay effect card
                EffectCard(
                    name: "Delay",
                    icon: "repeat",
                    enabled: viewModel.delayEnabled,
                    onEnabledChange: { viewModel.updateDelayEnabled($0) }
                ) {
                    ParameterRow(
                        label: "Time",
                        value: viewModel.delayTimeMs,
                        range: 10...1000,
                        format: { "\(Int($0)) ms" },
                        onChange: { viewModel.updateDelayTimeMs($0) }
                    )
                    ParameterRow(
                        label: "Feedback",
                        value: viewModel.delayFeedback,
                        range: 0...0.95,
                        format: { String(format: "%.2f", $0) },
                        onChange: { viewModel.updateDelayFeedback($0) }
                    )
                    ParameterRow(
                        label: "Mix",
                        value: viewModel.delayMix,
                        range: 0...1,
                        format: { String(format: "%.2f", $0) },
                        onChange: { viewModel.updateDelayMix($0) }
                    )
                }

                // Reverb effect card
                EffectCard(
                    name: "Reverb",
                    icon: "waveform.path",
                    enabled: viewModel.reverbEnabled,
                    onEnabledChange: { viewModel.updateReverbEnabled($0) }
                ) {
                    ParameterRow(
                        label: "Room Size",
                        value: viewModel.reverbRoomSize,
                        range: 0...1,
                        format: { String(format: "%.2f", $0) },
                        onChange: { viewModel.updateReverbRoomSize($0) }
                    )
                    ParameterRow(
                        label: "Damping",
                        value: viewModel.reverbDamping,
                        range: 0...1,
                        format: { String(format: "%.2f", $0) },
                        onChange: { viewModel.updateReverbDamping($0) }
                    )
                    ParameterRow(
                        label: "Mix",
                        value: viewModel.reverbMix,
                        range: 0...1,
                        format: { String(format: "%.2f", $0) },
                        onChange: { viewModel.updateReverbMix($0) }
                    )
                }

            }
            .padding()
        }
    }
}

// MARK: - Effect Card

private struct EffectCard<Content: View>: View {
    let name: String
    let icon: String
    let enabled: Bool
    let onEnabledChange: (Bool) -> Void
    @ViewBuilder let content: Content

    var body: some View {
        VStack(spacing: 12) {
            HStack {
                Image(systemName: icon)
                    .foregroundColor(enabled ? .accentColor : .secondary)
                Text(name)
                    .font(.headline)
                Spacer()
                Toggle("", isOn: Binding(
                    get: { enabled },
                    set: { onEnabledChange($0) }
                ))
                .labelsHidden()
            }

            if enabled {
                content
            }
        }
        .padding()
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
        .opacity(enabled ? 1.0 : 0.7)
    }
}

// MARK: - Parameter Row

private struct ParameterRow: View {
    let label: String
    let value: Float
    let range: ClosedRange<Float>
    let format: (Float) -> String
    let onChange: (Float) -> Void

    var body: some View {
        VStack(spacing: 4) {
            HStack {
                Text(label)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Spacer()
                Text(format(value))
                    .font(.subheadline.monospacedDigit())
            }
            Slider(
                value: Binding(
                    get: { value },
                    set: { onChange($0) }
                ),
                in: range
            )
        }
    }
}

// MARK: - Output Level Bar

private struct OutputLevelBar: View {
    let level: Float

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .leading) {
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color.gray.opacity(0.3))

                RoundedRectangle(cornerRadius: 4)
                    .fill(
                        LinearGradient(
                            colors: [.green, .yellow, .orange],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .frame(width: max(0, geometry.size.width * CGFloat(level)))
                    .animation(.linear(duration: 0.05), value: level)
            }
        }
    }
}
