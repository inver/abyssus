/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.water

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.core.water.*
import java.util.*

/** Why a water edit was refused; a refused edit leaves the tree exactly as it was. */
sealed interface WaterEditError {
    /** `abyssus` or `waterSurfaces` (or a nested value) is not an object. */
    data object IncompatibleContainer : WaterEditError
    data object IdCollision : WaterEditError
    data object NotFound : WaterEditError
    data object BlankName : WaterEditError
    data class InvalidValue(val field: WaterField, val issue: WaterIssue) : WaterEditError
    data class InvalidSurface(val issues: Map<WaterField, WaterIssue>) : WaterEditError
}

sealed interface WaterEdit {
    /** The tree changed. */
    data object Changed : WaterEdit

    /** Nothing to do; the tree is untouched. */
    data object Unchanged : WaterEdit
    data class Rejected(val error: WaterEditError) : WaterEdit
}

/**
 * Pure edits of `abyssus.waterSurfaces` on a scene tree that its caller has already admitted. Only the targeted
 * surface members are touched; unknown members, key order, number text of everything else and an emptied
 * `waterSurfaces` object are left as they are.
 */
class WaterEdits(
    private val codec: WaterCodec = WaterCodec(),
    private val validation: WaterValidation = WaterValidation(),
    private val presets: WaterPresets = WaterPresets(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val nodes = JsonNodeFactory.instance

    /** An id not used by any surface of [root], from the injected generator. */
    fun allocateId(root: JsonNode): String {
        val existing = surfaces(root)?.fieldNames()?.asSequence()?.toSet().orEmpty()
        var id = newId()
        while (id in existing) id = newId()
        return id
    }

    /** [preset]'s display name, suffixed with the lowest number that makes it unique among the surface names of [root]. */
    fun uniqueName(root: JsonNode, preset: WaterPreset): String {
        val base = when (preset) {
            WaterPreset.LAKE -> "Lake"
            WaterPreset.SEA -> "Sea"
        }
        val names = codec.read(root.get(ABYSSUS_EXTENSION)).surfaces.map { it.name }.toSet()
        if (base !in names) return base
        return generateSequence(2) { it + 1 }.map { "$base $it" }.first { it !in names }
    }

    /** A new surface for [preset] centred on ([x], [z]) at [level], with an allocated id and unique name. */
    fun create(root: JsonNode, preset: WaterPreset, x: Double, level: Double, z: Double): WaterSettings =
        presets.defaults(preset, allocateId(root), uniqueName(root, preset), x, level, z)

    fun add(root: JsonNode, settings: WaterSettings): WaterEdit {
        val issues = validation.check(settings)
        if (issues.isNotEmpty() || settings.name.isBlank()) return rejectedSurface(settings, issues)
        val container = container(root, create = false) ?: return reject(WaterEditError.IncompatibleContainer)
        if (container.has(settings.id)) return reject(WaterEditError.IdCollision)
        val target = container(root, create = true) ?: return reject(WaterEditError.IncompatibleContainer)
        target.set<JsonNode>(settings.id, codec.node(settings, nodes))
        return WaterEdit.Changed
    }

    fun setField(root: JsonNode, id: String, field: WaterField, value: Double): WaterEdit =
        setFields(root, id, mapOf(field to value))

    /** Sets several scalars at once; all are checked first, so a bad one rejects the whole edit. */
    fun setFields(root: JsonNode, id: String, values: Map<WaterField, Double>): WaterEdit {
        for ((field, value) in values) validation.check(field, value)
            ?.let { return reject(WaterEditError.InvalidValue(field, it)) }
        val surface = surface(root, id) ?: return surfaceMissing(root)
        val changed = values.filter { (field, value) -> !sameNumber(lookup(surface, field.path), value) }
        if (changed.isEmpty()) return WaterEdit.Unchanged
        // check the parents first so nothing is written when one is not an object
        for (field in changed.keys) if (!writable(
                surface,
                field.path
            )
        ) return reject(WaterEditError.IncompatibleContainer)
        for ((field, value) in changed) write(surface, field.path, value)
        return WaterEdit.Changed
    }

    fun move(root: JsonNode, id: String, x: Double, level: Double, z: Double): WaterEdit =
        setFields(root, id, mapOf(WaterField.X to x, WaterField.LEVEL to level, WaterField.Z to z))

    fun rename(root: JsonNode, id: String, name: String): WaterEdit {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return reject(WaterEditError.BlankName)
        val surface = surface(root, id) ?: return surfaceMissing(root)
        if (surface.get("name")?.takeIf { it.isTextual }?.textValue() == trimmed) return WaterEdit.Unchanged
        surface.put("name", trimmed)
        return WaterEdit.Changed
    }

    fun setEnabled(root: JsonNode, id: String, enabled: Boolean): WaterEdit {
        val surface = surface(root, id) ?: return surfaceMissing(root)
        if (surface.get("enabled")?.takeIf { it.isBoolean }?.booleanValue() == enabled) return WaterEdit.Unchanged
        surface.put("enabled", enabled)
        return WaterEdit.Changed
    }

    /** Removes one surface; the emptied `waterSurfaces` object stays so unrelated `abyssus` members are not disturbed. */
    fun remove(root: JsonNode, id: String): WaterEdit {
        val container = surfaces(root) ?: return surfaceMissing(root)
        if (!container.has(id)) return reject(WaterEditError.NotFound)
        container.remove(id)
        return WaterEdit.Changed
    }

    private fun surfaces(root: JsonNode): ObjectNode? =
        (root.get(ABYSSUS_EXTENSION) as? ObjectNode)?.get(WATER_SURFACES) as? ObjectNode

    private fun surface(root: JsonNode, id: String): ObjectNode? = surfaces(root)?.get(id) as? ObjectNode

    private fun surfaceMissing(root: JsonNode): WaterEdit =
        reject(if (codec.read(root.get(ABYSSUS_EXTENSION)).incompatible) WaterEditError.IncompatibleContainer else WaterEditError.NotFound)

    /**
     * The `waterSurfaces` object of [root]. With [create] an absent one is made (with `abyssus`) and returned; without it an
     * absent one yields an empty detached object, so a caller can still tell "nothing there" from "cannot use". Null when
     * `abyssus` or `waterSurfaces` is present but not an object: those are never replaced.
     */
    private fun container(root: JsonNode, create: Boolean): ObjectNode? {
        val rootObject = root as? ObjectNode ?: return null
        val abyssus = rootObject.get(ABYSSUS_EXTENSION)
        if (abyssus != null && abyssus !is ObjectNode) return null
        val water = abyssus?.get(WATER_SURFACES)
        if (water != null && water !is ObjectNode) return null
        if (!create) return water as ObjectNode? ?: nodes.objectNode()
        val extension =
            abyssus as ObjectNode? ?: nodes.objectNode().also { rootObject.set<JsonNode>(ABYSSUS_EXTENSION, it) }
        return water as ObjectNode? ?: nodes.objectNode().also { extension.set<JsonNode>(WATER_SURFACES, it) }
    }

    private fun lookup(node: JsonNode, path: List<String>): JsonNode? {
        var current: JsonNode = node
        for (key in path) current = current.get(key) ?: return null
        return current
    }

    private fun sameNumber(node: JsonNode?, value: Double) =
        node != null && node.isNumber && node.doubleValue() == value

    private fun writable(surface: ObjectNode, path: List<String>): Boolean {
        var current: JsonNode = surface
        for (key in path.dropLast(1)) current = current.get(key) ?: return true
        return current is ObjectNode
    }

    private fun write(surface: ObjectNode, path: List<String>, value: Double) {
        var current = surface
        for (key in path.dropLast(1)) current =
            current.get(key) as? ObjectNode ?: nodes.objectNode().also { current.set<JsonNode>(key, it) }
        current.putNumber(path.last(), value)
    }

    private fun rejectedSurface(settings: WaterSettings, issues: Map<WaterField, WaterIssue>): WaterEdit =
        if (settings.name.isBlank() && issues.isEmpty()) reject(WaterEditError.BlankName) else reject(
            WaterEditError.InvalidSurface(
                issues
            )
        )

    private fun reject(error: WaterEditError): WaterEdit = WaterEdit.Rejected(error)
}
