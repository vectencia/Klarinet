package com.vectencia.klarinet.demo.web

import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.w3c.dom.HTMLElement

private val screens = listOf(
    "Tone Gen",
    "Mic Meter",
    "Latency",
    "File",
    "Effects",
    "Scenes",
)

fun main() {
    val root = document.getElementById("root") as HTMLElement
    val app = root.el("div", "app")
    val header = app.el("header", "header")
    header.el("h1", text = "Klarinet")
    header.el("p", text = "Web Audio demo. Same six screens as Compose and SwiftUI.")

    val banner = app.el("p", "banner")
    banner.hidden = true
    DemoSession.onBanner { text ->
        banner.hidden = text == null
        banner.textContent = text ?: ""
    }

    val tabs = app.el("nav", "tabs")
    val content = app.el("main")
    val buttons = mutableListOf<HTMLElement>()
    var scope = MainScope()
    var current = 0

    fun show(index: Int) {
        scope.cancel()
        scope = MainScope()
        current = index
        buttons.forEachIndexed { i, button ->
            button.className = if (i == index) "tab active" else "tab"
        }
        content.innerHTML = ""
        when (index) {
            0 -> ToneScreen.mount(content, scope)
            1 -> MicScreen.mount(content, scope)
            2 -> LatencyScreen.mount(content, scope)
            3 -> FileScreen.mount(content, scope)
            4 -> EffectsScreen.mount(content, scope)
            5 -> ScenesScreen.mount(content, scope)
        }
    }

    screens.forEachIndexed { index, title ->
        val button = tabs.el("button", if (index == 0) "tab active" else "tab", title)
        button.onclick = { show(index) }
        buttons += button
    }

    show(current)
}
