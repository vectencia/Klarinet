import SwiftUI

struct ContentView: View {
    @State private var showCompose = false
    @State private var showSwiftUI = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 32) {
                Spacer()

                VStack(alignment: .leading, spacing: 8) {
                    Text("Klarinet")
                        .font(.largeTitle.weight(.semibold))
                    Text("Audio SDK demo")
                        .font(.title3)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 32)

                Spacer()

                VStack(spacing: 16) {
                    Button {
                        withAnimation(.spring(response: 0.42, dampingFraction: 0.86)) {
                            showCompose = true
                        }
                    } label: {
                        Label("Compose Multiplatform", systemImage: "apps.iphone")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)

                    Button {
                        withAnimation(.spring(response: 0.42, dampingFraction: 0.86)) {
                            showSwiftUI = true
                        }
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
