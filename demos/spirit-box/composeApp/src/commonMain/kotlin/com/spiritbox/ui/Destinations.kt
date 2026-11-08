package com.spiritbox.ui

import kotlinx.serialization.Serializable

@Serializable
internal data object RadioDestination

@Serializable
internal data class ReviewDestination(val path: String)
