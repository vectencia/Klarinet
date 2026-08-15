# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Kotlin/JS browser target for `klarinet` and `klarinet-coroutines`
- Web Audio `AudioEngine` / `AudioStream` (`ScriptProcessorNode`, `getUserMedia` for input)
- Web Audio effect graphs mapped from Klarinet Studio (gain, pan, dynamics, EQ, delay, reverb, modulation)
- In-memory `AudioFileWriter` (WAV) and `AudioFileReader` after `decodeAudioFile`, `putWavBytes`, or `putAudioFile`

### Changed

- `klarinet-coroutines` file helpers default to a platform IO dispatcher (`Dispatchers.Default` on JS, where `Dispatchers.IO` does not exist)

## [0.3.0] - 2026-08-15

### Added

- JVM desktop JAR now ships `libklarinet_jvm` for macOS (arm64/x64), Linux (x64/arm64), and Windows (x64)
- `./gradlew :klarinet:buildJvmNatives` cross-compiles those libraries (Zig for Linux/Windows)
- Apple `getAvailableDevices()` lists real Core Audio / AVAudioSession ports and `deviceId` selects them

### Changed

- JVM desktop effects now run the shared C++ DSP core on the miniaudio callback instead of a Kotlin parameter map
- Native desktop (Linux / Windows) effects call the same `klarinet_dsp` C API instead of a Kotlin parameter map
- Apple effects run the shared C++ DSP core on the AVAudioEngine render/tap path
- Kotlin `onAudioReady` runs on a worker thread on Apple, JVM, and native desktop; the audio callback only does FIFO + C++ DSP
- Dropped the Kotlin/Native `macosX64` target; Intel Macs keep using the JVM desktop artifact

### Build

- Removed the extra Android `singleVariant("release")` block; vanniktech already publishes that variant

## [0.2.0] - 2026-08-15

### Added

- `AutoCloseable` on `AudioEngine`, `AudioStream`, `AudioEffect`, and `AudioEffectChain` (`use {}` / `close()`)
- `ResourceReleasedException` for operations on released engines, streams, effects, and chains
- `AudioStreamConfig.deviceId` to open a stream on a specific `AudioDeviceInfo`

### Changed

- `AudioStream.stateFlow` is now a cold `Flow` function instead of a `StateFlow` property, so collectors no longer leak an unstructured polling coroutine
- Released engines and closed streams throw `ResourceReleasedException` instead of `IllegalStateException` or crashing in JNI
- Android, JVM, and native desktop backends honor `deviceId` when opening a stream
- Kotlin `2.3.20` → `2.4.10`
- Compose Multiplatform `1.10.3` → `1.11.1`
- Android Gradle Plugin `8.13.2` → `9.1.0` (maximum version fully supported by Kotlin 2.4.10)
- Android `compileSdk` `36` → `37` (required by AndroidX Lifecycle 2.11.0)
- Gradle `8.14.4` → `9.5.0`
- kotlinx-coroutines `1.10.2` → `1.11.0`
- JetBrains AndroidX Lifecycle `2.10.0` → `2.11.0`
- vanniktech maven-publish `0.36.0` → `0.37.0`
- `AtomicFloat` is now a common implementation on `kotlin.concurrent.atomics` instead of per-platform expect/actual wrappers
- Removed unused `androidx.appcompat` and `androidx.core:core-ktx` catalog entries
- Fixed `klarienet` typo in the version catalog

### Build

- Extracted the Android Oboe/C++ JNI backend into `:klarinet-android` (`com.android.library`) so native CMake can stay on a regular Android library
- Publish that JNI module as `com.vectencia.klarinet:klarinet-jni` so it does not collide with the KMP `klarinet-android` target on Maven Central
- Migrated `:klarinet` and `:klarinet-coroutines` to `com.android.kotlin.multiplatform.library`
- Split the demo into `:demo` (shared Compose Multiplatform UI) and `:demo-android` (application entry point)
- Removed AGP 9 `android.builtInKotlin` / `android.newDsl` opt-outs
- Shared Linux and Windows native actuals in `nativeDesktopMain` instead of three copied source trees
- JVM tests for WAV encode/decode and live output-callback `peakLevel`; `./gradlew :klarinet:dspTests` runs the C++ DSP suite
- Android Oboe callback no longer enters the JVM: Kotlin `onAudioReady` runs on a worker thread behind a lock-free sample FIFO
- Dokka HTML API docs (`./gradlew :dokkaGenerate`) and a one-file JVM sine-wave sample (`./gradlew :sample:run`)

