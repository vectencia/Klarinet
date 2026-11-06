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

    internal var workletNode: AudioWorkletNode? = null
    internal val effectInserts = mutableListOf<AudioWorkletNode>()
    private var clockId: Int = -1
    private var clockOrigin: Double = 0.0
    private var clockFrames: Int = 0
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
            ensureKlarinetWorklet(engine.context).then(
                {
                    if (generation == startGeneration && _state == StreamState.STARTING) {
                        continueStartConnected(generation)
                    }
                    engine.context.resume().then(
                        { null },
                        { error ->
                            if (generation == startGeneration && _state == StreamState.STARTING) {
                                failStart(generation, error)
                            }
                            null
                        },
                    )
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
        workletNode = null
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
        processorNode.port.onmessage = { event -> onWorkletMessage(event) }
        val ctx = engine.context
        when (config.direction) {
            StreamDirection.OUTPUT -> {
                var node: AudioNode = processorNode
                node = connectEffects(node, processorNode)
                node.connect(ctx.destination)
            }
            StreamDirection.INPUT -> {
                val source = mediaSource ?: return
                var node: AudioNode = source
                node = connectEffects(node, processorNode)
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
        prefetchWorklet()
        postWorkletTransport(processorNode.port, "start")
        startOutputClock()
    }

    internal fun clearEffectChainIf(chain: AudioEffectChain) {
        if (_chain !== chain) return
        _chain = null
        chain.attachedStream = null
        if (_state == StreamState.STARTED) rebuildGraph()
    }

    private fun continueStartConnected(generation: Int) {
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

    private fun ensureProcessor(): AudioWorkletNode {
        workletNode?.let { return it }
        val direction = if (config.direction == StreamDirection.INPUT) "input" else "output"
        val created = createAudioWorkletNode(engine.context, config.channelCount, direction)
        created.port.onmessage = { event -> onWorkletMessage(event) }
        workletNode = created
        return created
    }

    internal fun usesAudioWorklet(): Boolean = workletNode != null

    private fun connectEffects(source: AudioNode, processorNode: AudioWorkletNode): AudioNode {
        var node = source
        val sampleRate = engine.context.sampleRate.toInt()
        val channels = config.channelCount
        val effects = _chain?.effects
        if (effects.isNullOrEmpty()) {
            postWorkletChain(processorNode.port, sampleRate, channels, emptyList())
            return node
        }
        var index = 0
        var installedOnIo = false
        while (index < effects.size) {
            val effect = effects[index]
            if (effect.type != AudioEffectType.GAIN) {
                val graph = effect.attachWeb(engine.context)
                val input = graph.input ?: error("web effect is missing an input")
                val output = graph.output ?: error("web effect is missing an output")
                node.connect(input)
                node = output
                index++
                continue
            }
            val start = index
            while (index < effects.size && effects[index].type == AudioEffectType.GAIN) {
                index++
            }
            val leadingOutput = config.direction == StreamDirection.OUTPUT &&
                node === processorNode &&
                start == 0
            val trailingInput = config.direction == StreamDirection.INPUT && index == effects.size
            val port = if (leadingOutput || trailingInput) {
                installedOnIo = true
                processorNode.port
            } else {
                val insert = createEffectWorkletNode(engine.context, channels)
                effectInserts.add(insert)
                node.connect(insert)
                node = insert
                insert.port
            }
            val specs = ArrayList<WasmGainSpec>(index - start)
            for (cursor in start until index) {
                specs.add(effects[cursor].installWasm(port))
            }
            postWorkletChain(port, sampleRate, channels, specs)
        }
        if (!installedOnIo) {
            postWorkletChain(processorNode.port, sampleRate, channels, emptyList())
        }
        return node
    }

    private fun onWorkletMessage(event: MessageEvent) {
        val data = event.data ?: return
        val type: String = js("String(data.type)")
        when (type) {
            "io" -> {
                val frames: Int = js("data.frames|0")
                val channels: Int = js("data.channels|0")
                val input: Float32Array? = js("data.input")
                handleWorkletIo(frames, channels, input)
            }
            "need" -> {
                if (config.direction != StreamDirection.OUTPUT) return
                val frames: Int = js("data.frames|0")
                val count: Int = js("data.n|0")
                if (frames <= 0 || count <= 0) return
                repeat(count) {
                    handleWorkletIo(frames, config.channelCount, null)
                }
            }
        }
    }

    private fun prefetchWorklet() {
        if (config.direction != StreamDirection.OUTPUT) return
        repeat(workletPrefetchQuanta(config.bufferCapacityInFrames)) {
            handleWorkletIo(128, config.channelCount, input = null)
        }
    }

    private fun handleWorkletIo(frames: Int, channels: Int, input: Float32Array?) {
        if (frames <= 0) return
        val ch = config.channelCount
        val samples = frames * ch
        val buffer = if (samples <= processBuffer.size) processBuffer else FloatArray(samples)
        if (_state != StreamState.STARTED && _state != StreamState.STARTING) {
            postOutput(FloatArray(samples), samples)
            return
        }
        try {
            when (config.direction) {
                StreamDirection.INPUT -> {
                    if (input != null) {
                        copyFloat32ToFloatArray(input, buffer, samples)
                    } else {
                        for (i in 0 until samples) buffer[i] = 0f
                    }
                    if (callback != null) {
                        callback.onAudioReady(buffer, frames)
                    } else {
                        inputRing.write(buffer, 0, samples)
                    }
                    updatePeak(buffer, samples)
                    postOutput(FloatArray(samples), samples)
                }
                StreamDirection.OUTPUT -> {
                    for (i in 0 until samples) buffer[i] = 0f
                    val produced = if (callback != null) {
                        callback.onAudioReady(buffer, frames)
                    } else {
                        outputRing.read(buffer, 0, samples) / ch
                    }
                    if (produced < frames) {
                        val start = (produced * ch).coerceAtLeast(0)
                        for (i in start until samples) buffer[i] = 0f
                    }
                    updatePeak(buffer, samples)
                    postOutput(buffer, samples)
                }
            }
        } catch (error: Throwable) {
            postOutput(FloatArray(samples), samples)
            callback?.onStreamError(
                this,
                StreamOperationException(error.message ?: "Audio callback failed", error),
            )
        }
    }

    private fun postOutput(buffer: FloatArray, samples: Int) {
        val port = workletNode?.port ?: return
        postWorkletOutput(port, floatArrayToFloat32(buffer, samples))
    }

    private fun updatePeak(buffer: FloatArray, samples: Int) {
        var peak = 0f
        for (i in 0 until samples) {
            val value = abs(buffer[i])
            if (value > peak) peak = value
        }
        peakLevelAtomic.set(peak)
    }

    private fun startOutputClock() {
        stopOutputClock()
        val sampleRate = config.sampleRate.coerceAtLeast(1)
        clockOrigin = engine.context.currentTime
        clockFrames = 0
        clockId = startWorkletPump {
            workletNode?.port?.let { port -> postWorkletTransport(port, "flush") }
            if (config.direction != StreamDirection.OUTPUT) return@startWorkletPump
            val elapsed = engine.context.currentTime - clockOrigin
            val rendered = (elapsed * sampleRate).toInt()
            var due = rendered - clockFrames
            if (due > 128 * 8) {
                clockFrames = rendered - 128
                due = 128
            }
            while (due >= 128) {
                handleWorkletIo(128, config.channelCount, null)
                clockFrames += 128
                due -= 128
            }
        }
    }

    private fun stopOutputClock() {
        if (clockId >= 0) {
            stopWorkletPump(clockId)
            clockId = -1
        }
    }

    private fun disconnectGraph() {
        stopOutputClock()
        workletNode?.let { node -> postWorkletTransport(node.port, "stop") }
        effectInserts.forEach { insert ->
            postWorkletTransport(insert.port, "stop")
            try {
                insert.disconnect()
            } catch (_: Throwable) {
            }
        }
        effectInserts.clear()
        workletNode?.port?.onmessage = null
        try {
            workletNode?.disconnect()
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
        bufferCapacityInFrames = workletBufferSize(requested),
    )
}

private fun workletBufferSize(config: AudioStreamConfig): Int {
    val requested = if (config.bufferCapacityInFrames > 0) {
        config.bufferCapacityInFrames
    } else {
        when (config.performanceMode) {
            PerformanceMode.LOW_LATENCY -> 256
            PerformanceMode.POWER_SAVING -> 4096
            PerformanceMode.NONE -> 1024
        }
    }
    return requested.coerceIn(128, 16384)
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
