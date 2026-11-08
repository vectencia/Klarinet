package com.spiritbox.data

import com.spiritbox.platform.applicationContext
import java.io.File

internal actual fun recordingsDirectory(): String {
    val root = applicationContext?.filesDir ?: error("playback context")
    val dir = File(root, "recordings")
    check(dir.isDirectory || dir.mkdirs())
    return dir.absolutePath
}

internal actual fun listRecordings(directory: String): List<String> =
    File(directory).listFiles()
        ?.filter { it.isFile && it.name.endsWith(".wav") && !it.name.endsWith(".station.wav") }
        ?.sortedByDescending { it.name }
        ?.map { it.absolutePath }
        ?: emptyList()
