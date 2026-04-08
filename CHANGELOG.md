# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `AudioFileReader` for decoding WAV, MP3, AAC, M4A files to PCM buffers
- `AudioFileWriter` for encoding PCM buffers to WAV, AAC, M4A files
- `AudioFileInfo` and `AudioFileTags` for file metadata and tag reading/writing
- `AudioEngine.playFile()` convenience extension for file playback
- `AudioEngine.recordToFile()` convenience extension for recording to file
- `AudioFileFormat` enum (WAV, MP3, AAC, M4A)
- `UnsupportedFormatException` and `AudioFileException` error types
- File Player screen in demo app
- **koboe**: Common audio API with `AudioEngine`, `AudioPlayer`, `AudioRecorder`, and `AudioFormat`
- **koboe**: `AudioBuffer` for efficient sample data management
- **koboe**: `AudioState` and `AudioException` for consistent state management and error handling
- **koboe**: Sample rate, channel count, and bit depth configuration
- **koboe**: Android implementation using `AudioTrack` and `AudioRecord`
- **koboe**: Low-latency playback support via performance mode hints
- **koboe**: iOS/iPadOS implementation using AVAudioEngine
- **koboe**: macOS implementation using AVAudioEngine
- **koboe**: tvOS target support
- **koboe**: Audio session category and routing configuration
- **demo**: Demo application with playback and recording examples
- CI workflow with GitHub Actions (build, test across platforms)
- Release workflow for Maven Central publishing
- Dependabot configuration for dependency updates
