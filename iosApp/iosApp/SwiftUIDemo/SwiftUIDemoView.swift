import SwiftUI

struct SwiftUIDemoView: View {
    @State private var screen = 0
    @ObservedObject private var session = DemoSession.shared
    @StateObject private var tone = ToneGeneratorViewModel()
    @StateObject private var mic = MicMeterViewModel()
    @StateObject private var latency = LatencyViewModel()
    @StateObject private var file = FilePlayerViewModel()
    @StateObject private var effects = EffectsViewModel()
    @StateObject private var scenes = ScenesViewModel()
    private let titles = ["Tone Gen", "Mic Meter", "Latency", "File", "Effects", "Scenes"]

    var body: some View {
        VStack(spacing: 0) {
            if session.interrupted {
                Text("Interrupted")
                    .frame(maxWidth: .infinity)
                    .padding(8)
                    .background(Color.yellow.opacity(0.35))
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack {
                    ForEach(titles.indices, id: \.self) { index in
                        Button(titles[index]) { screen = index }
                            .buttonStyle(.borderedProminent)
                            .tint(screen == index ? Color.accentColor : Color.gray)
                    }
                }
                .padding(.horizontal)
                .padding(.vertical, 8)
            }
            ZStack {
                pane(0) { ToneGeneratorView(viewModel: tone) }
                pane(1) { MicMeterView(viewModel: mic) }
                pane(2) { LatencyView(viewModel: latency) }
                pane(3) { FilePlayerView(viewModel: file) }
                pane(4) { EffectsView(viewModel: effects) }
                pane(5) { ScenesView(viewModel: scenes) }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .navigationBarBackButtonHidden(false)
        .navigationTitle("SwiftUI Demo")
    }

    private func pane<Content: View>(_ index: Int, @ViewBuilder content: () -> Content) -> some View {
        content()
            .opacity(screen == index ? 1 : 0)
            .zIndex(screen == index ? 1 : 0)
            .allowsHitTesting(screen == index)
            .accessibilityHidden(screen != index)
    }
}
