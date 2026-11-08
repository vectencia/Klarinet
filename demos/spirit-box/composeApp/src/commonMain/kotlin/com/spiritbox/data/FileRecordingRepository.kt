package com.spiritbox.data

import com.spiritbox.domain.Clock
import com.spiritbox.domain.RecordingRepository
import com.spiritbox.session.SessionMeta
import com.spiritbox.session.loadSessionMeta
import com.spiritbox.session.saveSessionMeta

internal expect fun recordingsDirectory(): String

internal expect fun listRecordings(directory: String): List<String>

internal class FileRecordingRepository(
    private val clock: Clock,
) : RecordingRepository {
    override fun listPaths(): List<String> = listRecordings(recordingsDirectory())

    override fun load(path: String): SessionMeta? = loadSessionMeta(path)

    override fun save(path: String, meta: SessionMeta) {
        saveSessionMeta(path, meta)
    }

    override fun newTakePath(): String =
        "${recordingsDirectory()}/session-${clock.nowMillis()}.wav"
}
