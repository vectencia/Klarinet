package com.spiritbox.transcription

internal data class Hypothesis(val text: String, val confidence: Float)

internal interface TranscriptionPort {
    fun transcribe(samples: FloatArray, startFrame: Int, endFrame: Int): List<Hypothesis>
}

internal class UnavailableTranscription : TranscriptionPort {
    override fun transcribe(samples: FloatArray, startFrame: Int, endFrame: Int): List<Hypothesis> =
        emptyList()
}

internal data class TranscriptionView(
    val notConnected: Boolean,
    val lines: List<Hypothesis>,
    val status: String,
    val warning: String,
)

internal fun transcriptionDisclaimer(): String =
    "Automatic suggestion only. This is not contact with the dead and not a deciphered message."

internal fun presentTranscription(hypotheses: List<Hypothesis>): TranscriptionView {
    val warning = transcriptionDisclaimer()
    if (hypotheses.isEmpty()) {
        return TranscriptionView(
            notConnected = true,
            lines = emptyList(),
            status = "Transcription is not connected",
            warning = warning,
        )
    }
    return TranscriptionView(
        notConnected = false,
        lines = hypotheses,
        status = "",
        warning = warning,
    )
}
