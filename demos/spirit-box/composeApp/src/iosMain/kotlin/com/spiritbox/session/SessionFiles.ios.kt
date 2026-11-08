@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.spiritbox.session

import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

internal actual fun writeUtf8(path: String, text: String) {
    NSString.create(string = text).writeToFile(
        path = path,
        atomically = true,
        encoding = NSUTF8StringEncoding,
        error = null,
    )
}

internal actual fun readUtf8(path: String): String? =
    NSString.stringWithContentsOfFile(
        path = path,
        encoding = NSUTF8StringEncoding,
        error = null,
    )
