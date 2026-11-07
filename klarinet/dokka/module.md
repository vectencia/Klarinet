# Module Klarinet

Low-latency playback, recording, file I/O, and effects for Kotlin Multiplatform. The steps below are what a host app does. Each type on this module has its own contract.

## Install

The published version is `0.6.0` (`VERSION_NAME` in `gradle.properties`).

```kotlin
repositories {
    mavenCentral()
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.vectencia.klarinet:klarinet:0.6.0")
            implementation("com.vectencia.klarinet:klarinet-coroutines:0.6.0")
        }
    }
}
```

`klarinet-coroutines` is optional. The Android variant of `klarinet` already depends on `com.vectencia.klarinet:klarinet-jni`. A SwiftUI app imports the `Klarinet` framework produced by the Apple targets:

```swift
import Klarinet

let engine = AudioEngine.companion.create()
```

## Playback

[com.vectencia.klarinet.AudioEngine.create] is the entry point. Keep one engine and close it when the process no longer plays audio.

```kotlin
val engine = AudioEngine.create()
val stream = engine.openStream(
    config = AudioStreamConfig(
        sampleRate = 48000,
        channelCount = 1,
        direction = StreamDirection.OUTPUT,
        performanceMode = PerformanceMode.LOW_LATENCY,
    ),
    callback = object : AudioStreamCallback {
        private var phase = 0.0
        override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
            for (i in 0 until numFrames) {
                buffer[i] = kotlin.math.sin(2.0 * Math.PI * 440.0 * phase / 48000.0).toFloat()
                phase += 1.0
            }
            return numFrames
        }
    },
)
stream.start()
```

Samples are interleaved `Float` in `[-1, 1]`. Return [com.vectencia.klarinet.AudioStreamCallback.onAudioReady]'s `numFrames`. A smaller return on an output stream fills the rest of the block with silence.

On Android, Apple, JVM, and native desktop, `onAudioReady` runs on a worker. The platform callback does not enter Kotlin. On JS, an `AudioWorklet` renders, and `onAudioReady` runs on the main thread. Slow work on that worker or on the main thread underruns.

`start()` on JS returns before the stream is `STARTED`. Watch [com.vectencia.klarinet.AudioStreamCallback.onStreamStateChanged].

Stop and close every stream, then the engine:

```kotlin
stream.stop()
stream.close()
engine.close()
```

Pass `callback = null` to [com.vectencia.klarinet.AudioEngine.openStream] and use [com.vectencia.klarinet.AudioStream.write] or [com.vectencia.klarinet.AudioStream.read] for blocking I/O. JVM and native desktop have no push mode. JS ignores `timeoutNanos`.

## Recording

```kotlin
val stream = engine.openStream(
    config = AudioStreamConfig(
        sampleRate = 48000,
        channelCount = 1,
        direction = StreamDirection.INPUT,
    ),
    callback = object : AudioStreamCallback {
        override fun onAudioReady(buffer: FloatArray, numFrames: Int): Int {
            return numFrames
        }
    },
)
```

`buffer` already holds the captured frames.

## Effects

Create effects from the engine, set parameters, then attach one chain to the stream. [com.vectencia.klarinet.AudioEffectType] order is stable. Parameter ids live on `GainParams`, `ReverbParams`, and the other `*Params` objects.

```kotlin
val compressor = engine.createEffect(AudioEffectType.COMPRESSOR)
compressor.setParameter(CompressorParams.THRESHOLD, -20f)
compressor.setParameter(CompressorParams.RATIO, 4f)
val reverb = engine.createEffect(AudioEffectType.REVERB)
reverb.setParameter(ReverbParams.ROOM_SIZE, 0.7f)
reverb.setParameter(ReverbParams.WET_DRY_MIX, 0.3f)

val chain = engine.createEffectChain()
chain.add(compressor)
chain.add(reverb)
stream.effectChain = chain
```

`setParameter`, [com.vectencia.klarinet.AudioEffectChain.add], and [com.vectencia.klarinet.AudioEffectChain.remove] are safe while the stream is started. On JS, `GAIN` runs in the wasm core. The other fifteen types are Web Audio nodes and are not the same samples as the native core.

## Files

```kotlin
val reader = AudioFileReader("/path/to/audio.wav")
val samples = reader.readAll()
reader.close()
```

WAV and MP3 read on Android, iOS, macOS, tvOS, JVM, and native desktop. AAC and M4A read on Android and iOS, macOS, and tvOS. watchOS has no reader. JVM and native desktop throw [com.vectencia.klarinet.UnsupportedFormatException] for AAC and M4A. JS reads only after `decodeAudioFile`, `decodeAudioBytes`, `putWavBytes`, or `putAudioFile`.

WAV write works on every platform (in memory on JS). AAC and M4A write on Android and on Apple except watchOS. MP3 cannot be encoded.

One [com.vectencia.klarinet.AudioFileReader] is not safe to share across threads.

## What the host implements

Android, before an input stream:

1. Declare `android.permission.RECORD_AUDIO` and grant it at runtime.
2. Call `AudioSessionManager().bind(context)` so a missing grant throws [com.vectencia.klarinet.PermissionException] and so `setActive(true)` can take audio focus.
3. Call `configure` and `setActive(true)` before `openStream` when the app should hold focus.

Apple, before any stream on iOS, tvOS, or watchOS:

1. Add `NSMicrophoneUsageDescription` when the app records.
2. `AudioSessionManager().configure(...)` and `setActive(true)` before `openStream`. macOS has no `AVAudioSession`.

JVM, Linux native, and Windows native have no audio session. Open the stream and start it.

Browser: call `start()` from a user gesture when the platform requires one. Output rate is the `AudioContext` rate, not the rate in [com.vectencia.klarinet.AudioStreamConfig].

[com.vectencia.klarinet.SleepTimer] needs a `GAIN` effect already in the first stream's chain. It fades that gain, then stops the streams. The host still closes them.

## Coroutines

With `klarinet-coroutines` on the classpath, collect from the extensions in `com.vectencia.klarinet.coroutines`:

```kotlin
stream.levelFlow().collect { peak -> /* 0..1, about 20 times a second */ }
stream.stateFlow().collect { state -> }
session.interruptionFlow().collect { info -> }
reader.asFlow(chunkSize = 4096).collect { chunk -> }
```

`levelFlow` completes after the stream leaves `STARTED` and `STARTING`. File suspend functions use `Dispatchers.IO`, or `Default` on JS.

# Package com.vectencia.klarinet

Public API: [com.vectencia.klarinet.AudioEngine], [com.vectencia.klarinet.AudioStream], [com.vectencia.klarinet.AudioStreamCallback], [com.vectencia.klarinet.AudioEffectChain], file reader and writer, and [com.vectencia.klarinet.AudioSessionManager].

Install, a playback implementation, effects, files, and the Android, Apple, and browser host steps are on the Klarinet module page.
