package com.spiritbox.ui

import com.spiritbox.review.MarkedSpan
import com.spiritbox.transcription.TranscriptionView
import com.spiritbox.transcription.presentTranscription

internal data class ReviewUiState(
    val title: String = "",
    val recorded: String = "",
    val duration: String = "",
    val panel: PanelState = PanelState.Loading,
    val wave: List<Float> = emptyList(),
    val marks: List<MarkedSpan> = emptyList(),
    val playing: Boolean = false,
    val paused: Boolean = false,
    val rate: Float = 1f,
    val reverse: Boolean = false,
    val gain: Float = 1f,
    val voiceBand: Boolean = false,
    val threshold: Float = 4f,
    val transcription: TranscriptionView = presentTranscription(emptyList()),
)
