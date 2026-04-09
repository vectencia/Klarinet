import SwiftUI

struct ContentView: View {
    @State private var showCompose = false
    @State private var showSwiftUI = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 32) {
                Spacer()

                VStack(spacing: 8) {
                    Text("Klarinet")
                        .font(.largeTitle.bold())
                    Text("Audio SDK Demo")
                        .font(.title3)
                        .foregroundStyle(.secondary)
                }

                Spacer()

                VStack(spacing: 16) {
                    Button {
                        showCompose = true
                    } label: {
                        Label("Compose Multiplatform", systemImage: "apps.iphone")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)

                    Button {
                        showSwiftUI = true
                    } label: {
                        Label("SwiftUI", systemImage: "swift")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.large)
                }
                .padding(.horizontal, 32)

                Spacer()
            }
            .fullScreenCover(isPresented: $showCompose) {
                ComposeView().ignoresSafeArea()
            }
            .navigationDestination(isPresented: $showSwiftUI) {
                SwiftUIDemoView()
            }
        }
    }
}
