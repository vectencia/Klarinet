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
            VStack(alignment: .leading, spacing: 4) {
                Text("Klarinet")
                    .font(.largeTitle.weight(.semibold))
                Text("Studio demo")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20)
            .padding(.top, 8)

            if session.interrupted {
                banner("Interrupted")
            }
            if let route = session.routeLabel {
                banner(route)
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(titles.indices, id: \.self) { index in
                        Button(titles[index]) {
                            withAnimation(.spring(response: 0.42, dampingFraction: 0.86)) {
                                screen = index
                            }
                        }
                        .buttonStyle(.plain)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(screen == index ? Color.accentColor : Color.primary.opacity(0.08), in: Capsule())
                        .foregroundStyle(screen == index ? Color.black : Color.primary)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
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
        .background(Color.black.opacity(0.04).ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .navigationTitle("SwiftUI Demo")
    }

    private func banner(_ text: String) -> some View {
        Text(text)
            .font(.subheadline.weight(.medium))
            .frame(maxWidth: .infinity)
            .padding(10)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .padding(.horizontal, 16)
            .padding(.top, 6)
            .transition(.move(edge: .top).combined(with: .opacity))
    }

    private func pane<Content: View>(_ index: Int, @ViewBuilder content: () -> Content) -> some View {
        content()
            .opacity(screen == index ? 1 : 0)
            .offset(x: screen == index ? 0 : 12)
            .animation(.spring(response: 0.42, dampingFraction: 0.86), value: screen)
            .zIndex(screen == index ? 1 : 0)
            .allowsHitTesting(screen == index)
            .accessibilityHidden(screen != index)
    }
}
