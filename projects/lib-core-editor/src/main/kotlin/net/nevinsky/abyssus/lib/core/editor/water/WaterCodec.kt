/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.water

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.core.water.WaterField
import net.nevinsky.abyssus.lib.core.water.WaterPresets
import net.nevinsky.abyssus.lib.core.water.WaterSettings
import net.nevinsky.abyssus.lib.core.water.WaterValidation

const val ABYSSUS_EXTENSION = "abyssus"
const val WATER_SURFACES = "waterSurfaces"

/** A saved surface: drawable, or kept as a row only so it can be removed. */
sealed interface WaterSurface {
    val id: String
    val name: String
}

data class ValidWater(val settings: WaterSettings) : WaterSurface {
    override val id: String get() = settings.id
    override val name: String get() = settings.name
}

/** [problems] are developer-facing reasons for the log; nothing is repaired in the file. */
data class InvalidWater(override val id: String, override val name: String, val problems: List<String>) : WaterSurface

/** [incompatible] is set when `abyssus` or `waterSurfaces` holds something other than an object, so nothing can be read or added. */
data class WaterRead(val surfaces: List<WaterSurface>, val incompatible: Boolean = false) {
    val valid: List<WaterSettings> get() = surfaces.filterIsInstance<ValidWater>().map { it.settings }
}

/**
 * Reads and writes `abyssus.waterSurfaces.<id>`. A missing scalar takes its recorded preset's default without any
 * write; an unknown preset, a wrong-typed value or an out-of-range value makes the surface invalid.
 */
class WaterCodec(
    private val presets: WaterPresets = WaterPresets(),
    private val validation: WaterValidation = WaterValidation(),
) {
    /** The surfaces of a scene's `abyssus` block ([abyssus] may be null when the scene has none). */
    fun read(abyssus: JsonNode?): WaterRead {
        if (abyssus == null || abyssus.isMissingNode) return WaterRead(emptyList())
        if (!abyssus.isObject) return WaterRead(emptyList(), incompatible = true)
        val surfaces = abyssus.get(WATER_SURFACES) ?: return WaterRead(emptyList())
        if (!surfaces.isObject) return WaterRead(emptyList(), incompatible = true)
        return WaterRead(surfaces.properties().map { (id, node) -> surface(id, node) })
    }

    fun surface(id: String, node: JsonNode): WaterSurface {
        if (!node.isObject) return InvalidWater(id, id, listOf("surface is not an object"))
        val name = node.get("name")?.takeIf { it.isTextual }?.textValue()?.takeIf { it.isNotBlank() } ?: id
        val problems = mutableListOf<String>()
        val preset = presets.parse(node.get("preset")?.takeIf { it.isTextual }?.textValue())
        if (preset == null) {
            problems += "unknown preset"
            return InvalidWater(id, name, problems)
        }
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
        return if (problems.isEmpty()) ValidWater(settings) else InvalidWater(id, name, problems)
    }

    /** The complete saved form of [settings]: every field written explicitly. */
    fun node(settings: WaterSettings, factory: JsonNodeFactory = JsonNodeFactory.instance): ObjectNode {
        val node = factory.objectNode()
        node.put("name", settings.name)
        node.put("preset", settings.preset.name)
        node.set<ObjectNode>("position", factory.objectNode().also {
            it.putNumber("x", settings.x); it.putNumber("y", settings.level); it.putNumber("z", settings.z)
        })
        node.putNumber("width", settings.width)
        node.putNumber("length", settings.length)
        node.set<ObjectNode>("tint", factory.objectNode().also {
            it.putNumber("r", settings.tintR); it.putNumber("g", settings.tintG); it.putNumber("b", settings.tintB)
        })
        node.putNumber("clarity", settings.clarity)
        node.putNumber("waveAmplitude", settings.waveAmplitude)
        node.putNumber("waveLength", settings.waveLength)
        node.putNumber("waveSpeed", settings.waveSpeed)
        node.putNumber("foamAmount", settings.foamAmount)
        node.putNumber("foamWidth", settings.foamWidth)
        node.put("enabled", settings.enabled)
        return node
    }

    private fun lookup(node: JsonNode, path: List<String>): JsonNode? {
        var current: JsonNode = node
        for (key in path) current = current.get(key) ?: return null
        return current
    }
}

/** Stores [value] as an integer when it is whole (so `100`, not `100.0`), else as a double. */
internal fun ObjectNode.putNumber(key: String, value: Double): ObjectNode =
    if (value == Math.rint(value) && kotlin.math.abs(value) < 1e15) put(key, value.toLong()) else put(key, value)
