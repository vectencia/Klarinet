import SwiftUI

struct ScenesView: View {
    @ObservedObject var viewModel: ScenesViewModel

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                Text("Scenes")
                    .font(.title)

                Text("Fade: \(Int(viewModel.fadeMs)) ms")
                    .font(.title3)
                Slider(value: $viewModel.fadeMs, in: 0...5_000)

                HStack {
                    Button("Low") { viewModel.transition(to: viewModel.low) }
                    Button("High") { viewModel.transition(to: viewModel.high) }
                }
                HStack {
                    Button("Stack") { viewModel.transition(to: viewModel.stack) }
                    Button("Low hall") { viewModel.transition(to: viewModel.lowHall) }
                }

                Button("Stop") { viewModel.stop() }

                Text("Current: \(viewModel.currentId ?? "")")
                    .font(.subheadline)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text("Layers: \(viewModel.layerIds.joined(separator: ", "))")
                    .font(.subheadline)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(viewModel.status)
                    .font(.subheadline)
                    .frame(maxWidth: .infinity, alignment: .leading)

                Text(viewModel.json)
                    .font(.caption)
                    .monospaced()
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding()
        }
    }
}
