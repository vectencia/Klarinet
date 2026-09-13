package com.vectencia.klarinet.demo.web

import com.vectencia.klarinet.AudioEngine
import com.vectencia.klarinet.AudioFileFormat
import com.vectencia.klarinet.AudioFileReader
import com.vectencia.klarinet.AudioStream
import com.vectencia.klarinet.audioFileWavBytes
import com.vectencia.klarinet.decodeAudioBytes
import com.vectencia.klarinet.decodeAudioFile
import com.vectencia.klarinet.playFile
import com.vectencia.klarinet.recordToFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement

internal object FileScreen {
    private const val recordingPath = "memory:recording.wav"

    fun mount(parent: HTMLElement, scope: CoroutineScope) {
        val screen = parent.el("section", "screen")
        screen.el("h2", text = "File Player")
        screen.el(
            "p",
            "status",
            "Decode a local file or URL into the in-memory store, then play it with AudioEngine.playFile.",
        )

        val pickerCard = screen.el("div", "card")
        pickerCard.el("strong", text = "Load audio")
        val urlInput = pickerCard.textInput("https://example.com/audio.mp3")
        val fileInput = pickerCard.el("input", "hidden-file") as HTMLInputElement
        fileInput.type = "file"
        fileInput.accept = "audio/*"
        val actions = pickerCard.el("div", "actions")
        val status = pickerCard.el("p", "status", "No file loaded")
        val infoBox = screen.el("div", "card")
        infoBox.el("strong", text = "File info")
        val infoBody = infoBox.el("div", "status", "Load a file to see format, duration, and channels.")

        var path: String? = null
        var engine: AudioEngine? = null
        var stream: AudioStream? = null
        var recording = false

        fun stopPlayback() {
            stream?.let { DemoSession.detach(it) }
            try {
                stream?.stop()
                stream?.close()
            } catch (_: Throwable) {
            }
            try {
                engine?.release()
            } catch (_: Throwable) {
            }
            stream = null
            engine = null
            recording = false
        }

        fun showInfo(readerPath: String) {
            val reader = AudioFileReader(readerPath)
            val info = reader.info
            reader.close()
            path = readerPath
            infoBody.textContent = buildString {
                appendLine("Path: $readerPath")
                appendLine("Format: ${info.format}")
                appendLine("Duration: ${formatDuration(info.durationMs)}")
                appendLine("Sample rate: ${info.sampleRate} Hz")
                appendLine("Channels: ${info.channelCount}")
                appendLine("Bit rate: ${info.bitRate / 1000} kbps")
                info.tags.title?.let { appendLine("Title: $it") }
            }
            status.textContent = "Ready"
            status.className = "status"
        }

        fun fail(message: String) {
            status.textContent = message
            status.className = "status error"
        }

        actions.button("Decode URL") {
            val url = urlInput.value.trim()
            if (url.isEmpty()) {
                fail("Enter a URL first")
                return@button
            }
            status.textContent = "Decoding…"
            decodeAudioFile(url).then(
                { reader ->
                    reader.close()
                    showInfo(url)
                    null
                },
                { error ->
                    fail(error.message ?: "Decode failed")
                    null
                },
            )
        }

        actions.button("Choose file") { fileInput.click() }
        fileInput.onchange = {
            val file = firstSelectedFile(fileInput)
            if (file != null) {
                status.textContent = "Reading ${file.name}…"
                readFileBytes(
                    file,
                    onOk = { bytes ->
                        decodeAudioBytes(file.name, bytes).then(
                            { reader ->
                                reader.close()
                                showInfo(file.name)
                                null
                            },
                            { error ->
                                fail(error.message ?: "Decode failed")
                                null
                            },
                        )
                    },
                    onErr = { error -> fail(error.message ?: "Read failed") },
                )
            }
        }

        val transport = screen.el("div", "actions")
        val play = transport.button("Play", primary = true) {}
        play.onclick = {
            val loaded = path
            if (stream != null && !recording) {
                stopPlayback()
                play.textContent = "Play"
                status.textContent = "Stopped"
            } else if (loaded == null) {
                fail("Load a file first")
            } else {
                try {
                    stopPlayback()
                    val created = AudioEngine.create()
                    engine = created
                    val opened = created.playFile(loaded)
                    stream = opened
                    opened.start()
                    DemoSession.attach(opened)
                    play.textContent = "Stop"
                    status.textContent = "Playing"
                } catch (error: Throwable) {
                    fail(error.message ?: "Playback failed")
                    stopPlayback()
                }
            }
        }

        val recordCard = screen.el("div", "card")
        recordCard.el("strong", text = "Record to WAV")
        recordCard.el("p", "status", "Captures the microphone into the in-memory store, then you can play or download it.")
        val recordActions = recordCard.el("div", "actions")
        val record = recordActions.button("Record") {}
        record.onclick = {
            if (recording) {
                stopPlayback()
                record.textContent = "Record"
                try {
                    showInfo(recordingPath)
                    status.textContent = "Recording saved"
                } catch (error: Throwable) {
                    fail(error.message ?: "Could not read recording")
                }
            } else {
                try {
                    stopPlayback()
                    val created = AudioEngine.create()
                    engine = created
                    val opened = created.recordToFile(recordingPath, AudioFileFormat.WAV)
                    stream = opened
                    recording = true
                    opened.start()
                    DemoSession.attach(opened)
                    record.textContent = "Stop recording"
                    play.textContent = "Play"
                    status.textContent = "Recording (allow the microphone if asked)"
                } catch (error: Throwable) {
                    fail(error.message ?: "Record failed")
                    stopPlayback()
                }
            }
        }

        recordActions.button("Download WAV") {
            val loaded = path
            if (loaded == null) {
                fail("Nothing to download")
            } else {
                try {
                    downloadBytes(
                        fileName = loaded.substringAfterLast('/').ifBlank { "klarinet.wav" },
                        bytes = audioFileWavBytes(loaded),
                        mime = "audio/wav",
                    )
                } catch (error: Throwable) {
                    fail(error.message ?: "Download failed")
                }
            }
        }

        scope.coroutineContext.job.invokeOnCompletion { stopPlayback() }
    }
}
