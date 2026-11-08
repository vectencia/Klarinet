package com.spiritbox.ui

internal enum class PanelState { Loading, Empty, Error, Ready }

internal fun panelState(loading: Boolean, failed: Boolean, itemCount: Int): PanelState = when {
    loading -> PanelState.Loading
    failed -> PanelState.Error
    itemCount <= 0 -> PanelState.Empty
    else -> PanelState.Ready
}