## [0.1.0] - 2026-04-16

### Added

- **watchOS**: Full audio streaming support via AVAudioEngine (watchosArm64, watchosSimulatorArm64)
- **watchOS**: `WatchosAudioSession` with full AVAudioSession integration
- **watchOS**: `AudioFileWriter` WAV support via posix I/O
- **watchOS**: `AudioStreamCallbackImpl` for Swift interop
- **JVM Desktop**: Full audio streaming support via miniaudio (macOS, Linux, Windows)
- **JVM Desktop**: `AudioFileReader` with WAV and MP3 decoding via miniaudio
- **JVM Desktop**: `AudioFileWriter` with WAV encoding via miniaudio
- **JVM Desktop**: `NativeLibLoader` for automatic OS/arch detection and native library extraction from JAR
- **Linux Native**: Audio streaming support via miniaudio cinterop (linuxX64, linuxArm64)
- **Linux Native**: File I/O (WAV read/write, MP3 read) via miniaudio cinterop
- **Windows Native**: Audio streaming support via miniaudio cinterop (mingwX64)
- **Windows Native**: File I/O (WAV read/write, MP3 read) via miniaudio cinterop
- **klarinet**: Thin C wrapper (`klarinet_native.h/c`) over miniaudio for K/N cinterop across Linux and Windows
- **klarinet**: `appleNonWatchMain` intermediate source set for platforms with ExtAudioFile APIs
- **klarinet-coroutines**: JVM target support
- **demo**: Compose Multiplatform Desktop target (macOS, Linux, Windows)
- **demo-native**: Native console demo apps for Linux and Windows (sine wave tone generator)
- **iosApp**: tvOS native SwiftUI demo (tone generator with frequency selection)
- **iosApp**: watchOS native SwiftUI demo (tone generator adapted for small screen)
- POM `url` and `scm` metadata for klibs.io indexing

### Changed

- `AudioStream.apple.kt`: `memset` size parameter uses `convert()` for portable `size_t` handling across all Apple targets
- Renamed `linuxTargets` to `nativeDesktopTargets` in build config to include mingwX64

### Platform Support

| Platform | Targets | Backend |
|---|---|---|
| Android | arm64-v8a, armeabi-v7a, x86_64 | Google Oboe |
| iOS / iPadOS | arm64, simulatorArm64, x64 | AVAudioEngine |
| macOS | arm64, x64 | AVAudioEngine |
| tvOS | arm64, simulatorArm64 | AVAudioEngine |
| watchOS | arm64, simulatorArm64 | AVAudioEngine |
| JVM Desktop | macOS, Linux, Windows | miniaudio |
| Linux Native | x64, arm64 | miniaudio |
| Windows Native | x64 | miniaudio |

## [0.0.2] - 2026-04-15

### Added

- **klarinet**: `AudioAnalyzer` for real-time audio analysis (RMS, peak, spectral centroid, dominant frequency)
- **klarinet**: `AudioAnalysisResult` data class with computed audio metrics
- **klarinet**: `AudioMath` utility with dB conversion, RMS, peak, and spectral helpers
- **klarinet**: `Fft` — pure-Kotlin radix-2 FFT implementation for spectral analysis
- **klarinet**: `ChunkedAudioFileWriter` for writing audio in fixed-size chunks with automatic file rotation
- **klarinet-coroutines**: `AnalyzingCallback` — wraps `AudioStreamCallback` to emit `AudioAnalysisResult` as a `SharedFlow`
- **demo**: Flag to swap between module and released Maven Central artifacts
- CI publish workflow for Maven Central via GitHub Actions

