package com.spiritbox.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.spiritbox.PsicofoniaSpan
import com.spiritbox.applyVoiceBand
import com.spiritbox.domain.RecordingRepository
import com.spiritbox.playback.PlayControl
import com.spiritbox.playback.clampPlaybackRate
import com.spiritbox.playback.durationMs
import com.spiritbox.playback.loadTake
import com.spiritbox.playback.playReview
import com.spiritbox.playback.restartIndex
import com.spiritbox.playback.reverseWouldLeave
import com.spiritbox.playback.sessionWaveform
import com.spiritbox.review.addManualMark
import com.spiritbox.review.combineMarks
import com.spiritbox.review.removeManualMark
import com.spiritbox.scanPosiblePsicofonias
import com.spiritbox.session.SessionMeta
import com.spiritbox.session.listedSession
import com.spiritbox.transcription.TranscriptionPort
import com.spiritbox.transcription.presentTranscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class ReviewViewModel(
    savedStateHandle: SavedStateHandle,
    private val transcription: TranscriptionPort,
    private val recordings: RecordingRepository,
) : ViewModel() {
    private val path: String = savedStateHandle.toRoute<ReviewDestination>().path
    private val playhead = PlayControl()
    private var reviewSamples = FloatArray(0)
    private var reviewClip: FloatArray? = null
    private var reviewMeta: SessionMeta? = null
    private var auto = emptyList<PsicofoniaSpan>()
    private var manual = emptyList<PsicofoniaSpan>()
    private var panel = PanelState.Loading
    private var wave = emptyList<Float>()
    private var threshold = 4f
    private var rate = 1f
    private var reverse = false
    private var paused = false
    private var gain = 1f
    private var voiceBand = false
    private var voiceCache: FloatArray? = null
    private var playbackJob: Job? = null
    private var scanJob: Job? = null
    private var voiceJob: Job? = null
    private var opened = false

    val uiState: StateFlow<ReviewUiState>
        field = MutableStateFlow(ReviewUiState(title = path.substringAfterLast('/')))

    fun open() {
        if (opened) return
        opened = true
        viewModelScope.launch {
            panel = PanelState.Loading
            publish()
            val loaded = runCatching {
                withContext(Dispatchers.Default) { loadTake(path) to recordings.load(path) }
            }
            if (loaded.isFailure) {
                clearTake()
                panel = PanelState.Error
                publish()
                return@launch
            }
            val (audio, meta) = loaded.getOrThrow()
            reviewSamples = audio.first
            reviewClip = audio.second
            reviewMeta = meta
            manual = meta?.manual ?: emptyList()
            auto = withContext(Dispatchers.Default) {
                scanPosiblePsicofonias(reviewSamples, reviewClip, threshold)
            }
            wave = withContext(Dispatchers.Default) { sessionWaveform(reviewSamples).toList() }
            panel = panelState(loading = false, failed = false, itemCount = if (reviewSamples.isEmpty()) 0 else 1)
            publish()
        }
    }

    fun stopPlayback() {
        val job = playbackJob
        playbackJob = null
        paused = false
        playhead.paused = false
        job?.cancel()
        publish()
    }

    fun onPlay() {
        if (playbackJob == null || playhead.ended) playhead.restart = true
        launchPlayback()
    }

    fun onPause() {
        paused = true
        playhead.paused = true
        publish()
    }

    fun onRate(value: Float) {
        val clamped = clampPlaybackRate(value)
        rate = clamped
        playhead.rate = clamped
        publish()
    }

    fun onReverse(enabled: Boolean) {
        reverse = enabled
        playhead.reverse = enabled
        publish()
        if (!enabled) return
        viewModelScope.launch {
            val samples = if (playhead.samples.isNotEmpty()) playhead.samples else reviewSource()
            val pastEnd = samples.isNotEmpty() && playhead.index > samples.lastIndex.toDouble()
            if (pastEnd || reverseWouldLeave(playhead.index, playhead.rate, samples.size)) {
                if (playbackJob == null || playhead.ended) {
                    playhead.restart = true
                    launchPlayback()
                } else {
                    playhead.index = restartIndex(reverse = true, length = samples.size)
                    playhead.ended = false
                    publish()
                }
            }
        }
    }

    fun onGain(value: Float) {
        gain = value
        playhead.gain = value
        publish()
    }

    fun onVoice(enabled: Boolean) {
        voiceBand = enabled
        voiceJob?.cancel()
        voiceJob = viewModelScope.launch {
            playhead.samples = reviewSource()
            publish()
        }
    }

    fun onThreshold(value: Float) {
        threshold = value
        publish()
        scanJob?.cancel()
        val samples = reviewSamples
        val clip = reviewClip
        scanJob = viewModelScope.launch {
            if (samples.isEmpty()) return@launch
            val found = withContext(Dispatchers.Default) {
                scanPosiblePsicofonias(samples, clip, value)
            }
            if (threshold != value) return@launch
            auto = found
            publish()
        }
    }

    fun onAddMark() = updateManual(addManualMark(manual, playhead.frame))

    fun onRemoveMark(span: PsicofoniaSpan) = updateManual(removeManualMark(manual, span))

    fun onJump(frame: Int) {
        playhead.restart = false
        playhead.index = frame.toDouble()
        playhead.frame = frame
        reverse = false
        playhead.reverse = false
        publish()
        launchPlayback()
    }

    override fun onCleared() {
        playbackJob?.cancel()
        scanJob?.cancel()
        voiceJob?.cancel()
    }

    private fun launchPlayback() {
        paused = false
        playhead.paused = false
        val live = playbackJob != null && !playhead.ended
        if (live) {
            viewModelScope.launch {
                playhead.samples = reviewSource()
                publish()
            }
            return
        }
        val previous = playbackJob
        previous?.cancel()
        playbackJob = viewModelScope.launch {
            previous?.cancelAndJoin()
            playhead.samples = reviewSource()
            if (playhead.restart) {
                playhead.index = restartIndex(playhead.reverse, playhead.samples.size)
                playhead.restart = false
            }
            playhead.ended = false
            publish()
            try {
                playReview(playhead)
            } finally {
                if (playbackJob == this.coroutineContext[Job]) playbackJob = null
                publish()
            }
        }
    }

    private suspend fun reviewSource(): FloatArray {
        if (!voiceBand) return reviewSamples
        voiceCache?.let { return it }
        val filtered = withContext(Dispatchers.Default) { applyVoiceBand(reviewSamples) }
        voiceCache = filtered
        return filtered
    }

    private fun updateManual(next: List<PsicofoniaSpan>) {
        manual = next
        val saved = (reviewMeta ?: SessionMeta(
            name = path.substringAfterLast('/'),
            recordedAtMillis = 0L,
            durationMs = durationMs(reviewSamples.size.toLong()),
            manual = next,
        )).copy(manual = next)
        reviewMeta = saved
        publish()
        // The mark is the user's data. Finish the write if this screen closes.
        viewModelScope.launch {
            withContext(NonCancellable) {
                withContext(Dispatchers.Default) { runCatching { recordings.save(path, saved) } }
            }
        }
    }

    private fun clearTake() {
        reviewSamples = FloatArray(0)
        reviewClip = null
        reviewMeta = null
        manual = emptyList()
        auto = emptyList()
        wave = emptyList()
    }

    private fun publish() {
        val marks = combineMarks(auto, manual)
        val summary = listedSession(path, reviewMeta)
        val heard = if (reviewSamples.isEmpty()) {
            emptyList()
        } else {
            marks.flatMap { mark ->
                transcription.transcribe(reviewSamples, mark.startFrame, mark.endFrame)
            }
        }
        uiState.value = ReviewUiState(
            title = summary.title,
            recorded = summary.recorded,
            duration = summary.duration,
            panel = panel,
            wave = wave,
            marks = marks,
            playing = playbackJob?.isActive == true,
            paused = paused,
            rate = rate,
            reverse = reverse,
            gain = gain,
            voiceBand = voiceBand,
            threshold = threshold,
            transcription = presentTranscription(heard),
        )
    }
}
