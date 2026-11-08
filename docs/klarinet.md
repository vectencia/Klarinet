# Klarinet

Klarinet 0.6.0 is a Kotlin Multiplatform library for low-latency playback, recording, file I/O, and real-time effects. One Kotlin API covers 9 platforms and 14 KMP targets. Install coordinates, the platform table, and the API types are in [README.md](../README.md). Hosted API reference: [vectencia.github.io/Klarinet/api/](https://vectencia.github.io/Klarinet/api/). Release history: [CHANGELOG.md](../CHANGELOG.md).

## What it does

App code calls `AudioEngine`, `AudioStream`, `AudioEffect` / `AudioEffectChain`, `AudioFileReader`, `AudioFileWriter`, and `AudioSessionManager`. The optional `klarinet-coroutines` artifact adds Flows and suspending file I/O.

Each platform keeps its native I/O backend:

| Platform | Backend |
|---|---|
| Android | Google Oboe 1.11.0 (AAudio / OpenSL ES) |
| Apple (iOS, macOS, tvOS, watchOS) | AVAudioEngine |
| JVM, Linux, Windows | miniaudio |
| Browser | Web Audio |

The shared DSP core is Rust: queues, the effect chain, the sixteen effects, and callback offload. The audio callback does not allocate, lock, or enter Kotlin. On Android the Oboe callback never enters the JVM. Kotlin `onAudioReady` runs on a worker, and samples cross a lock-free FIFO. Effects still process on the audio thread. On JS, Gain runs in the wasm worklet. The other JS effects are Web Audio nodes, so their output differs from the Rust DSP.

File I/O follows the platform decoder. WAV encode works on every target that writes files. AAC/M4A encode works on Android and on Apple except watchOS. MP3 encode is unsupported. watchOS file reading throws `UnsupportedFormatException`. JVM and native desktop have no AAC/M4A. The browser keeps files in memory and decodes them before `AudioFileReader`. Push-mode `write` / `read` throw `StreamOperationException` where the platform cannot block.

`AudioSessionManager` sets the session category, takes Android audio focus, and reports interruptions. `hasRecordPermission()` and `requestRecordPermission()` cover the microphone. On API 37+, Android `observeRouteChanges` / `routeChangeFlow` emit `reason = "RoutingChanged"` when Oboe reports a routing change. Older Android versions disconnect the stream and do not emit that event.

The README lists the product use cases: music production, voice, games, analysis, accessibility, education, and podcast editing.

## What it fixes

Platform audio is four stacks, and a real-time callback cannot allocate, lock, or enter Kotlin. Klarinet keeps that split behind the types above and surfaces the failures:

- Apple input `openStream` throws `PermissionException` when microphone permission is missing. On Android, `start` / `pause` / `stop` throw `StreamOperationException` when Oboe returns an error, and a missing `RECORD_AUDIO` permission after `bind` throws `PermissionException`.
- Cancelling `routeChangeFlow()` removes the Apple `NSNotificationCenter` observer. The interruption observer is removed when no listeners or attached streams remain.
- `onStreamUnderrun` fires for a FIFO output underrun and for an input overrun.
- `AudioFileReader.asFlow` returns after `close()` instead of spinning.
- JS `playFile` resamples when the stream sample rate differs from the file.
- 0.6.0 keeps the published Kotlin API and moves the shared DSP to Rust. The Android backend is Oboe 1.11, which is what makes `routeChangeFlow` able to report `"RoutingChanged"` on API 37+. Maven publish builds `:klarinet:jsSourcesJar` after `generateKlarinetWasm`, so the generated wasm source is in the jar.

Earlier releases and build-only fixes are in the changelog.

## Spirit Box

Spirit Box is a Compose Multiplatform app in [`demos/spirit-box`](../demos/spirit-box). It is its own Gradle build (`rootProject.name = "SpiritBox"`) and resolves Klarinet from Maven Central:

```
com.vectencia.klarinet:klarinet:0.6.0
com.vectencia.klarinet:klarinet-coroutines:0.6.0
```

The version pin is `klarinet` in `demos/spirit-box/gradle/libs.versions.toml`.

The radio screen sweeps twelve bundled station clips, six FM and six AM. Dwell is 50–350 ms, and the default is 150. The live session uses `AudioEngine`, a band-pass and gain on the station bed, a microphone mix, and `AudioSessionManager` on Android and iOS. Record writes a 48 kHz mono WAV with `AudioFileWriter`, a second WAV of the station bed (`*.station.wav`), and a `.meta` sidecar.

Review opens that take with `AudioFileReader` and plays it on `AudioEngine` at 0.25×–2×, forward or reverse, with output gain and an optional voice band. The app marks spans from its psicofonía detector. Transcription is a port. With the bundled implementation the review screen shows "Transcription is not connected".

Recordings:

| Target | Directory |
|---|---|
| Desktop | `~/.spirit-box/recordings` |
| Android | app files directory, `recordings/` |
| iOS | documents directory, `recordings/` |

```bash
cd demos/spirit-box
./gradlew :composeApp:run
./gradlew :androidApp:installDebug
./gradlew :composeApp:jvmTest
```

From the Klarinet repo root, `make spiritbox` runs those JVM tests. iOS is the Xcode project `demos/spirit-box/iosApp`, scheme `iosApp`. Its script phase runs `:composeApp:embedAndSignAppleFrameworkForXcode`. The Android manifest declares `RECORD_AUDIO`. The desktop target depends on `kotlinx-coroutines-swing` so `Dispatchers.Main` exists for `collectAsStateWithLifecycle`.
