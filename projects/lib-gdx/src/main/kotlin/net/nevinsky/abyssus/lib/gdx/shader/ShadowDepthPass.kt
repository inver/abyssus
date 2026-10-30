/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3

/** Parameters shared by the depth shader for a single tile/pass. */
class ShadowDepthPass {
    val viewProjection = Matrix4()
    val lightPosition = Vector3()
    var radialDepth: Boolean = false
    var far: Float = 1f

    /**
     * A world clip plane `(a, b, c, d)`: fragments with `a x + b y + c z + d < 0` are discarded (alpha-tested cutouts
     * are unaffected otherwise). Null: no clipping, as for a shadow pass.
     */
    var clipPlane: FloatArray? = null

    /**
     * Above 0: the pass writes the linear view depth (the distance along the camera's forward axis) divided by this
     * far distance instead of the window depth, with an even precision over the range. The water passes read it back.
     */
    var linearFar: Float = 0f
}
