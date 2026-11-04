/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.document

import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLayerMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageModelMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageScale
import net.nevinsky.abyssus.lib.core.editor.util.JsonValues
import net.nevinsky.abyssus.lib.core.editor.util.valuesOf
import net.nevinsky.abyssus.lib.core.io.JsonProcessor

/**
 * Editor admission defaults, kept distinct from the runtime mapper's coercions: a member of the wrong kind takes its
 * default instead of failing the whole block. Reads the `additional` block as plain values and never rewrites its
 * source.
 */
class FoliageSettingsBinding(private val json: JsonProcessor) {
    private val defaults = FoliageMeta()
    private val layerDefaults = FoliageLayerMeta()

    fun read(block: JsonValues): FoliageMeta =
        FoliageMeta(
            terrain = block.text("terrain") ?: "",
            dataFile = block.text("dataFile"),
            maskResolution = block.int("maskResolution") ?: defaults.maskResolution,
            layers =
                (block["layers"] as? List<*>)?.mapNotNull { layer(valuesOf(it) ?: return@mapNotNull null) }
                    ?: emptyList(),
        )

    private fun layer(node: JsonValues): FoliageLayerMeta? {
        val id = node.int("id") ?: return null
        val scale = valuesOf(node["scale"])
        return FoliageLayerMeta(
            id = id,
            kind = node.text("kind") ?: layerDefaults.kind,
            models =
                (node["models"] as? List<*>)?.mapNotNull { model ->
                    val values = valuesOf(model) ?: return@mapNotNull null
                    val asset = values.text("asset") ?: return@mapNotNull null
                    FoliageModelMeta(asset, values.float("weight") ?: 1f)
                } ?: emptyList(),
            density = node.float("density") ?: layerDefaults.density,
            scale =
                FoliageScale(
                    min = scale?.float("min") ?: layerDefaults.scale.min,
                    max = scale?.float("max") ?: layerDefaults.scale.max,
                ),
            alignToNormal = node.float("alignToNormal") ?: layerDefaults.alignToNormal,
            minHeight = node.float("minHeight"),
            maxHeight = node.float("maxHeight"),
            maxSlope = node.float("maxSlope"),
            drawDistance = node.float("drawDistance") ?: layerDefaults.drawDistance,
            seed = node.int("seed") ?: 0,
            collider = node["collider"]?.let { colliderNode(json, it) },
        )
    }

    private fun JsonValues.text(name: String) = this[name] as? String

    private fun JsonValues.int(name: String) = (this[name] as? Number)?.takeIf { it is Int || it is Long }?.toInt()

    private fun JsonValues.float(name: String) = (this[name] as? Number)?.toFloat()
}

/**
 * A layer's `collider` member as the raw form [FoliageLayerMeta.collider] keeps. This is the one place the editor makes
 * that form from plain values, so the rest of the editor never handles it.
 */
internal fun colliderNode(json: JsonProcessor, value: Any) =
    json.readObject("""{"collider":${json.toString(value)}}""").get("collider")
