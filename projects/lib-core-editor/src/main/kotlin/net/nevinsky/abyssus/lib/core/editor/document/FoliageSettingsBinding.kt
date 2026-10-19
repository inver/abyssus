/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.document

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLayerMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageModelMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageScale

/** Editor admission defaults, kept distinct from the runtime mapper's coercions. Never rewrites its source. */
class FoliageSettingsBinding {
    private val defaults = FoliageMeta()
    private val layerDefaults = FoliageLayerMeta()
    fun read(node: JsonNode): FoliageMeta = FoliageMeta(
        terrain = node.text("terrain") ?: "",
        dataFile = node.text("dataFile"),
        maskResolution = node.int("maskResolution") ?: defaults.maskResolution,
        layers = node.get("layers")?.takeIf { it.isArray }?.mapNotNull(::layer) ?: emptyList(),
    )

    private fun layer(node: JsonNode): FoliageLayerMeta? {
        val id = node.int("id") ?: return null
        return FoliageLayerMeta(
            id = id,
            kind = node.text("kind") ?: layerDefaults.kind,
            models = node.get("models")?.takeIf { it.isArray }
                ?.mapNotNull { model ->
                    val asset = model.text("asset") ?: return@mapNotNull null
                    FoliageModelMeta(asset, model.float("weight") ?: 1f)
                } ?: emptyList(),
            density = node.float("density") ?: layerDefaults.density,
            scale = FoliageScale(
                min = node.get("scale")?.float("min") ?: layerDefaults.scale.min,
                max = node.get("scale")?.float("max") ?: layerDefaults.scale.max,
            ),
            alignToNormal = node.float("alignToNormal") ?: layerDefaults.alignToNormal,
            minHeight = node.float("minHeight"),
            maxHeight = node.float("maxHeight"),
            maxSlope = node.float("maxSlope"),
            drawDistance = node.float("drawDistance") ?: layerDefaults.drawDistance,
            seed = node.int("seed") ?: 0,
            collider = node.get("collider")?.takeUnless { it.isNull },
        )
    }

    private fun JsonNode.text(name: String) = get(name)?.takeIf { it.isTextual }?.asText()
    private fun JsonNode.int(name: String) = get(name)?.takeIf { it.isIntegralNumber }?.asInt()
    private fun JsonNode.float(name: String) = get(name)?.takeIf { it.isNumber }?.floatValue()
}
