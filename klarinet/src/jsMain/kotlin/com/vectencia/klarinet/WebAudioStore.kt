package com.vectencia.klarinet

internal data class StoredAudio(
    val samples: FloatArray,
    val info: AudioFileInfo,
)

internal object WebAudioStore {
    private val files = mutableMapOf<String, StoredAudio>()

    fun put(path: String, stored: StoredAudio) {
        files[path] = stored
    }

    fun get(path: String): StoredAudio? = files[path]
}

internal object WebDeviceRegistry {
    private val ids = mutableMapOf<Int, String>()

    fun register(deviceId: String): Int {
        var id = deviceId.hashCode()
        if (id == 0 || id == 1) id = 10_000 + (deviceId.hashCode() and 0x7FFF)
        ids[id] = deviceId
        return id
    }

    fun webId(id: Int?): String? = id?.let { ids[it] }
}

internal fun formatFromPath(path: String): AudioFileFormat {
    return when (path.substringAfterLast('.', "").lowercase()) {
        "wav" -> AudioFileFormat.WAV
        "mp3" -> AudioFileFormat.MP3
        "aac" -> AudioFileFormat.AAC
        "m4a" -> AudioFileFormat.M4A
        else -> AudioFileFormat.WAV
    }
}
