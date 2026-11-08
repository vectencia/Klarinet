package com.spiritbox.domain

import com.spiritbox.session.SessionMeta

internal interface RecordingRepository {
    fun listPaths(): List<String>

    fun load(path: String): SessionMeta?

    fun save(path: String, meta: SessionMeta)

    fun newTakePath(): String
}
