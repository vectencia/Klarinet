# Klarinet

[![CI](https://github.com/vectencia/klarinet/actions/workflows/ci.yml/badge.svg)](https://github.com/vectencia/klarinet/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.vectencia.klarinet/klarinet)](https://central.sonatype.com/namespace/com.vectencia.klarinet)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**Klarinet** is a Kotlin Multiplatform audio library that provides a unified API for low-latency audio playback and recording across Android and Apple platforms. It bridges platform-native audio engines behind a single, idiomatic Kotlin API so you can write audio code once and run it everywhere.

## Why Klarinet?

Writing cross-platform audio code today means maintaining separate implementations for Android (AudioTrack/Oboe) and Apple (AVAudioEngine/Core Audio), each with different threading models, buffer management, and lifecycle semantics. Klarinet eliminates this duplication with a common API that delegates to the best native backend on each platform, while preserving low-latency characteristics and real-time safety.

## Supported Platforms

| Platform | Backend | Status |
|---|---|---|
| Android (API 24+) | Google Oboe (AAudio / OpenSL ES) | Supported |
| iOS / iPadOS | AVAudioEngine | Supported |
| macOS | AVAudioEngine | Supported |
| tvOS | AVAudioEngine | Supported |

## Setup

Add the dependency to your KMP module:

```kotlin
// settings.gradle.kts or build.gradle.kts repositories
repositories {
    mavenCentral()
}
```

```kotlin
// shared/build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.vectencia.klarinet:klarinet:0.0.1")
        }
    }
}
```

## Quick Start

### Playback (Sine Wave)

```kotlin
import com.vectencia.klarinet.*
import kotlin.math.sin

val engine = AudioEngine.create()

// Generate a 440 Hz sine wave via callback
val callback = object : AudioStreamCallback {
    private var phase = 0.0
    private val frequency = 440.0

    override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
        val sampleRate = 48000.0
        for (i in 0 until numFrames) {
            buffer[i] = sin(2.0 * Math.PI * frequency * phase / sampleRate).toFloat()
            phase += 1.0
        }
        return numFrames
    }
}

val stream = engine.openStream(
    config = AudioStreamConfig(
        sampleRate = 48000,
        channelCount = 1,
        direction = StreamDirection.OUTPUT,
        performanceMode = PerformanceMode.LOW_LATENCY,
    ),
    callback = callback,
)

stream.start()
// ... audio is playing ...
stream.stop()
stream.close()
engine.release()
```

### Recording

```kotlin
import com.vectencia.klarinet.*

val engine = AudioEngine.create()

val stream = engine.openStream(
    config = AudioStreamConfig(
        sampleRate = 48000,
        channelCount = 1,
        direction = StreamDirection.INPUT,
    ),
)

stream.start()

val buffer = FloatArray(1024)
val framesRead = stream.read(buffer, numFrames = 1024)
// Process recorded audio in `buffer`...

stream.stop()
stream.close()
engine.release()
```

### File I/O

#### Reading file metadata and tags

```kotlin
import com.vectencia.klarinet.*

val reader = AudioFileReader("/path/to/song.mp3")
val info = reader.info

println("Format: ${info.format}")           // MP3
println("Duration: ${info.durationMs} ms")  // 210000
println("Sample rate: ${info.sampleRate}")   // 44100
println("Channels: ${info.channelCount}")    // 2
println("Bit rate: ${info.bitRate}")         // 320000

val tags = info.tags
println("Title: ${tags.title}")   // "My Song"
println("Artist: ${tags.artist}") // "Artist Name"
println("Album: ${tags.album}")   // "Album Name"

reader.close()
```

#### Decoding a file to PCM

```kotlin
import com.vectencia.klarinet.*

val reader = AudioFileReader("/path/to/audio.wav")

// Read all samples at once
val allSamples = reader.readAll() // FloatArray of interleaved PCM [-1.0, 1.0]

// Or read in chunks
reader.seekTo(0)
while (!reader.isAtEnd) {
    val chunk = reader.readFrames(maxFrames = 4096)
    // Process chunk...
}

reader.close()
```

#### Playing a file with AudioEngine.playFile()

```kotlin
import com.vectencia.klarinet.*

val engine = AudioEngine.create()
val stream = engine.playFile("/path/to/song.mp3")
stream.start()
// ... audio is playing ...
stream.stop()
stream.close()
engine.release()
```

#### Recording to file with AudioEngine.recordToFile()

```kotlin
import com.vectencia.klarinet.*

val engine = AudioEngine.create()
val stream = engine.recordToFile(
    filePath = "/path/to/recording.wav",
    format = AudioFileFormat.WAV,
)
stream.start()
// ... recording ...
stream.stop()
stream.close()
engine.release()
```

### Audio Effects

```kotlin
import com.vectencia.klarinet.*

val engine = AudioEngine.create()

// Create effects
val reverb = engine.createEffect(AudioEffectType.REVERB)
reverb.setParameter(ReverbParams.ROOM_SIZE, 0.7f)
reverb.setParameter(ReverbParams.WET_DRY_MIX, 0.3f)

val compressor = engine.createEffect(AudioEffectType.COMPRESSOR)
compressor.setParameter(CompressorParams.THRESHOLD, -20f)
compressor.setParameter(CompressorParams.RATIO, 4f)

// Build an effect chain
val chain = engine.createEffectChain()
chain.add(compressor)
chain.add(reverb)

// Attach to a stream
val stream = engine.openStream(config, callback)
stream.effectChain = chain
stream.start()

// Update parameters in real-time (lock-free)
reverb.setParameter(ReverbParams.WET_DRY_MIX, 0.6f)

// Hot-swap: add/remove effects while playing
chain.add(engine.createEffect(AudioEffectType.DELAY))
chain.remove(compressor)

stream.stop()
stream.close()
engine.release()
```

## Architecture

```mermaid
graph TD
    A[Your App] --> B[klarinet]
    B --> C[Oboe / AAudio / OpenSL ES]
    B --> D[AVAudioEngine / Core Audio]
```

**klarinet** is a single Kotlin Multiplatform module that defines the common API (`AudioEngine`, `AudioStream`, `AudioStreamConfig`, `AudioStreamCallback`) as `expect` declarations in `commonMain`, with `actual` implementations in `androidMain` (backed by Google Oboe via JNI/C++) and `appleMain` (backed by AVAudioEngine).

## Modules

| Module | Artifact | Description |
|---|---|---|
| `klarinet` | `com.vectencia.klarinet:klarinet` | KMP audio SDK: common API + Android and Apple backends |
| `demo` | -- | Demo app with playback and recording examples |

## Features

- Playback and recording streams with callback and blocking I/O modes
- Low-latency performance mode
- PCM Float, I16, I24, I32 sample formats
- Mono and stereo channel configurations
- Audio device enumeration and default device selection
- Audio session management (Apple platforms)
- Stream state lifecycle with error callbacks
- Latency measurement via `LatencyInfo`
- Audio route change notifications
- Audio file I/O: decode (WAV, MP3, AAC, M4A) and encode (WAV, AAC, M4A) via `AudioFileReader` / `AudioFileWriter`
- Convenience extensions: `AudioEngine.playFile()` and `AudioEngine.recordToFile()`
- File metadata and tag reading via `AudioFileInfo` / `AudioFileTags`
- Real-time audio effects via shared C++ DSP core: 16 built-in effects (Gain, Pan, Compressor, Limiter, NoiseGate, ParametricEQ, LPF, HPF, BPF, Delay, Reverb, Chorus, Flanger, Phaser, Tremolo, Mute/Solo)
- `AudioEffectChain` with hot-swap support (add/remove/reorder effects while streaming)
- Lock-free parameter updates via atomics and ring buffer for batch changes
- Kotlin DSL control plane with `AudioEffect`, `AudioEffectChain`, and parameter constants
- Android: API 24+ with Google Oboe (AAudio / OpenSL ES) via JNI/C++
- Apple: iOS, iPadOS, macOS, tvOS via AVAudioEngine

### Not Included (Future)

- Node graph topology (effects use linear chain for now)
- Sidechain input
- MIDI support
- Desktop JVM (Windows, Linux) targets
- Web (Kotlin/JS, Kotlin/Wasm) targets

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, build commands, and PR guidelines.

## License

```
Copyright 2026 Vectencia

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
