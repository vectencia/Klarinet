import SwiftUI

struct SwiftUIDemoView: View {
    var body: some View {
        TabView {
            ToneGeneratorView()
                .tabItem {
                    Label("Tone Gen", systemImage: "waveform")
                }

            MicMeterView()
                .tabItem {
                    Label("Mic Meter", systemImage: "mic")
                }

            LatencyView()
                .tabItem {
                    Label("Latency", systemImage: "timer")
                }

            FilePlayerView()
                .tabItem {
                    Label("File", systemImage: "doc.text")
                }

            EffectsView()
                .tabItem {
                    Label("Effects", systemImage: "slider.horizontal.3")
                }
        }
        .navigationBarBackButtonHidden(false)
        .navigationTitle("SwiftUI Demo")
    }
}
