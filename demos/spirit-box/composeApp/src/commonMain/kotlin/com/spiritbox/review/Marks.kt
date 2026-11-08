package com.spiritbox.review

import com.spiritbox.PsicofoniaSpan

internal fun addManualMark(
    marks: List<PsicofoniaSpan>,
    atFrame: Int,
    window: Int = 4_800,
): List<PsicofoniaSpan> {
    val start = atFrame.coerceAtLeast(0)
    val end = start + window.coerceAtLeast(1)
    return marks + PsicofoniaSpan(start, end)
}

internal fun removeManualMark(
    marks: List<PsicofoniaSpan>,
    span: PsicofoniaSpan,
): List<PsicofoniaSpan> =
    marks.filterNot { it.startFrame == span.startFrame && it.endFrame == span.endFrame }

internal data class MarkedSpan(val startFrame: Int, val endFrame: Int, val manual: Boolean)

internal fun combineMarks(
    auto: List<PsicofoniaSpan>,
    manual: List<PsicofoniaSpan>,
): List<MarkedSpan> =
    auto.map { MarkedSpan(it.startFrame, it.endFrame, manual = false) } +
        manual.map { MarkedSpan(it.startFrame, it.endFrame, manual = true) }
