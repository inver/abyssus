/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.core.model

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial
import com.badlogic.gdx.graphics.g3d.model.data.ModelTexture

/**
 * A [ModelMaterial] with metallic-roughness (glTF) properties. `null` values are not set on the material.
 * Textures use the `USAGE_*` constants of this class in addition to the ones of
 * [ModelTexture].
 */
class PbrModelMaterial : ModelMaterial() {
    enum class AlphaMode {
        OPAQUE, MASK, BLEND
    }

    var baseColor: Color? = null
    var metallic: Float? = null
    var roughness: Float? = null
    var doubleSided: Boolean = false
    var alphaMode: AlphaMode = AlphaMode.OPAQUE
    var alphaCutoff: Float = 0.5f

    companion object {
        const val USAGE_METALLIC_ROUGHNESS: Int = 100
        const val USAGE_OCCLUSION: Int = 101
    }
}
