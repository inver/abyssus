/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.render.terrain

import com.badlogic.gdx.graphics.GLTexture
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.gdx.shader.DirectionalVisibilityAttribute
import net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasAttribute

/**
 * The lighting inputs that only one terrain variant understands. The shared pass checks that the inputs it is handed
 * belong to the variant it was made for.
 */
sealed interface TerrainLighting {
    /**
     * The editor's: several lights, an optional built sky's irradiance cube (and the cube a cross-fade goes to, with how far
     * it has got), the shadow [atlas], the cloud shadow map [sunVisibility] and, in a planar reflection pass, a [clipPlane]
     * `(a, b, c, d)` that discards fragments with `a x + b y + c z + d < 0`.
     */
    class Editor(
        val irradiance: GLTexture? = null,
        val atlas: ShadowAtlasAttribute? = null,
        val fadeIrradiance: GLTexture? = null,
        val fade: Float = 0f,
        val sunVisibility: DirectionalVisibilityAttribute? = null,
        val clipPlane: FloatArray? = null,
    ) : TerrainLighting

    /**
     * A game's: one sun ([towardSun] unit vector, linear RGB [sunR]/[sunG]/[sunB]) that shadows through [shadow], when set.
     * A built sky's [irradiance] cube, when set, replaces the frame's flat ambient; while a newer one fades in, [fadeIrradiance]
     * is it and [fade] (0 to 1) how far it has replaced [irradiance]. The host keeps and disposes the cubes.
     */
    class Sun(
        val towardSun: Vector3,
        val sunR: Float,
        val sunG: Float,
        val sunB: Float,
        val shadow: SunShadowInput? = null,
        val irradiance: GLTexture? = null,
        val fadeIrradiance: GLTexture? = null,
        val fade: Float = 0f,
    ) : TerrainLighting
}

/** A sun's shadow map as the game's terrain program reads it: packed depth in [texture], seen through [matrix]. */
class SunShadowInput(val texture: Texture, val matrix: Matrix4, val texelSize: Float, val bias: Float)

/** One terrain to draw: its [mesh] placed by [world]. */
class TerrainDraw(val mesh: net.nevinsky.abyssus.lib.core.assets.terrain.TerrainMesh, val world: Matrix4)
