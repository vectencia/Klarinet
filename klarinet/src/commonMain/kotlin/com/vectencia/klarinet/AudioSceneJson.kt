package com.vectencia.klarinet

/**
 * JSON codec for [AudioScene]. Hosts can ship `.json` files as their own presets.
 *
 * ```json
 * {
 *   "id": "rain-night",
 *   "layers": [
 *     {
 *       "id": "rain",
 *       "gainDb": -6.0,
 *       "effects": [{ "type": "REVERB", "params": { "0": 0.5 } }]
 *     }
 *   ]
 * }
 * ```
 *
 * [SceneLayer.id] is opaque. The host maps it to a file or generator when
 * calling [AudioScenePlayer.transitionTo].
 */
object AudioSceneJson {
    fun encode(scene: AudioScene): String = buildString {
        append("{\"id\":")
        appendJsonString(scene.id)
        append(",\"layers\":[")
        scene.layers.forEachIndexed { index, layer ->
            if (index > 0) append(',')
            append("{\"id\":")
            appendJsonString(layer.id)
            append(",\"gainDb\":")
            append(layer.gainDb)
            append(",\"effects\":[")
            layer.effects.forEachIndexed { effectIndex, effect ->
                if (effectIndex > 0) append(',')
                append("{\"type\":")
                appendJsonString(effect.type.name)
                append(",\"params\":{")
                effect.params.entries.forEachIndexed { paramIndex, (id, value) ->
                    if (paramIndex > 0) append(',')
                    append('"')
                    append(id)
                    append("\":")
                    append(value)
                }
                append("}}")
            }
            append("]}")
        }
        append("]}")
    }

    fun decode(json: String): AudioScene {
        try {
            return JsonReader(json).parseScene()
        } catch (error: SceneFormatException) {
            throw error
        } catch (error: IllegalArgumentException) {
            throw SceneFormatException(error.message ?: "Invalid scene JSON", error)
        } catch (error: Exception) {
            throw SceneFormatException("Invalid scene JSON", error)
        }
    }
}

private fun StringBuilder.appendJsonString(value: String) {
    append('"')
    for (ch in value) {
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(ch)
        }
    }
    append('"')
}

private class JsonReader(private val text: String) {
    private var pos = 0

    fun parseScene(): AudioScene {
        skipWs()
        expect('{')
        var id: String? = null
        var layers: List<SceneLayer> = emptyList()
        var first = true
        while (true) {
            skipWs()
            if (peek() == '}') {
                pos++
                break
            }
            if (!first) expect(',')
            first = false
            skipWs()
            when (val key = parseString()) {
                "id" -> {
                    skipWs()
                    expect(':')
                    skipWs()
                    id = parseString()
                }
                "layers" -> {
                    skipWs()
                    expect(':')
                    skipWs()
                    layers = parseArray { parseLayer() }
                }
                else -> skipValue()
            }
            skipWs()
        }
        skipWs()
        if (pos != text.length) {
            throw SceneFormatException("Trailing data in scene JSON")
        }
        return AudioScene(id = id ?: throw SceneFormatException("Scene missing id"), layers = layers)
    }

    private fun parseLayer(): SceneLayer {
        expect('{')
        var id: String? = null
        var gainDb = 0f
        var effects: List<SceneEffect> = emptyList()
        var first = true
        while (true) {
            skipWs()
            if (peek() == '}') {
                pos++
                break
            }
            if (!first) expect(',')
            first = false
            skipWs()
            when (val key = parseString()) {
                "id" -> {
                    skipWs()
                    expect(':')
                    skipWs()
                    id = parseString()
                }
                "gainDb" -> {
                    skipWs()
                    expect(':')
                    skipWs()
                    gainDb = parseNumber()
                }
                "effects" -> {
                    skipWs()
                    expect(':')
                    skipWs()
                    effects = parseArray { parseEffect() }
                }
                else -> skipValue()
            }
            skipWs()
        }
        return SceneLayer(
            id = id ?: throw SceneFormatException("Layer missing id"),
            gainDb = gainDb,
            effects = effects,
        )
    }

