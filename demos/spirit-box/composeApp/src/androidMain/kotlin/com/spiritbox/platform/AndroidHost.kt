package com.spiritbox.platform

import android.content.Context

internal var applicationContext: Context? = null

fun bindPlaybackContext(context: Context) {
    applicationContext = context.applicationContext
}
