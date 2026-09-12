package com.vectencia.klarinet

import kotlin.math.abs
import kotlin.math.min

actual class AudioStream internal constructor(
    private val engine: AudioEngine,
    requested: AudioStreamConfig,
    private val callback: AudioStreamCallback?,
) : AutoCloseable {

    actual val config: AudioStreamConfig = negotiateConfig(engine.context, requested)

    private var _state: StreamState = StreamState.OPEN
    private var _chain: AudioEffectChain? = null
    private var startGeneration = 0

    private var processor: ScriptProcessorNode? = null
    private var mediaStream: MediaStream? = null
    private var mediaSource: MediaStreamAudioSourceNode? = null
    private var muteGain: GainNode? = null

    private val processBuffer = FloatArray(maxProcessSamples(config))
    private val outputRing = SampleRing(config.channelCount * config.bufferCapacityInFrames * 8)
    private val inputRing = SampleRing(config.channelCount * config.bufferCapacityInFrames * 8)

    actual val state: StreamState get() = _state

    actual val latencyInfo: LatencyInfo
        get() {
            if (_state != StreamState.STARTED) return LatencyInfo(0.0, 0.0)
            val ms = config.bufferCapacityInFrames * 1000.0 / config.sampleRate.toDouble()
            return when (config.direction) {
                StreamDirection.OUTPUT -> LatencyInfo(0.0, ms)
                StreamDirection.INPUT -> LatencyInfo(ms, 0.0)
            }
        }

    actual fun start() {
        requireActive(_state != StreamState.CLOSED, "AudioStream")
        when (_state) {
            StreamState.STARTED -> return
            StreamState.OPEN, StreamState.PAUSED -> Unit
            else -> throw StreamOperationException("Cannot start stream in state $_state")
        }
        val generation = ++startGeneration
        setState(StreamState.STARTING)
        try {
            engine.context.resume().then(
                {
                    if (generation == startGeneration && _state == StreamState.STARTING) {
                        continueStart(generation)
                    }
                    null
                },
                { error ->
                    failStart(generation, error)
                    null
                },
            )
        } catch (error: Throwable) {
            failStart(generation, error)
        }
    }

    actual fun pause() {
        requireActive(_state != StreamState.CLOSED, "AudioStream")
        when (_state) {
            StreamState.PAUSED -> return
            StreamState.STARTED, StreamState.STARTING -> {
                startGeneration++
                setState(StreamState.PAUSING)
                disconnectGraph()
                setState(StreamState.PAUSED)
            }
            else -> throw StreamOperationException("Cannot pause stream in state $_state")
        }
    }

    actual fun stop() {
        requireActive(_state != StreamState.CLOSED, "AudioStream")
        when (_state) {
            StreamState.STOPPED -> return
            StreamState.STARTED, StreamState.PAUSED, StreamState.STARTING -> {
                startGeneration++
                setState(StreamState.STOPPING)
                teardownIo()
                setState(StreamState.STOPPED)
            }
            else -> throw StreamOperationException("Cannot stop stream in state $_state")
        }
    }

    actual override fun close() {
        if (_state == StreamState.CLOSED) return
        startGeneration++
        if (_state == StreamState.STARTED || _state == StreamState.PAUSED || _state == StreamState.STARTING) {
            try {
                teardownIo()
            } catch (_: Throwable) {
            }
        }
        setState(StreamState.CLOSING)
        detachChain()
        processor = null
        muteGain = null
        setState(StreamState.CLOSED)
    }

    actual fun write(data: FloatArray, numFrames: Int, timeoutNanos: Long): Int {
        requireActive(_state != StreamState.CLOSED, "AudioStream")
        if (config.direction != StreamDirection.OUTPUT) {
            throw StreamOperationException("Cannot write to an input stream")
        }
        val samples = numFrames * config.channelCount
        return outputRing.write(data, 0, min(samples, data.size)) / config.channelCount
    }

    actual fun read(data: FloatArray, numFrames: Int, timeoutNanos: Long): Int {
        requireActive(_state != StreamState.CLOSED, "AudioStream")
        if (config.direction != StreamDirection.INPUT) {
            throw StreamOperationException("Cannot read from an output stream")
        }
        val samples = numFrames * config.channelCount
        return inputRing.read(data, 0, min(samples, data.size)) / config.channelCount
    }

    actual var effectChain: AudioEffectChain?
        get() = _chain
        set(value) {
            requireActive(_state != StreamState.CLOSED, "AudioStream")
            if (_chain === value) return
            _chain?.attachedStream = null
            _chain = value
            value?.attachedStream = this
            if (_state == StreamState.STARTED) rebuildGraph()
        }

    actual val peakLevel: Float get() = peakLevelAtomic.get()

    internal actual val peakLevelAtomic = AtomicFloat(0f)

    internal fun rebuildGraph() {
        disconnectGraph()
        val processorNode = ensureProcessor()
        val ctx = engine.context
        when (config.direction) {
            StreamDirection.OUTPUT -> {
                var node: AudioNode = processorNode
                node = connectEffects(node)
                node.connect(ctx.destination)
            }
            StreamDirection.INPUT -> {
                val source = mediaSource ?: return
                var node: AudioNode = source
                node = connectEffects(node)
                node.connect(processorNode)
                val mute = muteGain ?: ctx.createGain().also { created ->
                    created.gain.value = 0f
                    muteGain = created
                }
                mute.gain.value = 0f
                processorNode.connect(mute)
                mute.connect(ctx.destination)
            }
        }
        processorNode.onaudioprocess = { event -> onAudioProcess(event) }
    }

    internal fun clearEffectChainIf(chain: AudioEffectChain) {
        if (_chain !== chain) return
        _chain = null
        chain.attachedStream = null
        if (_state == StreamState.STARTED) rebuildGraph()
    }

    private fun continueStart(generation: Int) {
        if (config.deviceId != null && config.deviceId != 0 && config.deviceId != 1) {
            WebDeviceRegistry.webId(config.deviceId)?.let { sinkId ->
                if (config.direction == StreamDirection.OUTPUT) {
                    trySetSinkId(engine.context, sinkId)
                }
            }
        }
        if (config.direction == StreamDirection.INPUT && mediaStream == null) {
            try {
                navigator.mediaDevices.getUserMedia(mediaConstraints()).then(
                    { stream ->
                        if (generation != startGeneration || _state != StreamState.STARTING) {
                            stopTracks(stream)
                        } else {
                            mediaStream = stream
                            mediaSource = engine.context.createMediaStreamSource(stream)
                            rebuildGraph()
                            setState(StreamState.STARTED)
                        }
                        null
                    },
                    { error ->
                        failStart(generation, error, permission = true)
                        null
                    },
                )
            } catch (error: Throwable) {
                failStart(generation, error, permission = true)
            }
            return
        }
        rebuildGraph()
        setState(StreamState.STARTED)
    }

    private fun failStart(generation: Int, error: Throwable, permission: Boolean = false) {
        if (generation != startGeneration) return
        if (_state == StreamState.STARTING) setState(StreamState.OPEN)
        val exception = when {
            permission -> PermissionException(error.message ?: "Microphone permission was not granted")
            else -> StreamOperationException(error.message ?: "Failed to start audio stream", error)
        }
        callback?.onStreamError(this, exception)
    }

    private fun mediaConstraints(): dynamic {
        val audio: dynamic = js("{}")
        audio.channelCount = config.channelCount
        val webId = WebDeviceRegistry.webId(config.deviceId)
        if (webId != null && config.deviceId != 0 && config.deviceId != 1) {
            val device: dynamic = js("{}")
            device.exact = webId
            audio.deviceId = device
        }
        val constraints: dynamic = js("{}")
        constraints.audio = audio
        return constraints
    }

    private fun ensureProcessor(): ScriptProcessorNode {
        processor?.let { return it }
        val created = engine.context.createScriptProcessor(
            config.bufferCapacityInFrames,
            config.channelCount,
            config.channelCount,
        )
        processor = created
        return created
    }

    private fun connectEffects(source: AudioNode): AudioNode {
        var node = source
        val effects = _chain?.effects ?: return node
        for (effect in effects) {
            val graph = effect.attach(engine.context)
            node.connect(graph.input)
            node = graph.output
        }
        return node
    }

    private fun onAudioProcess(event: AudioProcessingEvent) {
        if (_state != StreamState.STARTED) {
            silence(event)
            return
        }
        val frames = event.outputBuffer.length
        val channels = config.channelCount
        val samples = frames * channels
        val buffer = if (samples <= processBuffer.size) processBuffer else FloatArray(samples)
        try {
            when (config.direction) {
                StreamDirection.INPUT -> {
                    copyInput(event, buffer, frames, channels)
                    if (callback != null) {
                        callback.onAudioReady(buffer, frames)
                    } else {
                        inputRing.write(buffer, 0, samples)
                    }
                    updatePeak(buffer, samples)
                    silence(event)
                }
                StreamDirection.OUTPUT -> {
                    for (i in 0 until samples) buffer[i] = 0f
                    val produced = if (callback != null) {
                        callback.onAudioReady(buffer, frames)
                    } else {
                        outputRing.read(buffer, 0, samples) / channels
                    }
                    if (produced < frames) {
                        val start = (produced * channels).coerceAtLeast(0)
                        for (i in start until samples) buffer[i] = 0f
                    }
                    updatePeak(buffer, samples)
                    copyOutput(event, buffer, frames, channels)
                }
            }
        } catch (error: Throwable) {
            silence(event)
            callback?.onStreamError(
                this,
                StreamOperationException(error.message ?: "Audio callback failed", error),
            )
        }
    }

    private fun updatePeak(buffer: FloatArray, samples: Int) {
        var peak = 0f
        for (i in 0 until samples) {
            val value = abs(buffer[i])
            if (value > peak) peak = value
        }
        peakLevelAtomic.set(peak)
    }

    private fun copyInput(event: AudioProcessingEvent, dest: FloatArray, frames: Int, channels: Int) {
        val input = event.inputBuffer
        val srcCount = input.numberOfChannels.coerceAtLeast(1)
        for (frame in 0 until frames) {
            for (ch in 0 until channels) {
                val srcCh = if (ch < srcCount) ch else 0
                dest[frame * channels + ch] = float32Get(input.getChannelData(srcCh), frame)
            }
        }
    }

    private fun copyOutput(event: AudioProcessingEvent, src: FloatArray, frames: Int, channels: Int) {
        val output = event.outputBuffer
        val dstCount = output.numberOfChannels
        for (ch in 0 until dstCount) {
            val data = output.getChannelData(ch)
            val srcCh = if (ch < channels) ch else 0
            for (frame in 0 until frames) {
                float32Set(data, frame, src[frame * channels + srcCh])
            }
        }
    }

    private fun silence(event: AudioProcessingEvent) {
        val output = event.outputBuffer
        for (ch in 0 until output.numberOfChannels) {
            val data = output.getChannelData(ch)
            for (i in 0 until output.length) float32Set(data, i, 0f)
        }
    }

    private fun disconnectGraph() {
        processor?.onaudioprocess = null
        try {
            processor?.disconnect()
        } catch (_: Throwable) {
        }
        try {
            mediaSource?.disconnect()
        } catch (_: Throwable) {
        }
        try {
            muteGain?.disconnect()
        } catch (_: Throwable) {
        }
        _chain?.effects?.forEach { effect ->
            try {
                effect.graph?.input?.disconnect()
            } catch (_: Throwable) {
            }
            try {
                effect.graph?.output?.disconnect()
            } catch (_: Throwable) {
            }
        }
    }

    private fun teardownIo() {
        disconnectGraph()
        mediaStream?.let { stopTracks(it) }
        mediaStream = null
        mediaSource = null
        outputRing.clear()
        inputRing.clear()
    }

    private fun detachChain() {
        _chain?.attachedStream = null
        _chain = null
    }

    private fun setState(next: StreamState) {
        _state = next
        callback?.onStreamStateChanged(this, next)
    }
}