    private fun parseEffect(): SceneEffect {
        expect('{')
        var typeName: String? = null
        var params: Map<Int, Float> = emptyMap()
        var first = true
        while (true) {
            skipWs()
            if (peek() == '}') {
                pos++
                break
            }
            if (!first) expect(',')
            first = false
            skipWs()
            when (val key = parseString()) {
                "type" -> {
                    skipWs()
                    expect(':')
                    skipWs()
                    typeName = parseString()
                }
                "params" -> {
                    skipWs()
                    expect(':')
                    skipWs()
                    params = parseParams()
                }
                else -> skipValue()
            }
            skipWs()
        }
        val name = typeName ?: throw SceneFormatException("Effect missing type")
        val type = try {
            AudioEffectType.valueOf(name)
        } catch (error: IllegalArgumentException) {
            throw SceneFormatException("Unknown effect type $name", error)
        }
        return SceneEffect(type = type, params = params)
    }

    private fun parseParams(): Map<Int, Float> {
        expect('{')
        val params = mutableMapOf<Int, Float>()
        var first = true
        while (true) {
            skipWs()
            if (peek() == '}') {
                pos++
                break
            }
            if (!first) expect(',')
            first = false
            skipWs()
            val key = parseString()
            val id = key.toIntOrNull() ?: throw SceneFormatException("Parameter id must be an integer: $key")
            skipWs()
            expect(':')
            skipWs()
            params[id] = parseNumber()
            skipWs()
        }
        return params
    }

    private fun <T> parseArray(parseItem: () -> T): List<T> {
        expect('[')
        val items = mutableListOf<T>()
        var first = true
        while (true) {
            skipWs()
            if (peek() == ']') {
                pos++
                break
            }
            if (!first) expect(',')
            first = false
            skipWs()
            items += parseItem()
            skipWs()
        }
        return items
    }

    private fun parseString(): String {
        expect('"')
        val out = StringBuilder()
        while (pos < text.length) {
            val ch = text[pos++]
            when (ch) {
                '"' -> return out.toString()
                '\\' -> {
                    if (pos >= text.length) throw SceneFormatException("Unterminated escape")
                    when (val esc = text[pos++]) {
                        '"', '\\', '/' -> out.append(esc)
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            if (pos + 4 > text.length) throw SceneFormatException("Bad unicode escape")
                            val hex = text.substring(pos, pos + 4)
                            pos += 4
                            out.append(hex.toInt(16).toChar())
                        }
                        else -> throw SceneFormatException("Bad escape \\$esc")
                    }
                }
                else -> out.append(ch)
            }
        }
        throw SceneFormatException("Unterminated string")
    }

    private fun parseNumber(): Float {
        val start = pos
        if (peek() == '-') pos++
        while (pos < text.length && text[pos] in '0'..'9') pos++
        if (pos < text.length && text[pos] == '.') {
            pos++
            while (pos < text.length && text[pos] in '0'..'9') pos++
        }
        if (pos < text.length && (text[pos] == 'e' || text[pos] == 'E')) {
            pos++
            if (pos < text.length && (text[pos] == '+' || text[pos] == '-')) pos++
            while (pos < text.length && text[pos] in '0'..'9') pos++
        }
        val raw = text.substring(start, pos)
        return raw.toFloatOrNull() ?: throw SceneFormatException("Invalid number $raw")
    }

    private fun skipValue() {
        skipWs()
        when (val ch = peek()) {
            '"' -> parseString()
            '{' -> parseObjectSkip()
            '[' -> parseArray { skipValue() }
            't' -> consumeLiteral("true")
            'f' -> consumeLiteral("false")
            'n' -> consumeLiteral("null")
            else -> {
                if (ch == '-' || ch in '0'..'9') {
                    parseNumber()
                } else {
                    throw SceneFormatException("Unexpected '$ch'")
                }
            }
        }
    }

    private fun parseObjectSkip() {
        expect('{')
        var first = true
        while (true) {
            skipWs()
            if (peek() == '}') {
                pos++
                return
            }
            if (!first) expect(',')
            first = false
            skipWs()
            parseString()
            skipWs()
            expect(':')
            skipValue()
        }
    }

    private fun consumeLiteral(literal: String) {
        if (!text.startsWith(literal, pos)) {
            throw SceneFormatException("Expected $literal")
        }
        pos += literal.length
    }

    private fun skipWs() {
        while (pos < text.length && text[pos].isWhitespace()) pos++
    }

    private fun peek(): Char {
        if (pos >= text.length) throw SceneFormatException("Unexpected end of scene JSON")
        return text[pos]
    }

    private fun expect(ch: Char) {
        skipWs()
        if (peek() != ch) throw SceneFormatException("Expected '$ch'")
        pos++
    }
}
