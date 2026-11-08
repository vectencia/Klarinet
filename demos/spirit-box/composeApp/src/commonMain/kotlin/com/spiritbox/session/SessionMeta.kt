package com.spiritbox.session

import com.spiritbox.PsicofoniaSpan
import com.spiritbox.playback.formatElapsed

internal data class SessionMeta(
    val name: String,
    val recordedAtMillis: Long,
    val durationMs: Long,
    val manual: List<PsicofoniaSpan>,
)

internal data class ListedSession(
    val path: String,
    val title: String,
    val recorded: String,
    val duration: String,
)

internal fun listedSession(path: String, meta: SessionMeta?): ListedSession {
    val title = meta?.name?.takeIf { it.isNotBlank() } ?: path.substringAfterLast('/')
    return ListedSession(
        path = path,
        title = title,
        recorded = meta?.let { formatUtc(it.recordedAtMillis) }.orEmpty(),
        duration = meta?.let { formatElapsed(it.durationMs) }.orEmpty(),
    )
}

internal fun encodeSessionMeta(meta: SessionMeta): String = buildString {
    append("name=")
    append(meta.name.replace('\n', ' ').replace('\r', ' '))
    append('\n')
    append("at=")
    append(meta.recordedAtMillis)
    append('\n')
    append("dur=")
    append(meta.durationMs)
    append('\n')
    for (span in meta.manual) {
        append("mark=")
        append(span.startFrame)
        append(',')
        append(span.endFrame)
        append('\n')
    }
}

internal fun decodeSessionMeta(text: String): SessionMeta {
    var name = ""
    var at = 0L
    var dur = 0L
    val marks = ArrayList<PsicofoniaSpan>()
    for (raw in text.lineSequence()) {
        val line = raw.trim()
        val eq = line.indexOf('=')
        if (eq <= 0) continue
        val key = line.substring(0, eq)
        val value = line.substring(eq + 1)
        when (key) {
            "name" -> name = value
            "at" -> at = value.toLong()
            "dur" -> dur = value.toLong()
            "mark" -> {
                val comma = value.indexOf(',')
                if (comma <= 0) continue
                marks.add(
                    PsicofoniaSpan(
                        value.substring(0, comma).toInt(),
                        value.substring(comma + 1).toInt(),
                    ),
                )
            }
        }
    }
    return SessionMeta(name, at, dur, marks)
}

internal expect fun writeUtf8(path: String, text: String)

internal expect fun readUtf8(path: String): String?

internal fun saveSessionMeta(wavPath: String, meta: SessionMeta) {
    writeUtf8("$wavPath.meta", encodeSessionMeta(meta))
}

internal fun loadSessionMeta(wavPath: String): SessionMeta? {
    val text = readUtf8("$wavPath.meta") ?: return null
    return runCatching { decodeSessionMeta(text) }.getOrNull()
}

internal fun formatUtc(epochMillis: Long): String {
    val days = epochMillis.floorDiv(86_400_000L)
    val seconds = epochMillis.mod(86_400_000L) / 1000L
    val (year, month, day) = civilFromUnixDay(days)
    val hour = (seconds / 3600L).toInt()
    val minute = ((seconds % 3600L) / 60L).toInt()
    return "$year-${two(month)}-${two(day)} ${two(hour)}:${two(minute)}"
}

private fun two(value: Int): String = if (value < 10) "0$value" else "$value"

private fun civilFromUnixDay(daysSinceEpoch: Long): Triple<Int, Int, Int> {
    val z = daysSinceEpoch + 719468
    val era = if (z >= 0) z / 146097 else (z - 146096) / 146097
    val doe = (z - era * 146097).toInt()
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era.toInt() * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = doy - (153 * mp + 2) / 5 + 1
    val month = if (mp < 10) mp + 3 else mp - 9
    val year = if (month <= 2) y + 1 else y
    return Triple(year, month, day)
}
