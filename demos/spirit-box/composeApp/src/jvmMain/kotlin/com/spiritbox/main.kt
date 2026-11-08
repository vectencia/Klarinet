package com.spiritbox

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.spiritbox.session.playNoise

fun main(args: Array<String>) {
    if (args.firstOrNull() == "oneshot") {
        playNoise()
        return
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Spirit Box",
        ) {
            App()
        }
    }
}
