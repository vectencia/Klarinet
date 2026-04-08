import SwiftUI

struct LatencyView: View {
    @StateObject private var viewModel = LatencyViewModel()

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Header
                VStack(spacing: 4) {
                    Image(systemName: "timer")
                        .font(.system(size: 40))
                        .foregroundStyle(.tint)
                    Text("Latency Info")
                        .font(.title2.bold())
                    Text("Measure audio stream latency and configuration")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(.top, 8)

                // Latency card
                VStack(spacing: 0) {
                    InfoRow(
                        icon: "arrow.right.circle",
                        label: "Output Latency",
                        value: viewModel.outputLatency
                    )
                    Divider().padding(.leading, 40)
                    InfoRow(
                        icon: "arrow.left.circle",
                        label: "Input Latency",
                        value: viewModel.inputLatency
                    )
                }
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))

                // Stream config card
                VStack(spacing: 0) {
                    InfoRow(
                        icon: "waveform",
                        label: "Sample Rate",
                        value: viewModel.sampleRate
                    )
                    Divider().padding(.leading, 40)
                    InfoRow(
                        icon: "memorychip",
                        label: "Buffer Size",
                        value: viewModel.bufferSize
                    )
                    Divider().padding(.leading, 40)
                    InfoRow(
                        icon: "speedometer",
                        label: "Performance Mode",
                        value: viewModel.performanceMode
                    )
                }
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))

                // Measure / Stop button
                Button {
                    viewModel.toggleMeasure()
                } label: {
                    Label(
                        viewModel.isMeasuring ? "Stop" : "Measure",
                        systemImage: viewModel.isMeasuring ? "stop.circle.fill" : "gauge.medium"
                    )
                    .font(.title3.bold())
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                }
                .buttonStyle(.borderedProminent)
                .tint(viewModel.isMeasuring ? .red : .accentColor)

            }
            .padding()
        }
    }
}

// MARK: - Info Row

private struct InfoRow: View {
    let icon: String
    let label: String
    let value: String

    var body: some View {
        HStack {
            Image(systemName: icon)
                .foregroundStyle(.secondary)
                .frame(width: 24)
            Text(label)
                .font(.subheadline)
            Spacer()
            Text(value)
                .font(.subheadline.monospaced())
                .foregroundStyle(.primary)
        }
        .padding(.horizontal)
        .padding(.vertical, 12)
    }
}
