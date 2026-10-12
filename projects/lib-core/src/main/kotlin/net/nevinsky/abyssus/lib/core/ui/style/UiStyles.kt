/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.ui.style

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.ui.model.UiCollection
import net.nevinsky.abyssus.lib.core.ui.model.UiWidget

/** Appearance states, lowest priority first: a later state in [UiVisualState.entries] wins over an earlier one. */
enum class UiVisualState(val json: String) { NORMAL("normal"), FOCUSED("focused"), HOVER("hover"), PRESSED("pressed"), DISABLED("disabled") }

enum class UiStyleIssue { MISSING_STYLE, BAD_VALUE }

data class UiStyleDiagnostic(val code: UiStyleIssue, val widgetId: String, val key: String? = null)

/** The merged style values of one widget in one set of states, with typed readers that ignore a malformed value. */
class UiResolvedStyle(private val values: Map<String, JsonNode>, val diagnostics: List<UiStyleDiagnostic> = emptyList()) {
    fun string(key: String): String? = values[key]?.takeIf { it.isTextual }?.textValue()

    fun number(key: String): Double? = values[key]?.takeIf { it.isNumber && it.doubleValue().isFinite() }?.doubleValue()

    /** A boolean style value (`uppercase`); false when absent or not a boolean. */
    fun flag(key: String): Boolean = values[key]?.takeIf { it.isBoolean }?.booleanValue() ?: false

    /** `#rrggbb` or `#rrggbbaa` as RGBA8888, or null when absent or malformed. */
    fun color(key: String): Int? = string(key)?.let(::parseUiColor)

    /** A resource id, e.g. a `font` or `image` value; resolution to a file is the resource layer's job. */
    fun resource(key: String): String? = string(key)

    fun has(key: String) = key in values
}

fun parseUiColor(text: String): Int? {
    if (!text.startsWith("#") || (text.length != 7 && text.length != 9)) return null
    val digits = text.substring(1)
    if (!digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    val rgb = digits.substring(0, 6).toLong(16).toInt()
    val alpha = if (digits.length == 8) digits.substring(6).toInt(16) else 0xff
    return (rgb shl 8) or alpha
}

/** One layer of style data: base values and per-state overrides. */
private class StyleLayer(val base: Map<String, JsonNode>, val states: Map<UiVisualState, Map<String, JsonNode>>)

private fun layerOf(node: JsonNode?): StyleLayer {
    if (node == null || !node.isObject) return StyleLayer(emptyMap(), emptyMap())
    val base = node.properties().filter { it.key != "states" && it.key != "name" }.associate { it.key to it.value }
    val states = UiVisualState.entries.mapNotNull { state ->
        node.get("states")?.get(state.json)?.takeIf { it.isObject }?.let { state to it.properties().associate { e -> e.key to e.value } }
    }.toMap()
    return StyleLayer(base, states)
}

/**
 * Resolves a widget's style: bundled type defaults, then the named style, then the widget's own overrides, and only
 * then state overrides (each layer in that order), the states applied from lowest to highest priority. Pure.
 */
class UiStyleResolver(json: JsonProcessor) {
    private val bundled: Map<String, StyleLayer> = run {
        val text = checkNotNull(UiStyleResolver::class.java.getResourceAsStream("/ui/default-theme.json")) { "default UI theme missing" }
            .use { String(it.readAllBytes(), Charsets.UTF_8) }
        json.readObject(text).properties().associate { it.key to layerOf(it.value) }
    }

    /** The bundled default for a widget type name (`button`, ...); empty for a type the theme does not style. */
    fun bundledValue(type: String, key: String): JsonNode? = bundled[type]?.base?.get(key)

    /** [styleName] is the named style to use; a widget with a bound `style` passes the bound name, else its own. */
    fun resolve(collection: UiCollection, widget: UiWidget, states: Set<UiVisualState>, styleName: String? = widget.style): UiResolvedStyle {
        val diagnostics = mutableListOf<UiStyleDiagnostic>()
        val default = widget.type?.let { bundled[it.json] } ?: StyleLayer(emptyMap(), emptyMap())
        val named = styleName?.let { name ->
            collection.styles.firstOrNull { it.name == name }?.let { layerOf(it.values) }
                ?: run { diagnostics += UiStyleDiagnostic(UiStyleIssue.MISSING_STYLE, widget.id, name); null }
        } ?: StyleLayer(emptyMap(), emptyMap())
        val own = layerOf(widget.properties["styleOverrides"])
        val merged = LinkedHashMap<String, JsonNode>()
        for (layer in listOf(default, named, own)) merged.putAll(layer.base)
        val active = UiVisualState.entries.filter { it == UiVisualState.NORMAL || it in states }
        for (state in active) for (layer in listOf(default, named, own)) layer.states[state]?.let(merged::putAll)
        for ((key, value) in merged) if (value.isNull) diagnostics += UiStyleDiagnostic(UiStyleIssue.BAD_VALUE, widget.id, key)
        return UiResolvedStyle(merged.filterValues { !it.isNull }, diagnostics)
    }
}
