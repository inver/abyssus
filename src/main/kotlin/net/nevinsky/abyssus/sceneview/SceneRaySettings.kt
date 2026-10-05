/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.sceneview

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode

/** Saved scene preferences; runtime enable state is deliberately separate. */
data class SceneRaySettings(
    val targetSamplesPerPixel: Int = 256,
    val maxRaysPerFrame: Int = 2097152,
    val maxReflectionBounces: Int = 1,
    val maxRefractionBounces: Int = 0,
) {
    init {
        require(targetSamplesPerPixel in 1..4096 && maxRaysPerFrame in 1..67108864)
        require(maxReflectionBounces in 0..16 && maxRefractionBounces in 0..16)
    }
}

enum class SceneRayField(val key: String, val default: Int, val minimum: Int, val maximum: Int) {
    SAMPLES("targetSamplesPerPixel", 256, 1, 4096),
    RAYS("maxRaysPerFrame", 2097152, 1, 67108864),
    REFLECTIONS("maxReflectionBounces", 1, 0, 16),
    REFRACTIONS("maxRefractionBounces", 0, 0, 16),
}

enum class RayDataError { INTEGER, RANGE, OBJECT, NUMBER, MATERIAL_ID, NON_PBR }
sealed interface RayDataEdit {
    data object Changed : RayDataEdit
    data object Unchanged : RayDataEdit
    data object Conflict : RayDataEdit
    data class Rejected(val error: RayDataError) : RayDataEdit
}

data class SceneRaySettingsState(
    val settings: SceneRaySettings?, val values: Map<SceneRayField, Int>, val errors: Map<String, RayDataError>,
)

/** Pure JSON codec/editor. Callers validate the enclosing native document and provide its expected field snapshot. */
class SceneRaySettingsCodec {
    fun edit(root: ObjectNode, field: SceneRayField, expected: JsonNode?, text: String): RayDataEdit {
        val block = root.get("rayTracing")
        if (block != null && !block.isObject) return RayDataEdit.Rejected(RayDataError.OBJECT)
        val actual = block?.get(field.key)
        if (actual != expected) return RayDataEdit.Conflict
        val trimmed = text.trim()
        if (!trimmed.matches(Regex("[+-]?[0-9]+"))) return RayDataEdit.Rejected(RayDataError.INTEGER)
        val value = trimmed.toIntOrNull() ?: return RayDataEdit.Rejected(RayDataError.RANGE)
        if (value !in field.minimum..field.maximum) return RayDataEdit.Rejected(RayDataError.RANGE)
        if ((actual == null && value == field.default) || (validate(
                actual,
                field
            ) == null && actual?.intValue() == value)
        ) return RayDataEdit.Unchanged
        if (value == field.default) {
            (block as? ObjectNode)?.remove(field.key)
            if (block != null && block.isEmpty) root.remove("rayTracing")
        } else {
            val target = block as? ObjectNode ?: root.objectNode().also { root.set<ObjectNode>("rayTracing", it) }
            target.put(field.key, value)
        }
        return RayDataEdit.Changed
    }

    private fun validate(node: JsonNode?, field: SceneRayField): RayDataError? = when {
        node == null -> null
        !node.isIntegralNumber -> RayDataError.INTEGER
        !node.canConvertToInt() || node.intValue() !in field.minimum..field.maximum -> RayDataError.RANGE
        else -> null
    }
}
