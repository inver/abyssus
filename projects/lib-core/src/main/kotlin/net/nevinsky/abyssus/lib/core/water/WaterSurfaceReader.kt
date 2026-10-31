/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.water

import com.fasterxml.jackson.databind.JsonNode

/** What one saved surface reads as: [settings] when it is drawable, else the [problems] that make it invalid. */
data class WaterSurfaceRead(
    val id: String,
    val name: String,
    val settings: WaterSettings?,
    val problems: List<String>,
)

/**
 * Reads one `abyssus.waterSurfaces.<id>` object into [WaterSettings], for the editor and for games alike. A missing
 * scalar takes its recorded preset's default without any write; an unknown preset, a wrong-typed value or an
 * out-of-range value makes the surface invalid. Nothing is repaired.
 */
class WaterSurfaceReader(
    private val presets: WaterPresets = WaterPresets(),
    private val validation: WaterValidation = WaterValidation(),
) {
    fun read(id: String, node: JsonNode): WaterSurfaceRead {
        if (!node.isObject) return WaterSurfaceRead(id, id, null, listOf("surface is not an object"))
        val name = node.get("name")?.takeIf { it.isTextual }?.textValue()?.takeIf { it.isNotBlank() } ?: id
        val preset = presets.parse(node.get("preset")?.takeIf { it.isTextual }?.textValue())
            ?: return WaterSurfaceRead(id, name, null, listOf("unknown preset"))
        val problems = mutableListOf<String>()
        var settings = presets.defaults(preset, id, name)
        for (field in WaterField.entries) {
            val value = lookup(node, field.path) ?: continue
            if (!value.isNumber) {
                problems += "${field.path.joinToString(".")} is not a number"
                continue
            }
            settings = settings.with(field, value.doubleValue())
        }
        val enabled = node.get("enabled")
        if (enabled != null) {
            if (enabled.isBoolean) settings = settings.copy(enabled = enabled.booleanValue()) else problems += "enabled is not a boolean"
        }
        for ((field, issue) in validation.check(settings)) problems += "${field.path.joinToString(".")}: $issue"
        return if (problems.isEmpty()) WaterSurfaceRead(id, name, settings, emptyList()) else WaterSurfaceRead(id, name, null, problems)
    }

    private fun lookup(node: JsonNode, path: List<String>): JsonNode? {
        var current: JsonNode = node
        for (key in path) current = current.get(key) ?: return null
        return current
    }
}
