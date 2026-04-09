import SwiftUI

struct FilePlayerView: View {
    @StateObject private var viewModel = FilePlayerViewModel()

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Header
                VStack(spacing: 4) {
                    Image(systemName: "doc.text.fill")
                        .font(.system(size: 40))
                        .foregroundStyle(.tint)
                    Text("File Player")
                        .font(.title2.bold())
                    Text("Load and play audio files from disk")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(.top, 8)

                // File path input
                VStack(spacing: 12) {
                    HStack {
                        Image(systemName: "folder")
                            .foregroundStyle(.secondary)
                        Text("Audio File")
                            .font(.headline)
                        Spacer()
                    }

                    TextField("Enter file path...", text: $viewModel.filePath)
                        .textFieldStyle(.roundedBorder)
                        .font(.system(.body, design: .monospaced))
                        .autocorrectionDisabled()

                    Button {
                        viewModel.loadFile()
                    } label: {
                        Label("Load File Info", systemImage: "arrow.down.doc")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                }
                .padding()
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))

                // File info card
                if viewModel.hasFileInfo {
                    VStack(spacing: 0) {
                        HStack {
                            Image(systemName: "info.circle.fill")
                                .foregroundStyle(.tint)
                            Text("File Information")
                                .font(.headline)
                            Spacer()
                        }
                        .padding(.horizontal)
                        .padding(.top, 12)
                        .padding(.bottom, 8)

                        Divider()

                        FileInfoRow(label: "Format", value: viewModel.fileFormat)
                        Divider().padding(.leading, 16)
                        FileInfoRow(label: "Duration", value: viewModel.duration)
                        Divider().padding(.leading, 16)
                        FileInfoRow(label: "Sample Rate", value: viewModel.fileSampleRate)
                        Divider().padding(.leading, 16)
                        FileInfoRow(label: "Channels", value: viewModel.channels)
                        Divider().padding(.leading, 16)
                        FileInfoRow(label: "Bit Rate", value: viewModel.bitRate)
                    }
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
                }

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
                .disabled(viewModel.filePath.isEmpty)

                // Error message
                if let error = viewModel.errorMessage {
                    HStack(alignment: .top) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundStyle(.yellow)
                        Text(error)
                            .font(.caption)
                            .foregroundStyle(.red)
                    }
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.red.opacity(0.1), in: RoundedRectangle(cornerRadius: 12))
                }
            }
            .padding()
        }
    }
}

// MARK: - File Info Row

private struct FileInfoRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack {
            Text(label)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Spacer()
            Text(value)
                .font(.subheadline.monospaced())
        }
        .padding(.horizontal)
        .padding(.vertical, 10)
    }
}
