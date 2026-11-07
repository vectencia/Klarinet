# Module Klarinet Coroutines

Flow and suspend wrappers around Klarinet. Add it next to the core artifact:

```kotlin
implementation("com.vectencia.klarinet:klarinet-coroutines:0.6.0")
```

The core engine, stream, and session setup stay in the Klarinet module. These functions only observe or move bytes.

```kotlin
stream.levelFlow().collect { peak -> }
stream.stateFlow().collect { state -> }
stream.awaitState(StreamState.STARTED)
session.interruptionFlow().collect { info -> }
session.routeChangeFlow().collect { info -> }
timer.stateFlow().collect { state -> }
val samples = reader.readAllSuspend()
reader.asFlow(chunkSize = 4096).collect { chunk -> }
writer.writeFramesSuspend(samples, samples.size)
```

`levelFlow` polls [com.vectencia.klarinet.AudioStream.peakLevel] every 50 ms, runs on `Dispatchers.Default`, and completes with a final `0` after the stream leaves `STARTED` and `STARTING`. File functions use `Dispatchers.IO`, or `Default` on JS. Collect them off the audio callback.

# Package com.vectencia.klarinet.coroutines

Extensions on [com.vectencia.klarinet.AudioStream], [com.vectencia.klarinet.AudioSessionManager], [com.vectencia.klarinet.AudioFileReader], [com.vectencia.klarinet.AudioFileWriter], and [com.vectencia.klarinet.SleepTimer]. The dependency line and the calls are on the Klarinet Coroutines module page.
