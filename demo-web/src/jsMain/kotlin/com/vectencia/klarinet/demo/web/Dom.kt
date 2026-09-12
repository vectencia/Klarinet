package com.vectencia.klarinet.demo.web

import kotlinx.browser.document
import org.w3c.dom.Element
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File
import org.w3c.files.FileReader
import org.w3c.files.get

internal fun Element.el(
    tag: String,
    className: String = "",
    text: String? = null,
    setup: HTMLElement.() -> Unit = {},
): HTMLElement {
    val child = document.createElement(tag) as HTMLElement
    if (className.isNotEmpty()) child.className = className
    if (text != null) child.textContent = text
    child.setup()
    appendChild(child)
    return child
}

internal fun Element.button(label: String, primary: Boolean = false, onClick: () -> Unit): HTMLButtonElement {
    val button = el("button", if (primary) "primary" else "", label) as HTMLButtonElement
    button.onclick = { onClick() }
    return button
}

internal fun Element.slider(
    min: Double,
    max: Double,
    step: Double,
    value: Double,
    onInput: (Double) -> Unit,
): HTMLInputElement {
    val input = el("input") as HTMLInputElement
    input.type = "range"
    input.min = min.toString()
    input.max = max.toString()
    input.step = step.toString()
    input.value = value.toString()
    input.oninput = {
        onInput(input.value.toDouble())
    }
    return input
}

internal fun Element.checkbox(checked: Boolean, onChange: (Boolean) -> Unit): HTMLInputElement {
    val input = el("input") as HTMLInputElement
    input.type = "checkbox"
    input.checked = checked
    input.onchange = { onChange(input.checked) }
    return input
}

internal fun Element.textInput(placeholder: String, value: String = ""): HTMLInputElement {
    val input = el("input") as HTMLInputElement
    input.type = "text"
    input.placeholder = placeholder
    input.value = value
    return input
}

internal fun readFileBytes(file: File, onOk: (ByteArray) -> Unit, onErr: (Throwable) -> Unit) {
    val reader = FileReader()
    reader.onload = {
        try {
            onOk(jsArrayBufferToBytes(reader.result))
        } catch (error: Throwable) {
            onErr(error)
        }
    }
    reader.onerror = { onErr(Error("Failed to read ${file.name}")) }
    reader.readAsArrayBuffer(file)
}

internal fun firstSelectedFile(input: HTMLInputElement): File? = input.files?.get(0)

internal fun downloadBytes(fileName: String, bytes: ByteArray, mime: String) {
    val blob = bytesToBlob(bytes, mime)
    val url = jsCreateObjectUrl(blob)
    val link = document.createElement("a") as org.w3c.dom.HTMLAnchorElement
    link.href = url
    link.download = fileName
    document.body?.appendChild(link)
    link.click()
    document.body?.removeChild(link)
    jsRevokeObjectUrl(url)
}

internal fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun jsArrayBufferToBytes(buffer: dynamic): ByteArray {
    val view: dynamic = js("new Uint8Array(buffer)")
    val length = view.length as Int
    return ByteArray(length) { index -> (view[index] as Int).toByte() }
}

private fun bytesToBlob(bytes: ByteArray, mime: String): dynamic {
    val view: dynamic = js("new Uint8Array(bytes.length)")
    for (i in bytes.indices) view[i] = bytes[i]
    return js("new Blob([view], { type: mime })")
}

private fun jsCreateObjectUrl(blob: dynamic): String = js("URL.createObjectURL(blob)")

private fun jsRevokeObjectUrl(url: String) {
    js("URL.revokeObjectURL(url)")
}