### Tests

- Unit tests for `AudioAnalyzer`, `AudioMath`, `Fft`, `ChunkedAudioFileWriter`, and `AnalyzingCallback`

## [0.0.1] - 2026-04-09

### Added

- **klarinet-coroutines**: Optional Kotlin Coroutines extensions module
- **klarinet-coroutines**: `AudioStream.stateFlow` and `awaitState()` for reactive state observation
- **klarinet-coroutines**: `AudioStream.levelFlow()` for real-time audio level metering via Flow
- **klarinet-coroutines**: `AudioFileReader.readAllSuspend()` and `readFramesSuspend()` for async file I/O
- **klarinet-coroutines**: `AudioFileReader.asFlow()` for streaming decoded audio as Flow
- **klarinet**: `AudioStream.peakLevel` property for lock-free audio level reading
- **klarinet**: Real-time audio effects via shared C++ DSP core with 16 built-in effects
- **klarinet**: `AudioEffect` and `AudioEffectChain` with hot-swap support (add/remove/reorder while streaming)
- **klarinet**: Built-in effects: Gain, Pan, Mute/Solo, Compressor, Limiter, NoiseGate, ParametricEQ (8-band), LowPassFilter, HighPassFilter, BandPassFilter, Delay, Reverb (Freeverb), Chorus, Flanger, Phaser, Tremolo
- **klarinet**: Lock-free parameter updates via `std::atomic` and SPSC ring buffer for batch changes
- **klarinet**: C++ DSP primitives: Biquad (IIR filter), CircularBuffer, LFO, EnvelopeFollower
- **klarinet**: `EffectFactory` and C API (`klarinet_dsp.h`) for Kotlin/Native cinterop
- **klarinet**: `AudioEffectType` enum, parameter ID constants for all effects
- **demo**: Effects Demo screen with real-time effect chain manipulation
- **klarinet**: Common audio API with `AudioEngine`, `AudioStream`, `AudioStreamConfig`, and `AudioStreamCallback`
- **klarinet**: `AudioDeviceInfo` for device enumeration and `LatencyInfo` for latency measurement
- **klarinet**: `StreamState` and `KlarinetException` hierarchy for consistent state management and error handling
- **klarinet**: `AudioFormat` (PCM_FLOAT, PCM_I16, PCM_I24, PCM_I32), `PerformanceMode`, `SharingMode`, `StreamDirection` enums
- **klarinet**: Sample rate, channel count, and buffer capacity configuration via `AudioStreamConfig`
- **klarinet**: Android implementation using Google Oboe (AAudio / OpenSL ES) via JNI/C++
- **klarinet**: Low-latency playback and recording with callback and blocking I/O modes
- **klarinet**: iOS/iPadOS implementation using AVAudioEngine
- **klarinet**: macOS implementation using AVAudioEngine
- **klarinet**: tvOS target support
- **klarinet**: `AudioSessionManager` with category and mode configuration (Apple platforms)
- **klarinet**: Audio route change notifications via `AudioRouteChangeInfo`
- **klarinet**: `AudioFileReader` for decoding WAV, MP3, AAC, M4A files to PCM buffers
- **klarinet**: `AudioFileWriter` for encoding PCM buffers to WAV, AAC, M4A files
- **klarinet**: `AudioFileInfo` and `AudioFileTags` for file metadata and tag reading
- **klarinet**: `AudioEngine.playFile()` convenience extension for file playback
- **klarinet**: `AudioEngine.recordToFile()` convenience extension for recording to file
- **klarinet**: `AudioFileFormat` enum (WAV, MP3, AAC, M4A)
- **klarinet**: `UnsupportedFormatException` and `AudioFileException` error types
- **demo**: Demo application with Tone Generator, Mic Meter, Latency Benchmark, and File Player screens
- CI workflow with GitHub Actions (build, test across platforms)
- Release workflow for Maven Central publishing
- Dependabot configuration for dependency updates
