package com.vectencia.klarinet

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

/**
 * Process-wide Android [Context] from [AudioSessionManager.bind].
 * Used to check [Manifest.permission.RECORD_AUDIO] before opening input streams.
 */
internal object AndroidHostContext {
    @Volatile
    var applicationContext: Context? = null
        private set

    fun bind(context: Context) {
        applicationContext = context.applicationContext
    }

    fun hasRecordAudioPermission(): Boolean? {
        val ctx = applicationContext ?: return null
        return ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }
}

internal fun throwIfRecordAudioDenied(granted: Boolean?) {
    if (granted == false) {
        throw PermissionException("RECORD_AUDIO permission is not granted")
    }
}