private fun negotiateConfig(context: AudioContext, requested: AudioStreamConfig): AudioStreamConfig {
    val channels = requested.channelCount.coerceIn(1, 2)
    return requested.copy(
        sampleRate = context.sampleRate.toInt(),
        channelCount = channels,
        bufferCapacityInFrames = scriptProcessorBufferSize(requested),
    )
}

private fun scriptProcessorBufferSize(config: AudioStreamConfig): Int {
    val requested = if (config.bufferCapacityInFrames > 0) {
        config.bufferCapacityInFrames
    } else {
        when (config.performanceMode) {
            PerformanceMode.LOW_LATENCY -> 256
            PerformanceMode.POWER_SAVING -> 4096
            PerformanceMode.NONE -> 1024
        }
    }
    val allowed = intArrayOf(256, 512, 1024, 2048, 4096, 8192, 16384)
    return allowed.minBy { size -> abs(size - requested) }
}

private fun maxProcessSamples(config: AudioStreamConfig): Int =
    config.bufferCapacityInFrames * config.channelCount

private fun stopTracks(stream: MediaStream) {
    try {
        val tracks = stream.getTracks()
        for (i in 0 until jsLength(tracks)) {
            val track = jsIndex(tracks, i).unsafeCast<MediaStreamTrack>()
            track.stop()
        }
    } catch (_: Throwable) {
    }
}

internal class SampleRing(private val capacity: Int) {
    private val data = FloatArray(capacity.coerceAtLeast(1))
    private var head = 0
    private var tail = 0
    private var count = 0

    fun write(src: FloatArray, offset: Int, length: Int): Int {
        val n = min(length, data.size - count)
        var i = 0
        while (i < n) {
            data[tail] = src[offset + i]
            tail++
            if (tail == data.size) tail = 0
            i++
        }
        count += n
        return n
    }

    fun read(dst: FloatArray, offset: Int, length: Int): Int {
        val n = min(length, count)
        var i = 0
        while (i < n) {
            dst[offset + i] = data[head]
            head++
            if (head == data.size) head = 0
            i++
        }
        count -= n
        return n
    }

    fun clear() {
        head = 0
        tail = 0
        count = 0
    }
}
