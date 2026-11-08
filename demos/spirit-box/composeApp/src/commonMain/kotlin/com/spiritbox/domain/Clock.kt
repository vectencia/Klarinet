package com.spiritbox.domain

internal expect fun epochMillis(): Long

internal fun interface Clock {
    fun nowMillis(): Long
}
