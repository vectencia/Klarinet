@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.spiritbox.data

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

internal actual fun recordingsDirectory(): String {
    val base = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory,
        NSUserDomainMask,
        true,
    ).firstOrNull() as? String ?: error("no documents directory")
    val dir = "$base/recordings"
    check(
        NSFileManager.defaultManager.createDirectoryAtPath(
            dir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        ) || NSFileManager.defaultManager.fileExistsAtPath(dir),
    )
    return dir
}

internal actual fun listRecordings(directory: String): List<String> {
    val names = NSFileManager.defaultManager.contentsOfDirectoryAtPath(directory, null) ?: return emptyList()
    return names.filterIsInstance<String>()
        .filter { it.endsWith(".wav") && !it.endsWith(".station.wav") }
        .sortedDescending()
        .map { "$directory/$it" }
}
