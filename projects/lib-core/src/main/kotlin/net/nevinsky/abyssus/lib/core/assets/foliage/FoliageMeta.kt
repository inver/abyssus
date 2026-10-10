/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.foliage

/** The file a foliage asset bakes its copies into, when `additional.dataFile` omits it. */
const val FOLIAGE_DATA_FILE = "foliage.data"

/** The `additional` block of a foliage `meta.json`. */
data class FoliageMeta(
    val terrain: String = "",
    val dataFile: String? = null,
    val maskResolution: Int = 256,
    val layers: List<FoliageLayerMeta> = emptyList()
) {
    /** The bake file of this asset: [dataFile], or [FOLIAGE_DATA_FILE] when the meta omits it. */
    fun dataFileName(): String = dataFile ?: FOLIAGE_DATA_FILE
}

/** The layer kinds. `OBJECT` copies are few and cast shadows; `DETAIL` copies are many and draw near the camera. */
enum class FoliageLayerKind { OBJECT, DETAIL }

/**
 * The kind of this layer, read defensively: `kind` is an untrusted string from `meta.json`, and a value the editor
 * does not know draws as an [FoliageLayerKind.OBJECT] layer rather than not at all.
 */
fun FoliageLayerMeta.layerKind(): FoliageLayerKind =
    FoliageLayerKind.entries.firstOrNull { it.name == kind } ?: FoliageLayerKind.OBJECT

/** One layer in a foliage meta. References are asset folder names, like scenes use them. */
data class FoliageLayerMeta(
    val id: Int = 0,
    val kind: String = FoliageLayerKind.OBJECT.name,
    val models: List<FoliageModelMeta> = emptyList(),
    /** Copies per square unit at full mask. */
    val density: Float = 0.05f,
    val scale: FoliageScale = FoliageScale(),
    /** How far copies lean toward the terrain normal, from 0 (upright) through 1 (with the slope). */
    val alignToNormal: Float = 0.5f,
    /** The lowest terrain-local height a copy may stand at, or null for no limit. */
    val minHeight: Float? = null,
    /** The highest terrain-local height a copy may stand at, or null for no limit. */
    val maxHeight: Float? = null,
    /** The steepest slope in degrees a copy may stand on, or null for no limit. */
    val maxSlope: Float? = null,
    /** How far from the camera DETAIL copies are drawn, in world units. */
    val drawDistance: Float = 80f,
    val seed: Int = 0
)

/** A model asset a layer scatters, with its [weight] among the layer's models. */
data class FoliageModelMeta(
    val asset: String = "",
    val weight: Float = 1f
)

/** The scale range of a layer's copies. */
data class FoliageScale(
    val min: Float = 0.8f,
    val max: Float = 1.2f
)
