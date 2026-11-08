package com.spiritbox.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

internal class Appearance {
    val dark: StateFlow<Boolean>
        field = MutableStateFlow(true)

    fun toggle() {
        dark.update { !it }
    }
}
