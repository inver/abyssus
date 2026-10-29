/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.water

import net.nevinsky.abyssus.lib.core.editor.util.shortNumberText
import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.water.WaterField
import net.nevinsky.abyssus.lib.core.water.WaterPresets
import net.nevinsky.abyssus.lib.core.water.WaterPreset

/** What the text a user typed for a water value is. */
sealed interface WaterInput {
    data class Value(val number: Double) : WaterInput

    /** Not a number at all. */
    data object NotANumber : WaterInput

    /** `NaN`, `Infinity`, or a number too large to hold: refused like any non-finite value. */
    data object NotFinite : WaterInput
}

/** Parsing of typed values and the text a property editor shows for a saved surface. */
class WaterText(private val presets: WaterPresets = WaterPresets()) {
    private val decimal = Regex("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")

    fun parse(text: String): WaterInput {
        val trimmed = text.trim()
        if (trimmed in NON_FINITE) return WaterInput.NotFinite
        if (!decimal.matches(trimmed)) return WaterInput.NotANumber
        val number = trimmed.toDouble()
        return if (number.isFinite()) WaterInput.Value(number) else WaterInput.NotFinite
    }

    /** [value] as short as it reads: `3` for 3.0. */
    fun format(value: Double): String =
        shortNumberText(value)

    /**
     * What each field of the saved [surface] reads: the number as the file spells it, else (when it is absent) the
     * recorded preset's default, else empty (a field with a wrong type, or a surface without a known preset).
     */
    fun texts(surface: JsonNode): Map<WaterField, String> {
        val preset: WaterPreset? = presets.parse(surface.get("preset")?.takeIf { it.isTextual }?.textValue())
        val defaults = preset?.let { presets.defaults(it, "", "") }
        return WaterField.entries.associateWith { field ->
            var node: JsonNode? = surface
            for (key in field.path) node = node?.get(key)
            when {
                node == null -> defaults?.let { format(it.value(field)) }.orEmpty()
                node.isNumber -> node.asText()
                else -> ""
            }
        }
    }
}

private val NON_FINITE = setOf("NaN", "Infinity", "+Infinity", "-Infinity")
