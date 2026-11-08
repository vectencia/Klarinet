package com.spiritbox.session

import com.spiritbox.playback.durationMs
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class TakeNote {
    private val open = AtomicInt(0)

    @Volatile var path: String? = null
    @Volatile var startedAt: Long = 0L
    @Volatile var name: String = ""

    fun begin(path: String, startedAt: Long, name: String) {
        this.path = path
        this.startedAt = startedAt
        this.name = name
        open.store(1)
    }

    fun claim(): String? = if (open.compareAndSet(1, 0)) path else null
}

internal fun closeTake(
    tape: SessionTape,
    note: TakeNote,
    save: (String, SessionMeta) -> Unit = ::saveSessionMeta,
) {
    val path = note.claim() ?: return
    tape.disarm()
    tape.seal()
    if (tape.frames <= 0L) return
    val name = note.name.ifBlank { path.substringAfterLast('/') }
    save(
        path,
        SessionMeta(
            name = name,
            recordedAtMillis = note.startedAt,
            durationMs = durationMs(tape.frames),
            manual = emptyList(),
        ),
    )
}