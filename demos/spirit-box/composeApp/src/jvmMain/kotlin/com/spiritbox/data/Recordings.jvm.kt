package com.spiritbox.data

import java.io.File

internal actual fun recordingsDirectory(): String {
    val dir = File(System.getProperty("user.home"), ".spirit-box/recordings")
    check(dir.isDirectory || dir.mkdirs())
    return dir.absolutePath
}

internal actual fun listRecordings(directory: String): List<String> =
    File(directory).listFiles()
        ?.filter { it.isFile && it.name.endsWith(".wav") && !it.name.endsWith(".station.wav") }
        ?.sortedByDescending { it.name }
        ?.map { it.absolutePath }
        ?: emptyList()
