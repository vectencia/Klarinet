package com.spiritbox.session

import java.io.File

internal actual fun writeUtf8(path: String, text: String) {
    File(path).writeText(text)
}

internal actual fun readUtf8(path: String): String? {
    val file = File(path)
    return if (file.isFile) file.readText() else null
}
