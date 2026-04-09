import SwiftUI

struct MicMeterView: View {
    @StateObject private var viewModel = MicMeterViewModel()

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Header
                VStack(spacing: 4) {
                    Image(systemName: "mic.fill")
                        .font(.system(size: 40))
                        .foregroundStyle(.tint)
                    Text("Mic Meter")
                        .font(.title2.bold())
                    Text("Monitor microphone input level in real time")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(.top, 8)

                // VU Meter
                VStack(spacing: 12) {
                    Text("Input Level")
                        .font(.headline)

                    // Level bar
                    VUMeterBar(level: viewModel.level)
                        .frame(height: 32)

                    // Level percentage
                    HStack {
                        Text("Level")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        Spacer()
                        Text("\(Int(viewModel.level * 100))%")
                            .font(.title2.monospacedDigit().bold())
                            .foregroundStyle(meterColor(for: viewModel.level))
                    }
                }
                .padding()
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))

                // Record / Stop button
                Button {
                    viewModel.toggleRecording()
                } label: {
                    Label(
                        viewModel.isRecording ? "Stop" : "Record",
                        systemImage: viewModel.isRecording ? "stop.circle.fill" : "record.circle"
                    )
                    .font(.title3.bold())
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                }
                .buttonStyle(.borderedProminent)
                .tint(viewModel.isRecording ? .red : .accentColor)

                // Latency info
                HStack {
                    Image(systemName: "timer")
                        .foregroundStyle(.secondary)
                    Text("Input Latency")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Spacer()
                    Text(viewModel.inputLatency)
                        .font(.subheadline.monospaced())
                }
                .padding()
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))

            }
            .padding()
        }
    }

    private func meterColor(for level: Float) -> Color {
        if level > 0.9 { return .red }
        if level > 0.6 { return .yellow }
        return .green
    }
}

// MARK: - VU Meter Bar

private struct VUMeterBar: View {
    let level: Float

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .leading) {
                // Background
                RoundedRectangle(cornerRadius: 6)
                    .fill(Color(.systemGray5))

                // Level fill
                RoundedRectangle(cornerRadius: 6)
                    .fill(levelGradient)
                    .frame(width: max(0, geometry.size.width * CGFloat(level)))
                    .animation(.linear(duration: 0.05), value: level)
            }
        }
    }

    private var levelGradient: LinearGradient {
        LinearGradient(
            colors: [.green, .green, .yellow, .red],
            startPoint: .leading,
            endPoint: .trailing
        )
    }
}
