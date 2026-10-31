/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.terrain

import net.nevinsky.abyssus.lib.core.editor.terrain.NoiseSamplerFactory
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.FlattenTool
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.RaiseLowerTool
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.SculptTool
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.SetHeightTool
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.SlopeLimitTool
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.SmoothTool
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.StampBlend
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.StampImage
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.StampTool
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.ThermalErosionTool
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.noiseTool

/** The tools of the Sculpt Terrain brush strip, in the order the strip lists them. */
enum class SculptToolKind { RAISE_LOWER, SMOOTH, FLATTEN, SET_HEIGHT, NOISE, SLOPE_LIMIT, STAMP, EROSION }

/** The options of every tool as the strip holds them; a tool reads only its own. */
data class SculptToolValues(
    val height: Float = 0f,
    val amplitude: Float = 5f,
    val featureSize: Float = 40f,
    val seed: Int = 1,
    val maxAngle: Float = 35f,
    val talus: Float = 30f,
    val iterations: Int = 8,
    val stampSize: Float = 100f,
    val stampHeight: Float = 20f,
    val stampRotation: Float = 0f,
    val stampBlend: StampBlend = StampBlend.ADD,
    val stampImage: StampImage? = null,
)

/** Builds the [SculptTool] a strip choice stands for. */
class SculptToolFactory(private val noise: NoiseSamplerFactory) {
    /** The tool for [kind] with [values], or null when it cannot work yet (a Stamp without a readable image). */
    fun create(kind: SculptToolKind, values: SculptToolValues): SculptTool? = when (kind) {
        SculptToolKind.RAISE_LOWER -> RaiseLowerTool()
        SculptToolKind.SMOOTH -> SmoothTool()
        SculptToolKind.FLATTEN -> FlattenTool()
        SculptToolKind.SET_HEIGHT -> SetHeightTool(values.height)
        SculptToolKind.NOISE -> noiseTool(noise, values.seed, values.featureSize, values.amplitude)
        SculptToolKind.SLOPE_LIMIT -> SlopeLimitTool(values.maxAngle)
        SculptToolKind.EROSION -> ThermalErosionTool(values.talus, values.iterations)
        SculptToolKind.STAMP -> values.stampImage?.let {
            StampTool(it, values.stampSize, values.stampHeight, values.stampRotation, values.stampBlend)
        }
    }
}
